package com.ainote.service

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * 从 MediaStore 里找出「新的截图」。
 *
 * 各 ROM 的截图目录并不统一（Pictures/Screenshots、DCIM/Screenshots、ScreenShot…），
 * 所以判定标准是路径里出现 screenshot 字样，而不是比对固定目录。
 */
object ScreenshotQuery {

    data class Candidate(
        val uri: Uri,
        val displayName: String,
        val dateAddedSeconds: Long,
        val path: String?
    )

    private const val KEYWORD = "screenshot"

    fun querySince(
        context: Context,
        sinceSeconds: Long,
        limit: Int = 80
    ): List<Candidate> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Images.Media.DATA)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(sinceSeconds.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val result = mutableListOf<Candidate>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val pathCol = cursor.getColumnIndex(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Images.Media.RELATIVE_PATH
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.DATA
                        }
                    )

                    while (cursor.moveToNext() && result.size < limit) {
                        val path = if (pathCol >= 0) cursor.getString(pathCol) else null
                        if (path == null || !path.lowercase().contains(KEYWORD)) continue

                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(collection, id)
                        result += Candidate(
                            uri = uri,
                            displayName = cursor.getString(nameCol) ?: "",
                            dateAddedSeconds = cursor.getLong(dateCol),
                            path = path
                        )
                    }
                }
        }
        return result
    }
}
