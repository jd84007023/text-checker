"""
任务4+5：macbert4csc → ONNX 导出、int8 量化、一致性验证
产出：model_export/macbert4csc.int8.onnx + model_export/vocab.txt
"""
import os, sys
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")  # 国内镜像优先

import torch
from transformers import AutoTokenizer, AutoModelForMaskedLM

MODEL_ID = "shibing624/macbert4csc-base-chinese"
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "model_export")
os.makedirs(OUT_DIR, exist_ok=True)
ONNX_FP32 = os.path.join(OUT_DIR, "macbert4csc.fp32.onnx")
ONNX_INT8 = os.path.join(OUT_DIR, "macbert4csc.int8.onnx")
VOCAB_OUT = os.path.join(OUT_DIR, "vocab.txt")

print("== 1. 下载模型与分词器 ==", flush=True)
tok = AutoTokenizer.from_pretrained(MODEL_ID)
model = AutoModelForMaskedLM.from_pretrained(MODEL_ID)
model.eval()

# 保存 vocab 供 Kotlin 端使用
with open(VOCAB_OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(tok.convert_ids_to_tokens(list(range(tok.vocab_size)))))
print("vocab 大小:", tok.vocab_size, flush=True)


class LogitsWrapper(torch.nn.Module):
    """剥离 HF 返回结构，只输出 logits，便于 ONNX 导出"""
    def __init__(self, m):
        super().__init__()
        self.m = m

    def forward(self, input_ids, attention_mask, token_type_ids):
        return self.m(input_ids=input_ids, attention_mask=attention_mask,
                      token_type_ids=token_type_ids).logits


print("== 2. 导出 ONNX（fp32，动态序列长度）==", flush=True)
wrapped = LogitsWrapper(model)
sample = tok("测试一下，这个的效果", return_tensors="pt")
with torch.no_grad():
    torch.onnx.export(
        wrapped,
        (sample["input_ids"], sample["attention_mask"], sample["token_type_ids"]),
        ONNX_FP32,
        input_names=["input_ids", "attention_mask", "token_type_ids"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "seq"},
            "attention_mask": {0: "batch", 1: "seq"},
            "token_type_ids": {0: "batch", 1: "seq"},
            "logits": {0: "batch", 1: "seq"},
        },
        opset_version=14,
        do_constant_folding=True,
    )
print("导出完成:", os.path.getsize(ONNX_FP32) // 1024 // 1024, "MB", flush=True)

print("== 3. int8 动态量化 ==", flush=True)
from onnxruntime.quantization import quantize_dynamic, QuantType
quantize_dynamic(ONNX_FP32, ONNX_INT8, weight_type=QuantType.QInt8)
print("量化完成:", os.path.getsize(ONNX_INT8) // 1024 // 1024, "MB", flush=True)

print("== 4. 推理一致性验证 ==", flush=True)
import onnxruntime as ort
import numpy as np

TESTS = [
    "测试一下，这狗的效果",
    "今天的生产计划已经安排好了",
    "报告以经交了，请查收",
    "这狗真听话",
]
so = ort.SessionOptions()
so.intra_op_num_threads = 4
sess = ort.InferenceSession(ONNX_INT8, so)

def torch_logits(text):
    s = tok(text, return_tensors="pt")
    with torch.no_grad():
        return wrapped(s["input_ids"], s["attention_mask"], s["token_type_ids"])[0].numpy()

def ort_logits(text):
    s = tok(text, return_tensors="np")
    feed = {k: v.astype(np.int64) for k, v in s.items()}
    return sess.run(None, feed)[0][0]

def correct_from(logits, text):
    """与训练目标一致：逐 token 取 argmax，非 [UNK] 特殊处理，比较原字与预测字"""
    enc = tok(text, return_offsets_mapping=True, add_special_tokens=True)
    ids = enc["input_ids"]
    tokens = tok.convert_ids_to_tokens(ids)
    lg = logits
    out = []
    for i, (tid, t) in enumerate(zip(ids, tokens)):
        if t in ("[CLS]", "[SEP]", "[PAD]", "[UNK]"):
            continue
        pred = int(np.argmax(lg[i]))
        if pred != tid:
            pt = tok.convert_ids_to_tokens([pred])[0]
            if pt and not pt.startswith("##") and len(pt) == 1 and "\u4e00" <= pt <= "\u9fff":
                out.append((t, pt, i))
    return out

agree, total = 0, 0
for text in TESTS:
    l_t = torch_logits(text)
    l_o = ort_logits(text)
    a = correct_from(l_t, text)
    b = correct_from(l_o, text)
    total += 1
    if a == b:
        agree += 1
    print(f"  [{'一致' if a==b else '不一致'}] {text!r}")
    print(f"    torch: {a}")
    print(f"    ort  : {b}")
print(f"一致性: {agree}/{total}")
print("== 完成 ==")
