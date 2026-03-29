package com.antonread.app.data.model

enum class ItemState {
    NEW,
    IN_SESSION_1,       // 1 correct this session
    SESSION_LEARNED,    // 2 correct this session
    RETENTION_PENDING,  // session ended with SESSION_LEARNED; awaiting next-session confirm
    KNOWN,
    FLAGGED             // known item answered wrong; needs re-confirmation
}

enum class ItemType {
    LETTER,
    WORD_EASY,
    WORD_HARD
}

enum class Mode {
    LETTERS,
    EASY_WORDS,
    HARD_WORDS
}
