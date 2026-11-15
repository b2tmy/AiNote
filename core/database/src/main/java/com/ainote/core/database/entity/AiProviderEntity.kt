package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 模型接入配置。API Key 本身不落库——只存 Keystore 里的别名，
 * 真正的密文由 core:security 模块保管。
 */
@Entity(
    tableName = "ai_providers",
    indices = [Index(value = ["name"], unique = true)]
)
data class AiProviderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    /** 形如 https://api.deepseek.com/v1 */
    @ColumnInfo(name = "base_url")
    val baseUrl: String,

    /** Keystore 中的密钥别名，null 表示尚未录入 */
    @ColumnInfo(name = "key_alias")
    val keyAlias: String? = null,

    @ColumnInfo(name = "model")
    val model: String,

    @ColumnInfo(name = "supports_vision")
    val supportsVision: Boolean = false,

    @ColumnInfo(name = "supports_stream")
    val supportsStream: Boolean = true,

    @ColumnInfo(name = "temperature")
    val temperature: Double = 0.3,

    @ColumnInfo(name = "max_tokens")
    val maxTokens: Int = 512,

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    /** 预设来源标识（deepseek/openai/custom…），便于识别内置项 */
    @ColumnInfo(name = "preset_key")
    val presetKey: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
