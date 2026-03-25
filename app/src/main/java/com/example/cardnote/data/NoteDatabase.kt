package com.example.cardnote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    // 预填充示例数据
    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateSampleData(database.noteDao())
                }
            }
        }

        suspend fun populateSampleData(dao: NoteDao) {
            dao.insertNote(
                NoteEntity(
                    name = "Kotlin 协程笔记",
                    url = "https://kotlinlang.org/docs/coroutines-overview.html",
                    isDownloaded = true,
                    remarks = "深入理解 suspend 函数和 Flow",
                    images = emptyList()
                )
            )
            dao.insertNote(
                NoteEntity(
                    name = "Jetpack Compose 入门",
                    url = "https://developer.android.com/jetpack/compose",
                    isDownloaded = false,
                    remarks = "需要下载离线文档",
                    images = emptyList()
                )
            )
            dao.insertNote(
                NoteEntity(
                    name = "Room 数据库指南",
                    url = "https://developer.android.com/training/data-storage/room",
                    isDownloaded = true,
                    remarks = "包含 DAO、Entity、Migration 示例",
                    images = emptyList()
                )
            )
            dao.insertNote(
                NoteEntity(
                    name = "Material3 设计规范",
                    url = "https://m3.material.io/",
                    isDownloaded = false,
                    remarks = "颜色系统和组件规范",
                    images = emptyList()
                )
            )
            dao.insertNote(
                NoteEntity(
                    name = "Android 架构组件",
                    url = "https://developer.android.com/topic/architecture",
                    isDownloaded = true,
                    remarks = "MVVM + Repository 模式",
                    images = emptyList()
                )
            )
        }
    }
}
