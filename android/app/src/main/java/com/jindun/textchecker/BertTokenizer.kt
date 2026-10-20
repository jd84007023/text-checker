package com.jindun.textchecker

/**
 * BERT WordPiece 分词器（中文场景）
 * 逐字处理中文；英文/数字整词 WordPiece；每个 token 携带原文起始偏移
 * 词汇表从 assets/vocab.txt 加载，行号即 token id
 */
class BertTokenizer(private val vocabLines: List<String>) {

    data class TokenInfo(val token: String, val charIndex: Int)  // charIndex=-1 为特殊符号

    private val vocab = HashMap<String, Int>(vocabLines.size * 2)
    private val unkId: Int

    init {
        vocabLines.forEachIndexed { i, t -> vocab[t] = i }
        unkId = vocab["[UNK]"] ?: 100
    }

    val clsId: Int get() = vocab["[CLS]"] ?: 101
    val sepId: Int get() = vocab["[SEP]"] ?: 102

    /** 生成 token 序列（含 [CLS]/[SEP]），每个 token 附原文偏移 */
    fun tokenizeWithOffsets(text: String): List<TokenInfo> {
        val out = mutableListOf(TokenInfo("[CLS]", -1))
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == ' ' || c == '\t' || c == '\n' -> { i++; continue }
                isCjk(c) -> {
                    val tok = if (vocab.containsKey(c.toString())) c.toString() else "[UNK]"
                    out.add(TokenInfo(tok, i))
                    i++
                }
                c.isLetterOrDigit() || c == '\'' -> {
                    // 连续英数/撇号作为一个词
                    var j = i
                    while (j < n && (text[j].isLetterOrDigit() || text[j] == '\'')) j++
                    val word = text.substring(i, j)
                    out.addAll(wordPiece(word, i))
                    i = j
                }
                else -> {
                    // 标点等单字符：BERT 逐符号切分
                    val tok = if (vocab.containsKey(c.toString())) c.toString() else "[UNK]"
                    out.add(TokenInfo(tok, i))
                    i++
                }
            }
        }
        out.add(TokenInfo("[SEP]", -1))
        return out
    }

    /** WordPiece：失败整词记 [UNK]；每片携带词内起始的原文偏移 */
    private fun wordPiece(word: String, wordStart: Int): List<TokenInfo> {
        val out = mutableListOf<TokenInfo>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var matched: String? = null
            while (start < end) {
                var candidate = word.substring(start, end)
                if (start > 0) candidate = "##$candidate"
                if (vocab.containsKey(candidate)) { matched = candidate; break }
                end--
            }
            if (matched == null) {
                out.add(TokenInfo("[UNK]", wordStart + start))
                return out
            }
            out.add(TokenInfo(matched, wordStart + start))
            start = end
        }
        return out
    }

    fun convertTokensToIds(tokens: List<TokenInfo>): IntArray =
        IntArray(tokens.size) { vocab[tokens[it].token] ?: unkId }

    fun convertIdToToken(id: Int): String? =
        if (id in vocabLines.indices) vocabLines[id] else null

    companion object {
        fun isCjk(c: Char): Boolean =
            (c in '\u4E00'..'\u9FFF') || (c in '\u3400'..'\u4DBF')
    }
}
