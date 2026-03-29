package com.antonread.app.data.repository

import com.antonread.app.data.content.Letters
import com.antonread.app.data.content.Seeder
import com.antonread.app.data.db.AppDatabase
import com.antonread.app.data.db.ItemEntity
import com.antonread.app.data.db.SessionEntity
import com.antonread.app.data.model.ItemState
import com.antonread.app.data.model.ItemType
import com.antonread.app.data.model.Mode
import kotlinx.coroutines.flow.Flow

private const val SESSION_GAP_MILLIS = 3 * 60 * 60 * 1000L
private const val LETTER_WINDOW = 3 // max non-known letters shown at once

class LearningRepository(private val db: AppDatabase) {

    val itemDao get() = db.itemDao()
    val sessionDao get() = db.sessionDao()

    // ── Session management ─────────────────────────────────────────────────

    private var currentSessionId: Long = 0L

    suspend fun initSession(forceNew: Boolean = false): Long {
        db.itemDao().insertAll(Seeder.allItems())

        val latest = db.sessionDao().getLatest()
        val now = System.currentTimeMillis()
        val isNewSession = forceNew
            || latest == null
            || (latest.endedAt != null && now - latest.endedAt > SESSION_GAP_MILLIS)
            || (latest.endedAt == null && now - latest.startedAt > SESSION_GAP_MILLIS)

        if (isNewSession) {
            if (latest != null && latest.endedAt == null) closeSession(latest.id)
            currentSessionId = db.sessionDao().insert(SessionEntity(startedAt = now))
        } else {
            currentSessionId = latest!!.id
        }
        return currentSessionId
    }

    private suspend fun closeSession(@Suppress("UNUSED_PARAMETER") sessionId: Long) {
        val session = db.sessionDao().getLatest() ?: return
        db.sessionDao().update(session.copy(endedAt = System.currentTimeMillis()))
        db.itemDao().applySessionEndTransitions()
        db.itemDao().resetSessionCounters()
    }

    suspend fun forceNewSession() {
        val latest = db.sessionDao().getLatest()
        if (latest != null && latest.endedAt == null) closeSession(latest.id)
        currentSessionId = db.sessionDao().insert(SessionEntity(startedAt = System.currentTimeMillis()))
    }

    // ── Unlock logic ───────────────────────────────────────────────────────

    private fun knownLetterSet(all: List<ItemEntity>): Set<String> =
        all.filter { it.type == ItemType.LETTER && it.isEffectivelyKnown() }
            .map { it.content }.toSet()

    /**
     * A word is unlocked when every letter it contains is known.
     * Ь and Ъ are transparent — they never block unlocking.
     */
    private fun wordUnlocked(item: ItemEntity, knownLetters: Set<String>): Boolean =
        item.content.all { ch ->
            ch.toString() in knownLetters || ch.toString() in setOf("Ь", "Ъ", "·", "-", " ")
        }

    // ── Counting for mode tabs ─────────────────────────────────────────────

    suspend fun unlockedCount(mode: Mode): Int {
        val all = db.itemDao().getAll()
        val known = knownLetterSet(all)
        return when (mode) {
            Mode.LETTERS -> Letters.ordered.size
            Mode.EASY_WORDS -> all.count { it.type == ItemType.WORD_EASY && wordUnlocked(it, known) }
            Mode.HARD_WORDS -> all.count { it.type == ItemType.WORD_HARD && wordUnlocked(it, known) }
        }
    }

    // ── Letter pool (windowed, session-bounded) ────────────────────────────

    /**
     * Returns letters eligible for the session. Introduces up to LETTER_WINDOW
     * non-known letters at once in frequency order. SESSION_LEARNED letters are
     * skipped transparently (done this session). Returns empty when there is
     * nothing left to do (session complete).
     */
    private fun lettersInScope(all: List<ItemEntity>): List<ItemEntity> {
        val byContent = all.filter { it.type == ItemType.LETTER }.associateBy { it.content }
        var newCount = 0
        val inScope = mutableListOf<ItemEntity>()
        for (letter in Letters.ordered) {
            val item = byContent[letter] ?: continue
            when {
                item.state == ItemState.SESSION_LEARNED -> continue
                item.isEffectivelyKnown()               -> inScope.add(item)
                item.state == ItemState.RETENTION_PENDING -> inScope.add(item)
                newCount < LETTER_WINDOW -> { inScope.add(item); newCount++ }
                else -> break
            }
        }
        return inScope
    }

    // ── Item pool for a session ────────────────────────────────────────────

    suspend fun getPool(mode: Mode): List<ItemEntity> {
        val all = db.itemDao().getAll()

        if (mode == Mode.LETTERS) {
            val scoped  = lettersInScope(all)
            val pending = scoped.filter { it.state == ItemState.RETENTION_PENDING }
            val active  = scoped.filter { it.state == ItemState.NEW || it.state == ItemState.IN_SESSION_1 }
            val flagged = scoped.filter { it.state == ItemState.FLAGGED }
            if (pending.isEmpty() && active.isEmpty() && flagged.isEmpty()) return emptyList()
            return (pending.shuffled() + active.shuffled() + flagged.shuffled()).distinctBy { it.id }
        }

        val known = knownLetterSet(all)
        val candidates = all.filter { it.type == itemTypeForMode(mode) && wordUnlocked(it, known) }

        val pending   = candidates.filter { it.state == ItemState.RETENTION_PENDING }
        val active    = candidates.filter { it.state == ItemState.NEW || it.state == ItemState.IN_SESSION_1 }
        val spotCheck = candidates.filter { it.isEffectivelyKnown() }
        val flagged   = candidates.filter { it.state == ItemState.FLAGGED }

        return (pending.shuffled() + active.shuffled() + (flagged + spotCheck).shuffled())
            .distinctBy { it.id }
    }

    private fun itemTypeForMode(mode: Mode) = when (mode) {
        Mode.EASY_WORDS -> ItemType.WORD_EASY
        Mode.HARD_WORDS -> ItemType.WORD_HARD
        Mode.LETTERS    -> ItemType.LETTER
    }

    // ── Answer recording ───────────────────────────────────────────────────

    suspend fun recordAnswer(itemId: String, correct: Boolean) {
        val item = db.itemDao().getById(itemId) ?: return
        val updated = if (correct) applyCorrect(item) else applyWrong(item)
        db.itemDao().update(updated.copy(
            correctTotal = if (correct) item.correctTotal + 1 else item.correctTotal,
            lastSeenSessionId = currentSessionId
        ))
    }

    /**
     * Called when a syllable in the teaching phase is answered wrong.
     * Flags the consonant and vowel letters of that syllable (exactly 2 chars: CV).
     */
    suspend fun flagSyllableLetters(syllable: String) {
        require(syllable.length == 2) { "Expected CV syllable, got: $syllable" }
        for (letter in syllable) {
            val item = db.itemDao().getById("letter:$letter") ?: continue
            if (item.isEffectivelyKnown()) {
                db.itemDao().update(applyWrong(item).copy(lastSeenSessionId = currentSessionId))
            }
        }
    }

    private fun applyCorrect(item: ItemEntity): ItemEntity = when (item.state) {
        ItemState.NEW               -> item.copy(state = ItemState.IN_SESSION_1, inSessionCorrect = 1)
        ItemState.IN_SESSION_1      -> item.copy(state = ItemState.SESSION_LEARNED, inSessionCorrect = 2)
        ItemState.SESSION_LEARNED   -> item
        ItemState.RETENTION_PENDING -> item.copy(state = ItemState.KNOWN)
        ItemState.KNOWN             -> item
        ItemState.FLAGGED           -> item.copy(state = ItemState.KNOWN)
    }

    private fun applyWrong(item: ItemEntity): ItemEntity = when (item.state) {
        ItemState.KNOWN -> item.copy(state = ItemState.FLAGGED)
        else            -> item
    }

    // ── Statistics ─────────────────────────────────────────────────────────

    data class Stats(
        val letters: List<ItemEntity>,
        val sessionCount: Int
    )

    suspend fun getStats(): Stats {
        val all = db.itemDao().getAll()
        val letters = all.filter { it.type == ItemType.LETTER }
            .sortedBy { Letters.ordered.indexOf(it.content) }
        return Stats(letters, db.sessionDao().count())
    }

    fun observeLetters(): Flow<List<ItemEntity>> = db.itemDao().observeByType(ItemType.LETTER)
}

private fun ItemEntity.isEffectivelyKnown() =
    state == ItemState.KNOWN || state == ItemState.FLAGGED
