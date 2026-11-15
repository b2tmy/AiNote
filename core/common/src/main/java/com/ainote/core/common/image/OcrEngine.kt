package com.ainote.core.common.image

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 本地中文 OCR（ML Kit，离线）。
 *
 * 存在的意义：让**没有视觉能力的模型**（比如 deepseek-chat）也能用截图分析 ——
 * 先把图上的字识别出来，再交给文本模型做结构化。代价是 APK 大约 +15MB。
 *
 * 引擎是懒加载的：只用文本模型的用户，第一次真正跑 OCR 之前不会付出初始化开销。
 */
@Singleton
class OcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /** 识别失败或图上没字都返回 null，调用方据此决定是否报「拿不到文字」 */
    suspend fun recognize(file: File): String? {
        if (!file.exists()) return null

        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) {
                            continuation.resume(result.text.takeIf { it.isNotBlank() })
                        }
                    }
                    .addOnFailureListener { error ->
                        Log.w(TAG, "OCR 失败", error)
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "OCR 异常", t)
            null
        }
    }

    private companion object {
        const val TAG = "OcrEngine"
    }
}
