package com.antonread.app.data.repository

import com.antonread.app.data.content.Letters
import com.antonread.app.data.content.Seeder
import com.antonread.app.data.content.Syllables
import com.antonread.app.data.db.AppDatabase
import com.antonread.app.data.db.ItemEntity
import com.antonread.app.data.db.SessionEntity
import com.antonread.app.data.model.ItemState
import com.antonread.app.data.model.ItemType
import com.antonread.app.data.model.Mode
import kotlinx.coroutines.flow.Flow

private const val SESSION_GAP_MILLIS = 3 * 60 * 60 * 1000L // 3 hours
private const val SPOT_CHECK_RATIO = 0.15
private const val LETTER_WINDOW = 3 // max non-known letters shown at once

class LearningRepository(private val db: AppDatabase) {

    val itemDao get() = db.itemDao()
    val sessionDao get() = db.sessionDao()

    // ── Session management ─────────────────────────────────────────────────

    private var currentSessionId: Long = 0L

    suspend fun initSession(forceNew: Boolean = false): Long {
        db.itemDao().insertAll(Seeder.allItems()) // no-op if already seeded (IGNORE)

        val latest = db.sessionDao().getLatest()
        val now = System.currentTimeMillis()
        val isNewSession = forceNew
            || latest == null
            || (latest.endedAt != null && now - latest.endedAt > SESSION_GAP_MILLIS)
            || (latest.endedAt == null && now - latest.startedAt > SESSION_GAP_MILLIS)

        if (isNewSession) {
            // Close previous session if open
            if (latest != null && latest.endedAt == null) {
                closeSession(latest.id)
            }
            val id = db.sessionDao().insert(SessionEntity(startedAt = now))
            currentSessionId = id
        } else {
            currentSessionId = latest!!.id
        }
        return currentSessionId
    }

    private suspend fun closeSession(sessionId: Long) {
        val session = db.sessionDao().getLatest() ?: return
        db.sessionDao().update(session.copy(endedAt = System.currentTimeMillis()))
        db.itemDao().applySessionEndTransitions()
        db.itemDao().resetSessionCounters()
    }

    suspend fun forceNewSession() {
        val latest = db.sessionDao().getLatest()
        if (latest != null && latest.endedAt == null) {
            closeSession(latest.id)
        }
        val id = db.sessionDao().insert(SessionEntity(startedAt = System.currentTimeMillis()))
        currentSessionId = id
    }

    // ── Unlock logic ───────────────────────────────────────────────────────

    private suspend fun knownLetters(): Set<String> =
        db.itemDao().getByType(ItemType.LETTER)
            .filter { it.state == ItemState.KNOWN || it.state == ItemState.FLAGGED }
            .map { it.content }
            .toSet()

    private fun syllableUnlocked(syllable: String, known: Set<String>): Boolean {
        val (c, v) = Syllables.lettersOf(syllable)
        return c in known && v in known
    }

    private suspend fun knownSyllables(): Set<String> {
        val known = knownLetters()
        return db.itemDao().getByType(ItemType.SYLLABLE)
            .filter { it.state == ItemState.KNOWN || it.state == ItemState.FLAGGED }
            .filter { syllableUnlocked(it.content, known) }
            .map { it.content }
            .toSet()
    }

    // ── Counting for mode tabs ─────────────────────────────────────────────

    suspend fun unlockedCount(mode: Mode): Int {
        return when (mode) {
            Mode.LETTERS -> Letters.ordered.size
            Mode.SYLLABLES -> {
                val known = knownLetters()
                db.itemDao().getByType(ItemType.SYLLABLE).count { syllableUnlocked(it.content, known) }
            }
            Mode.SYLLABLES_AND_WORDS, Mode.EASY_WORDS -> {
                val knownSyl = knownSyllables()
                db.itemDao().getByType(ItemType.WORD_EASY).count { wordEasyUnlocked(it, knownSyl) }
            }
            Mode.HARD_WORDS -> {
                val known = knownLetters()
                db.itemDao().getByType(ItemType.WORD_HARD).count { wordHardUnlocked(it, known) }
            }
        }
    }

    private fun wordEasyUnlocked(item: ItemEntity, knownSyllables: Set<String>): Boolean {
        // content is "МА·ШИ·НА" — each segment must be a known syllable
        val parts = item.content.split("·")
        return parts.all { it in knownSyllables }
    }

    private fun wordHardUnlocked(item: ItemEntity, knownLetters: Set<String>): Boolean {
        return item.content.all { ch ->
            ch.toString() in knownLetters || ch.toString() in setOf("Ь", "Ъ", "-", " ")
        }
    }

    // ── Item pool for a session ────────────────────────────────────────────

    /**
     * Returns letters eligible for the current session.
     * Letters are introduced in frequency order, max [LETTER_WINDOW] non-known letters at once.
     * All already-known (and flagged) letters are included for spot-checks.
     * Letters beyond the window are hidden until earlier ones are mastered.
     */
    private fun lettersInScope(all: List<ItemEntity>): List<ItemEntity> {
        val byContent = all.filter { it.type == ItemType.LETTER }.associateBy { it.content }
        var activeCount = 0
        val inScope = mutableListOf<ItemEntity>()
        for (letter in Letters.ordered) {
            val item = byContent[letter] ?: continue
            if (item.isEffectivelyKnown()) {
                inScope.add(item) // always include known letters (for spot-checks)
            } else {
                if (activeCount < LETTER_WINDOW) {
                    inScope.add(item)
                    activeCount++
                } else {
                    break // everything after the window is hidden
                }
            }
        }
        return inScope
    }

    suspend fun getPool(mode: Mode): List<ItemEntity> {
        val all = db.itemDao().getAll()
        val knownLetterSet = all.filter { it.type == ItemType.LETTER && it.isEffectivelyKnown() }
            .map { it.content }.toSet()
        val knownSylSet = all.filter { it.type == ItemType.SYLLABLE && it.isEffectivelyKnown()
                && syllableUnlocked(it.content, knownLetterSet) }
            .map { it.content }.toSet()

        val candidates: List<ItemEntity> = when (mode) {
            Mode.LETTERS -> lettersInScope(all)
            Mode.SYLLABLES -> all.filter { it.type == ItemType.SYLLABLE
                    && syllableUnlocked(it.content, knownLetterSet) }
            Mode.SYLLABLES_AND_WORDS -> all.filter {
                (it.type == ItemType.SYLLABLE && syllableUnlocked(it.content, knownLetterSet))
                || (it.type == ItemType.WORD_EASY && wordEasyUnlocked(it, knownSylSet))
            }
            Mode.EASY_WORDS -> all.filter { it.type == ItemType.WORD_EASY
                    && wordEasyUnlocked(it, knownSylSet) }
            Mode.HARD_WORDS -> all.filter { it.type == ItemType.WORD_HARD
                    && wordHardUnlocked(it, knownLetterSet) }
        }

        // Partition by priority
        val pending   = candidates.filter { it.state == ItemState.RETENTION_PENDING }
        val active    = candidates.filter { it.state == ItemState.NEW || it.state == ItemState.IN_SESSION_1 }
        val spotCheck = candidates.filter { it.isEffectivelyKnown() }
        val flagged   = candidates.filter { it.state == ItemState.FLAGGED }

        // Weighted shuffle: pending first, then active, then flagged mixed with spot-checks
        return (pending.shuffled() + active.shuffled() + (flagged + spotCheck).shuffled())
            .distinctBy { it.id }
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

    private fun applyCorrect(item: ItemEntity): ItemEntity = when (item.state) {
        ItemState.NEW -> item.copy(state = ItemState.IN_SESSION_1, inSessionCorrect = 1)
        ItemState.IN_SESSION_1 -> item.copy(state = ItemState.SESSION_LEARNED, inSessionCorrect = 2)
        ItemState.SESSION_LEARNED -> item // already maxed for this session
        ItemState.RETENTION_PENDING -> item.copy(state = ItemState.KNOWN)
        ItemState.KNOWN -> item // stays known
        ItemState.FLAGGED -> item.copy(state = ItemState.KNOWN)
    }

    private fun applyWrong(item: ItemEntity): ItemEntity = when (item.state) {
        ItemState.KNOWN -> item.copy(state = ItemState.FLAGGED)
        else -> item // no regression for non-known items
    }

    // ── Statistics ─────────────────────────────────────────────────────────

    data class Stats(
        val letters: List<ItemEntity>,
        val syllables: List<ItemEntity>,
        val unlockedSyllables: List<ItemEntity>,
        val sessionCount: Int
    )

    suspend fun getStats(): Stats {
        val all = db.itemDao().getAll()
        val knownLetterSet = all.filter { it.type == ItemType.LETTER && it.isEffectivelyKnown() }
            .map { it.content }.toSet()
        val letters = all.filter { it.type == ItemType.LETTER }
            .sortedBy { Letters.ordered.indexOf(it.content) }
        val allSyllables = all.filter { it.type == ItemType.SYLLABLE }
        val unlocked = allSyllables.filter { syllableUnlocked(it.content, knownLetterSet) }
        val sessionCount = db.sessionDao().count()
        return Stats(letters, allSyllables, unlocked, sessionCount)
    }

    // ── Flows for UI ───────────────────────────────────────────────────────

    fun observeLetters(): Flow<List<ItemEntity>> = db.itemDao().observeByType(ItemType.LETTER)
    fun observeSyllables(): Flow<List<ItemEntity>> = db.itemDao().observeByType(ItemType.SYLLABLE)
}

private fun ItemEntity.isEffectivelyKnown() =
    state == ItemState.KNOWN || state == ItemState.FLAGGED
