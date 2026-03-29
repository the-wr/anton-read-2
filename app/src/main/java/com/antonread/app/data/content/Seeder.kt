package com.antonread.app.data.content

import com.antonread.app.data.db.ItemEntity
import com.antonread.app.data.model.ItemType

/**
 * Produces the canonical set of ItemEntity records to seed the database on first launch.
 * Uses IGNORE on conflict so re-running is safe.
 */
object Seeder {

    fun allItems(): List<ItemEntity> = buildList {
        // Letters
        for (letter in Letters.ordered) {
            add(ItemEntity(id = "letter:$letter", type = ItemType.LETTER, content = letter))
        }
        // Easy words — content stored as syllable string "МА·ШИ·НА"
        for (word in EasyWords.list) {
            add(ItemEntity(id = "word_easy:${word.word}", type = ItemType.WORD_EASY, content = word.syllables))
        }
        // Hard words — content stored as plain word
        for (word in HardWords.list) {
            add(ItemEntity(id = "word_hard:$word", type = ItemType.WORD_HARD, content = word))
        }
    }
}
