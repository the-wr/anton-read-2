package com.antonread.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.antonread.app.data.db.ItemEntity
import com.antonread.app.data.model.ItemType
import com.antonread.app.data.model.Mode
import com.antonread.app.data.repository.LearningRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val mode: Mode = Mode.LETTERS,
    val currentItem: ItemEntity? = null,
    // For SYLLABLES_AND_WORDS mode: syllable sub-items before the word itself
    val pendingSyllables: List<ItemEntity> = emptyList(),
    val isShowingWordPhase: Boolean = false,
    val unlockedCounts: Map<Mode, Int> = emptyMap(),
    val isLoading: Boolean = true
)

class SessionViewModel(private val repo: LearningRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var pool: ArrayDeque<ItemEntity> = ArrayDeque()
    // syllables pending for the current word (SYLLABLES_AND_WORDS mode)
    private var wordSyllableQueue: ArrayDeque<ItemEntity> = ArrayDeque()
    private var currentWordItem: ItemEntity? = null

    init {
        viewModelScope.launch {
            repo.initSession()
            refreshUnlockedCounts()
            loadPool(_uiState.value.mode)
        }
    }

    fun setMode(mode: Mode) {
        viewModelScope.launch {
            wordSyllableQueue.clear()
            currentWordItem = null
            loadPool(mode)
        }
    }

    fun answerCorrect() = handleAnswer(correct = true)
    fun answerWrong()   = handleAnswer(correct = false)

    private fun handleAnswer(correct: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            val item = state.currentItem ?: return@launch

            repo.recordAnswer(item.id, correct)

            when {
                // In syllable phase of a word
                state.pendingSyllables.isNotEmpty() -> {
                    if (correct) {
                        val remaining = state.pendingSyllables.drop(1)
                        if (remaining.isEmpty()) {
                            // All syllables passed — show the word itself
                            _uiState.value = state.copy(
                                currentItem = currentWordItem,
                                pendingSyllables = emptyList(),
                                isShowingWordPhase = true
                            )
                        } else {
                            _uiState.value = state.copy(
                                currentItem = remaining.first(),
                                pendingSyllables = remaining
                            )
                        }
                    } else {
                        // Syllable wrong → skip word, move to next pool item
                        currentWordItem = null
                        wordSyllableQueue.clear()
                        advance()
                    }
                }
                else -> advance()
            }
            refreshUnlockedCounts()
        }
    }

    private suspend fun advance() {
        val mode = _uiState.value.mode
        if (pool.isEmpty()) loadPool(mode)

        val next = pool.removeFirstOrNull()
        if (next == null) {
            _uiState.value = _uiState.value.copy(currentItem = null, isLoading = false)
            return
        }

        if (mode == Mode.SYLLABLES_AND_WORDS && next.type == ItemType.WORD_EASY) {
            // Decompose word into syllables for the syllable phase
            val syllableContents = next.content.split("·")
            val allSyllables = repo.itemDao.getAll()
                .filter { it.type == ItemType.SYLLABLE }
                .associateBy { it.content }
            val syllableItems = syllableContents
                .mapNotNull { allSyllables[it] }
                .shuffled()
            if (syllableItems.isEmpty()) {
                advance(); return
            }
            currentWordItem = next
            wordSyllableQueue = ArrayDeque(syllableItems)
            _uiState.value = _uiState.value.copy(
                currentItem = syllableItems.first(),
                pendingSyllables = syllableItems,
                isShowingWordPhase = false
            )
        } else {
            currentWordItem = null
            _uiState.value = _uiState.value.copy(
                currentItem = next,
                pendingSyllables = emptyList(),
                isShowingWordPhase = false,
                isLoading = false
            )
        }
    }

    private suspend fun loadPool(mode: Mode) {
        val items = repo.getPool(mode).toMutableList()
        pool = ArrayDeque(items)
        _uiState.value = _uiState.value.copy(mode = mode, isLoading = false)
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
