package com.antonread.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.antonread.app.data.model.ItemState
import com.antonread.app.data.model.ItemType

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,          // e.g. "letter:м", "syllable:ма", "word:машина"
    val type: ItemType,
    val content: String,                 // display text
    val state: ItemState = ItemState.NEW,
    val correctTotal: Int = 0,
    val inSessionCorrect: Int = 0,       // resets each session
    val lastSeenSessionId: Long = 0
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,                 // epoch millis
    val endedAt: Long? = null
)
