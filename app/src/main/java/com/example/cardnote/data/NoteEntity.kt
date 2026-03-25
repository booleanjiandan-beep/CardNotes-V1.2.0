package com.example.cardnote.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "notes")
@TypeConverters(Converters::class)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val isDownloaded: Boolean = false,
    val remarks: String = "",
    val images: List<String> = emptyList() // Local URI strings
)

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(DELIMITER)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList()
        else value.split(DELIMITER)
    }

    companion object {
        private const val DELIMITER = "|||"
    }
}
