package com.eldercare.ai.data.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

/**
 * Room 类型转换器：Date、List<String> 与 JSON 互转
 */
class Converters {

    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.let { gson.toJson(it) }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value.isNullOrBlank()) return emptyList()
        return gson.fromJson(value, listType)
    }
}
