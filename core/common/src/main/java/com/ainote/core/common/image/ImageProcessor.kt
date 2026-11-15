package com.ainote.core.common.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * 截图落盘前的统一处理：算指纹去重、压到够用的尺寸控制 token 成本。
 *
 * 压缩策略：长边压到 [MAX_EDGE]，WebP 有损 q=[WEBP_QUALITY]。
 * 一张 1080x2400 的手机截图通常能从 2MB 压到 100~200KB，
 * 换算成多模态模型的图片 token 也降一个数量级。
 */
object ImageProcessor {

    const val MAX_EDGE = 1024
    const val WEBP_QUALITY = 80

    /** 画质档位，用户可在设置里切换 */
    enum class Quality(val maxEdge: Int, val quality: Int) {
        LOW(768, 70),
        MEDIUM(1024, 80),
        HIGH(1536, 90)
    }

    data class CompressResult(
        val file: File,
        val width: Int,
        val height: Int,
        val sizeBytes: Long
    )

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    /** 只读取图片头部拿到原始尺寸，避免整张解码进内存 */
    fun decodeBounds(input: InputStream): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    /**
     * 把输入流压缩成 WebP 写到 [target]。
     * 调用方负责提供 IO 调度器；这里是纯粹的阻塞实现。
     */
    fun compressToWebp(
        openInput: () -> InputStream,
        target: File,
        maxEdge: Int = MAX_EDGE,
        quality: Int = WEBP_QUALITY
    ): CompressResult? {
        val bounds = openInput().use { decodeBounds(it) }
            ?: return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.first, bounds.second, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = openInput().use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        val scaled = scaleDown(decoded, maxEdge)

        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            scaled.compress(webpFormat(), quality, out)
        }

        if (scaled !== decoded) decoded.recycle()
        scaled.recycle()

        return CompressResult(
            file = target,
            width = bounds.first,
            height = bounds.second,
            sizeBytes = target.length()
        )
    }

    /** 采样率取 2 的幂，保证解码后的位图仍大于目标尺寸，再由 scaleDown 精修 */
    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxEdge && h / 2 >= maxEdge) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        return if (scaled === bitmap) bitmap else scaled
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
}
