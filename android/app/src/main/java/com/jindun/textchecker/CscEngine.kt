package com.jindun.textchecker

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.LongBuffer

/**
 * 本地中文纠错引擎（macbert4csc int8 ONNX，全程离线）
 * 推理同步执行，由 FloatingService 保证在后台线程调用
 */
class CscEngine(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val tokenizer: BertTokenizer

    data class Suggestion(val index: Int, val original: String, val suggestion: String)

    init {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
        }
        context.assets.open(MODEL_FILE).use { input ->
            session = env.createSession(input.readBytes(), opts)
        }
        val vocabLines = context.assets.open(VOCAB_FILE).bufferedReader()
            .readLines().filter { it.isNotBlank() }
        tokenizer = BertTokenizer(vocabLines)
    }

    /**
     * 推理：返回「原字 → 预测字」建议列表
     * 仅保留：原 token 是单个 CJK 字、模型预测为不同的单个 CJK 字、且该位置未被 [UNK] 化
     */
    fun infer(text: String): List<Suggestion> {
        val tokenInfos = tokenizer.tokenizeWithOffsets(text)
        val ids = tokenizer.convertTokensToIds(tokenInfos)
        val seqLen = ids.size

        val inputIds = LongArray(seqLen) { ids[it].toLong() }
        val attention = LongArray(seqLen) { 1L }
        val tokenTypes = LongArray(seqLen)

        val shape = longArrayOf(1, seqLen.toLong())
        val results = mutableListOf<Suggestion>()

        OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape).use { a ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(attention), shape).use { b ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypes), shape).use { c ->
                    session.run(mapOf(
                        "input_ids" to a, "attention_mask" to b, "token_type_ids" to c
                    )).use { out ->
                        @Suppress("UNCHECKED_CAST")
                        val logits = out[0].value as Array<Array<FloatArray>>  // [1][seq][vocab]
                        val lg = logits[0]
                        for (i in 1 until seqLen - 1) {  // 跳过 [CLS] / [SEP]
                            val info = tokenInfos[i]
                            if (info.charIndex < 0) continue
                            if (info.token == "[UNK]") continue
                            if (info.token.length != 1 || !BertTokenizer.isCjk(info.token[0])) continue
                            // argmax + softmax 置信度阈值（实测 <0.5 多为量化噪声，如 话→囉 0.27）
                            val row = lg[i]
                            var best = 0
                            var bestVal = Float.NEGATIVE_INFINITY
                            for (v in row.indices) {
                                if (row[v] > bestVal) { bestVal = row[v]; best = v }
                            }
                            var sumExp = 0.0
                            for (v in row.indices) sumExp += Math.exp((row[v] - bestVal).toDouble())
                            val confidence = 1.0 / sumExp  // exp(0)/Σexp(row-max)
                            if (confidence < CONFIDENCE_MIN) continue
                            val pred = tokenizer.convertIdToToken(best) ?: continue
                            if (pred.length == 1 && BertTokenizer.isCjk(pred[0]) && pred != info.token) {
                                results.add(Suggestion(info.charIndex, info.token, pred))
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    fun close() {
        session.close()
    }

    companion object {
        const val MODEL_FILE = "model/macbert4csc.int8.onnx"
        const val VOCAB_FILE = "model/vocab.txt"
        const val CONFIDENCE_MIN = 0.5
    }
}
