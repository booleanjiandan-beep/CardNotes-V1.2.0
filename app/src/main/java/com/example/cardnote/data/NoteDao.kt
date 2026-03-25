package com.example.cardnote.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // 获取所有笔记（实时流）
    @Query("SELECT * FROM notes ORDER BY id DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    // 仅获取已下载的笔记
    @Query("SELECT * FROM notes WHERE isDownloaded = 1 ORDER BY id DESC")
    fun getDownloadedNotes(): Flow<List<NoteEntity>>

    // 仅获取未下载的笔记
    @Query("SELECT * FROM notes WHERE isDownloaded = 0 ORDER BY id DESC")
    fun getNotDownloadedNotes(): Flow<List<NoteEntity>>

    // 根据 isDownloaded 状态动态查询
    @Query("SELECT * FROM notes WHERE isDownloaded = :isDownloaded ORDER BY id DESC")
    fun getNotesByDownloadStatus(isDownloaded: Boolean): Flow<List<NoteEntity>>

    // 根据 ID 获取单条笔记
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): NoteEntity?

    // 插入笔记（冲突时替换）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    // 更新笔记
    @Update
    suspend fun updateNote(note: NoteEntity)

    // 删除笔记
    @Delete
    suspend fun deleteNote(note: NoteEntity)

    // 按 ID 删除
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    // 更新下载状态
    @Query("UPDATE notes SET isDownloaded = :isDownloaded WHERE id = :id")
    suspend fun updateDownloadStatus(id: Long, isDownloaded: Boolean)

    // 统计数量
    @Query("SELECT COUNT(*) FROM notes WHERE isDownloaded = :isDownloaded")
    suspend fun countByDownloadStatus(isDownloaded: Boolean): Int

    // ── 全字段模糊搜索（name / url / remarks 同时匹配）──
    @Query("""
        SELECT * FROM notes
        WHERE (
            name    LIKE '%' || :query || '%' OR
            url     LIKE '%' || :query || '%' OR
            remarks LIKE '%' || :query || '%'
        )
        ORDER BY id DESC
    """)
    fun searchAllNotes(query: String): Flow<List<NoteEntity>>

    // 搜索 + 仅已下载
    @Query("""
        SELECT * FROM notes
        WHERE isDownloaded = 1 AND (
            name    LIKE '%' || :query || '%' OR
            url     LIKE '%' || :query || '%' OR
            remarks LIKE '%' || :query || '%'
        )
        ORDER BY id DESC
    """)
    fun searchDownloadedNotes(query: String): Flow<List<NoteEntity>>

    // 搜索 + 仅未下载
    @Query("""
        SELECT * FROM notes
        WHERE isDownloaded = 0 AND (
            name    LIKE '%' || :query || '%' OR
            url     LIKE '%' || :query || '%' OR
            remarks LIKE '%' || :query || '%'
        )
        ORDER BY id DESC
    """)
    fun searchNotDownloadedNotes(query: String): Flow<List<NoteEntity>>
}
