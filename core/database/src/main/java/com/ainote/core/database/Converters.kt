package com.ainote.core.database

import androidx.room.TypeConverter

/**
 * 目前 Room 已内置枚举转换，这里保留转换器入口，
 * 后续加入 Instant / 自定义值对象时在此扩展。
 */
class Converters {

    @TypeConverter
    fun stringListToString(value: List<String>?): String? =
        value?.joinToString(separator = "\u001F")

    @TypeConverter
    fun stringToStringList(value: String?): List<String>? =
        value?.takeIf { it.isNotEmpty() }?.split("\u001F")
}
