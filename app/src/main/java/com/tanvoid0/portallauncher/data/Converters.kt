package com.tanvoid0.portallauncher.data

import androidx.room.TypeConverter

object Converters {
    private const val DELIM = "||"

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(DELIM)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(DELIM).filter { it.isNotBlank() }
}
