package com.antonread.app.data.db

import androidx.room.*

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int
}
