package com.zalodich

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/** Tầng 1: dịch offline bằng ML Kit. (Tầng 2 - AI sẽ thêm ở bước sau.) */
object TranslatorEngine {
    val LANGS = listOf(
        TranslateLanguage.VIETNAMESE,
        TranslateLanguage.ENGLISH,
        TranslateLanguage.JAPANESE,
        TranslateLanguage.KOREAN,
        TranslateLanguage.CHINESE
    )

    private val idClient = LanguageIdentification.getClient()
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
        idClient.identifyLanguage(text)
            .addOnSuccessListener { cb(it) }
            .addOnFailureListener { cb("und") }
    }

    /** cb(null) nếu chưa có gói dịch / ngôn ngữ không hỗ trợ. */
    fun translate(text: String, srcCode: String, tgtCode: String, cb: (String?) -> Unit) {
        val src = TranslateLanguage.fromLanguageTag(srcCode)
        val tgt = TranslateLanguage.fromLanguageTag(tgtCode)
        if (src == null || tgt == null || src == tgt) {
            cb(null); return
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
