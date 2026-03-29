package com.antonread.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.antonread.app.data.db.ItemEntity
import com.antonread.app.data.model.ItemType
import com.antonread.app.data.model.Mode
import com.antonread.app.data.repository.LearningRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val mode: Mode = Mode.LETTERS,
    val currentItem: ItemEntity? = null,
    // Non-null during the syllable teaching phase before an easy word
    val syllablePhase: SyllablePhase? = null,
    val unlockedCounts: Map<Mode, Int> = emptyMap(),
    val isLoading: Boolean = true
)

/** Teaching phase shown before an easy word. [remaining] is shuffled syllables yet to show. */
data class SyllablePhase(
    val wordItem: ItemEntity,
    val currentSyllable: String,
    val remaining: List<String> // excludes currentSyllable
)

class SessionViewModel(private val repo: LearningRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var pool: ArrayDeque<ItemEntity> = ArrayDeque()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repo.initSession()
            refreshUnlockedCounts()
            loadPool(_uiState.value.mode)
        }
    }

    fun setMode(mode: Mode) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadPool(mode)
        }
    }

    fun answerCorrect() = handleAnswer(correct = true)
    fun answerWrong()   = handleAnswer(correct = false)

    private fun handleAnswer(correct: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            val phase = state.syllablePhase

            if (phase != null) {
                // ── Syllable teaching phase ──────────────────────────────
                if (correct) {
                    if (phase.remaining.isEmpty()) {
                        // All syllables passed — show the word
                        _uiState.value = state.copy(
                            currentItem = phase.wordItem,
                            syllablePhase = null
                        )
                    } else {
                        _uiState.value = state.copy(
                            syllablePhase = phase.copy(
                                currentSyllable = phase.remaining.first(),
                                remaining = phase.remaining.drop(1)
                            )
                        )
                    }
                } else {
                    // Flag the two letters of the wrong syllable, skip word
                    repo.flagSyllableLetters(phase.currentSyllable)
                    _uiState.value = state.copy(syllablePhase = null)
                    advance()
                }
            } else {
                // ── Normal item (letter or word) ─────────────────────────
                val item = state.currentItem ?: return@launch
                val wasNew = item.state == com.antonread.app.data.model.ItemState.NEW
                repo.recordAnswer(item.id, correct)
                // A correct answer on a NEW letter moves it to IN_SESSION_1 —
                // it needs one more correct answer, so re-queue it at the end of the pool.
                if (correct && wasNew && state.mode == Mode.LETTERS) {
                    val updated = repo.itemDao.getById(item.id)
                    if (updated != null) pool.addLast(updated)
                }
                advance()
            }
            refreshUnlockedCounts()
        }
    }

    private suspend fun advance() {
        val mode = _uiState.value.mode
        // Only refill for word modes — letter mode stops when the session pool is exhausted
        if (pool.isEmpty() && mode != Mode.LETTERS) {
            pool = ArrayDeque(repo.getPool(mode))
        }

        val next = pool.removeFirstOrNull()
        if (next == null) {
            _uiState.value = _uiState.value.copy(currentItem = null, syllablePhase = null, isLoading = false)
            return
        }

        if (mode == Mode.EASY_WORDS) {
            // Start syllable teaching phase: content is "МА·ШИ·НА"
            val syllables = next.content.split("·").shuffled()
            _uiState.value = _uiState.value.copy(
                currentItem = null,
                syllablePhase = SyllablePhase(
                    wordItem = next,
                    currentSyllable = syllables.first(),
                    remaining = syllables.drop(1)
                ),
                isLoading = false
            )
        } else {
            _uiState.value = _uiState.value.copy(
                currentItem = next,
                syllablePhase = null,
                isLoading = false
            )
        }
    }

    private suspend fun loadPool(mode: Mode) {
        pool = ArrayDeque(repo.getPool(mode))
        _uiState.value = _uiState.value.copy(mode = mode, syllablePhase = null, isLoading = false)
        advance()
    }

    private suspend fun refreshUnlockedCounts() {
        val counts = Mode.entries.associateWith { repo.unlockedCount(it) }
        _uiState.value = _uiState.value.copy(unlockedCounts = counts)
    }

    fun forceNewSession() {
        viewModelScope.launch {
            repo.forceNewSession()
            loadPool(_uiState.value.mode)
        }
    }

    class Factory(private val repo: LearningRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(repo) as T
    }
}
