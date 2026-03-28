package com.antonread.app.data.db

import androidx.room.*
import com.antonread.app.data.model.ItemState
import com.antonread.app.data.model.ItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items WHERE type = :type")
    fun observeByType(type: ItemType): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE type = :type")
    suspend fun getByType(type: ItemType): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: String): ItemEntity?

    @Query("SELECT * FROM items")
    suspend fun getAll(): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ItemEntity>)

    @Update
    suspend fun update(item: ItemEntity)

    @Query("UPDATE items SET inSessionCorrect = 0 WHERE inSessionCorrect > 0")
    suspend fun resetSessionCounters()

    // After a session ends: SESSION_LEARNED → RETENTION_PENDING, IN_SESSION_1 → NEW
    @Query("""
        UPDATE items SET state = CASE
            WHEN state = 'SESSION_LEARNED' THEN 'RETENTION_PENDING'
            WHEN state = 'IN_SESSION_1'    THEN 'NEW'
            ELSE state
        END
    """)
    suspend fun applySessionEndTransitions()
}
