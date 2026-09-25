package com.example.relevantreasearchupdates.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromWatchType(value: WatchType): String = value.name

    @TypeConverter
    fun toWatchType(value: String): WatchType = WatchType.valueOf(value)

    @TypeConverter
    fun fromPaperSource(value: PaperSource): String = value.name

    @TypeConverter
    fun toPaperSource(value: String): PaperSource = PaperSource.valueOf(value)
}
