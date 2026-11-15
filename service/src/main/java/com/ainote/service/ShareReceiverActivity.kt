package com.ainote.service

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 分享菜单收图入口：从相册 / 浏览器 / 任何 App「分享」图片到本应用。
 * 这是 ContentObserver 在某些 ROM 上失效时的保底通道。
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var ingestor: ScreenshotIngestor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = collectImageUris(intent)
        if (uris.isEmpty()) {
            finishWith(getString(R.string.share_no_image))
            return
        }

        lifecycleScope.launch {
            var accepted = 0
            uris.forEach { uri ->
                if (ingestor.ingestSingle(uri) != null) accepted++
            }
            finishWith(
                if (accepted > 0) {
                    getString(R.string.share_added, accepted)
                } else {
                    getString(R.string.share_duplicated)
                }
            )
        }
    }

    private fun collectImageUris(intent: Intent?): List<Uri> {
        intent ?: return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            )

            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                list ?: emptyList()
            }

            else -> emptyList()
        }
    }

    private fun finishWith(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
