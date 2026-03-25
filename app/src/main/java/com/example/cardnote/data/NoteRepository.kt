package com.example.cardnote.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {

    fun getAllNotes(): Flow<List<NoteEntity>> = dao.getAllNotes()

    fun getDownloadedNotes(): Flow<List<NoteEntity>> = dao.getDownloadedNotes()

    fun getNotDownloadedNotes(): Flow<List<NoteEntity>> = dao.getNotDownloadedNotes()

    fun getNotesByStatus(isDownloaded: Boolean): Flow<List<NoteEntity>> =
        dao.getNotesByDownloadStatus(isDownloaded)

    // 搜索：全字段模糊，与下载筛选独立组合
    fun searchNotes(query: String, filterState: com.example.cardnote.ui.FilterState): Flow<List<NoteEntity>> =
        when {
            filterState.showAll        -> dao.searchAllNotes(query)
            filterState.showDownloaded -> dao.searchDownloadedNotes(query)
            else                       -> dao.searchNotDownloadedNotes(query)
        }

    suspend fun insertNote(note: NoteEntity): Long = dao.insertNote(note)

    suspend fun updateNote(note: NoteEntity) = dao.updateNote(note)

    suspend fun deleteNote(note: NoteEntity) = dao.deleteNote(note)

    suspend fun deleteNoteById(id: Long) = dao.deleteNoteById(id)

    suspend fun updateDownloadStatus(id: Long, isDownloaded: Boolean) =
        dao.updateDownloadStatus(id, isDownloaded)
}
