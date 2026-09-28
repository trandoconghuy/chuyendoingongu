package com.zalodich

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

data class ChatLine(
    val sender: String,
    val text: String,
    val outgoing: Boolean,
    val top: Int
)

data class LanguageDecision(
    val code: String,
    val translationVi: String? = null,
    val contextNote: String? = null
)

/** Nhận diện nhanh bằng ML Kit, sau đó tinh chỉnh bằng AI khi đã cấu hình backend. */
object TranslatorEngine {
    val LANGS = listOf(
        TranslateLanguage.VIETNAMESE,
        TranslateLanguage.ENGLISH,
        TranslateLanguage.JAPANESE,
        TranslateLanguage.KOREAN,
        TranslateLanguage.CHINESE
    )

    // Ngưỡng mặc định 0.5 thường trả về "und" cho tin nhắn chat ngắn.
    private val idClient = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(0.34f)
            .build()
    )
    // Client mặc định giữ ngưỡng 0.01 cho danh sách phương án dự phòng.
    private val possibleLanguagesClient = LanguageIdentification.getClient()
    private val cache = HashMap<String, Translator>()

    private fun translator(src: String, tgt: String): Translator =
        cache.getOrPut("$src>$tgt") {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(src)
                    .setTargetLanguage(tgt)
                    .build()
            )
        }

    fun detect(text: String, cb: (String) -> Unit) {
        detectScript(text)?.let { cb(it); return }
        idClient.identifyLanguage(text)
            .addOnSuccessListener { code ->
                if (code != "und") {
                    cb(code)
                } else {
                    possibleLanguagesClient.identifyPossibleLanguages(text)
                        .addOnSuccessListener { possibilities ->
                            val best = possibilities.firstOrNull()?.takeIf {
                                it.confidence >= 0.12f &&
                                    it.languageTag in LANGS &&
                                    it.languageTag != TranslateLanguage.VIETNAMESE
                            }
                            cb(best?.languageTag ?: latinFallback(text))
                        }
                        .addOnFailureListener { cb(latinFallback(text)) }
                }
            }
            .addOnFailureListener { cb(detectScript(text) ?: latinFallback(text)) }
    }

    /** Nhận diện theo lô; AI được nhìn các câu trước/sau để hiểu chat không dấu và câu cực ngắn. */
    fun detectWithContext(
        context: Context,
        messages: List<ChatLine>,
        cb: (decisions: List<LanguageDecision>, usedAi: Boolean) -> Unit
    ) {
        if (messages.isEmpty()) {
            cb(emptyList(), false)
            return
        }
        val local = MutableList(messages.size) { "und" }
        var pending = messages.size
        messages.forEachIndexed { index, message ->
            detect(message.text) { code ->
                local[index] = code
                pending--
                if (pending == 0) {
                    val contextualLocal = applyNeighborContext(messages, local)
                    // Hiển thị bản dịch offline ngay; AI sẽ tinh chỉnh bằng callback thứ hai.
                    cb(contextualLocal.map { LanguageDecision(it) }, false)
                    AiContextEngine.detectLanguages(context, messages, contextualLocal) { ai ->
                        if (ai != null) {
                            // Không để một phản hồi "und" làm mất kết quả chắc chắn ở tầng cục bộ.
                            val merged = ai.mapIndexed { i, decision ->
                                if (decision.code == "und" && contextualLocal[i] != "und") {
                                    decision.copy(code = contextualLocal[i])
                                } else {
                                    decision
                                }
                            }
                            cb(merged, true)
                        }
                    }
                }
            }
        }
    }

    private fun applyNeighborContext(messages: List<ChatLine>, codes: List<String>): List<String> {
        val result = codes.toMutableList()
        codes.indices.forEach { index ->
            if (codes[index] != "und" || messages[index].text.none(Char::isLetter)) return@forEach
            val left = (index - 1 downTo maxOf(0, index - 2))
                .map { codes[it] }.firstOrNull { it != "und" && it != "vi" }
            val right = (index + 1..minOf(codes.lastIndex, index + 2))
                .map { codes[it] }.firstOrNull { it != "und" && it != "vi" }
            if (left != null && left == right) result[index] = left
        }
        return result
    }

    private fun detectScript(text: String): String? = when {
        text.any { it in '\u3040'..'\u30ff' } -> TranslateLanguage.JAPANESE
        text.any { it in '\uac00'..'\ud7af' || it in '\u1100'..'\u11ff' } -> TranslateLanguage.KOREAN
        text.any { it in '\u3400'..'\u4dbf' || it in '\u4e00'..'\u9fff' } -> TranslateLanguage.CHINESE
        else -> null
    }

    private fun latinFallback(text: String): String {
        val normalized = text.lowercase()
        val hasVietnameseMarks = normalized.any { it in "ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ" }
        val words = normalized.split(Regex("\\s+")).filter { word -> word.any(Char::isLetter) }
        return if (!hasVietnameseMarks && words.size >= 2 && normalized.any { it in 'a'..'z' }) {
            TranslateLanguage.ENGLISH
        } else {
            "und"
        }
    }

    /** cb(null) nếu chưa có gói dịch / ngôn ngữ không hỗ trợ. */
    fun translate(text: String, srcCode: String, tgtCode: String, cb: (String?) -> Unit) {
        val src = TranslateLanguage.fromLanguageTag(srcCode)
        val tgt = TranslateLanguage.fromLanguageTag(tgtCode)
        if (src == null || tgt == null) {
            cb(null); return
        }
        if (src == tgt) {
            cb(text); return
        }
        val tr = translator(src, tgt)
        tr.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                tr.translate(text)
                    .addOnSuccessListener { cb(it) }
                    .addOnFailureListener { cb(null) }
            }
            .addOnFailureListener { cb(null) }
    }

    fun downloadAll(onStatus: (String) -> Unit) {
        val mgr = RemoteModelManager.getInstance()
        LANGS.forEach { l ->
            onStatus("Đang tải $l …")
            mgr.download(TranslateRemoteModel.Builder(l).build(), DownloadConditions.Builder().build())
                .addOnSuccessListener { onStatus("Đã tải xong: $l") }
                .addOnFailureListener { onStatus("Lỗi tải $l (kiểm tra mạng)") }
        }
    }
}
