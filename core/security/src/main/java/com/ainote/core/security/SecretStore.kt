package com.ainote.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API Key 的本地保管箱。
 *
 * 实现要点：
 * - 主密钥由 Android Keystore 生成并保管（AES256-GCM），密文写在 EncryptedSharedPreferences；
 * - Key 永远不进数据库、不进日志、不进备份（app 已设 allowBackup=false）；
 * - 数据库里只存别名（ai_providers.key_alias），真实值只在这里。
 *
 * 关于 EncryptedSharedPreferences 被标记废弃：Google 推荐自行封装 Keystore，
 * 但对本项目而言它是够用且经过验证的方案。若加密库初始化失败（极少数 ROM 的
 * Keystore 异常），这里会退回一个「不可用」状态而不是让整个 App 崩掉。
 */
@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences? by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as SharedPreferences
        }.onFailure {
            Log.e(TAG, "加密存储初始化失败，API Key 将无法保存", it)
        }.getOrNull()
    }

    /** 保管箱是否可用（Keystore 异常时为 false） */
    val isAvailable: Boolean get() = prefs != null

    fun putApiKey(alias: String, value: String) {
        prefs?.edit()?.putString(alias, value)?.apply()
    }

    fun getApiKey(alias: String): String? = prefs?.getString(alias, null)

    fun hasApiKey(alias: String): Boolean = !getApiKey(alias).isNullOrBlank()

    fun removeApiKey(alias: String) {
        prefs?.edit()?.remove(alias)?.apply()
    }

    fun clearAll() {
        prefs?.edit()?.clear()?.apply()
    }

    /** 给界面用的掩码显示，永不返回完整 Key */
    fun maskedKey(alias: String): String? {
        val value = getApiKey(alias) ?: return null
        if (value.length <= 8) return "••••"
        return value.take(4) + "••••" + value.takeLast(4)
    }

    private companion object {
        const val PREFS_NAME = "ainote_secrets"
        const val TAG = "SecretStore"
    }
}
