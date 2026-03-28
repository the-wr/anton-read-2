package com.antonread.app.data.content

/**
 * Russian letters in frequency order.
 * ь and ъ are included as letters but are not syllable-forming.
 */
object Letters {
    val ordered: List<String> = listOf(
        "О", "Е", "А", "И", "Н", "Т", "С", "Р", "В", "Л",
        "К", "М", "Д", "П", "У", "Я", "Ы", "З", "Б", "Г",
        "Ь", "Ч", "Й", "Х", "Ж", "Ё", "Ш", "Ю", "Ц", "Щ",
        "Э", "Ф", "Ъ"
    )
    val signs: Set<String> = setOf("Ь", "Ъ") // not syllable-forming
}

/**
 * All vowels. Hard vowels pair with hard consonants; soft vowels soften the consonant.
 */
object Vowels {
    val hard: List<String> = listOf("А", "О", "У", "Ы", "Э")
    val soft: List<String> = listOf("Я", "Ё", "Ю", "И", "Е")
    val all: List<String> = hard + soft
}

/**
 * Consonants that can form CV syllables.
 */
object Consonants {
    val all: List<String> = listOf(
        "Б", "В", "Г", "Д", "Ж", "З", "К", "Л", "М", "Н",
        "П", "Р", "С", "Т", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Й"
    )

    // These consonants are always hard — cannot take soft vowels
    val alwaysHard: Set<String> = setOf("Ж", "Ш", "Ц")
    // These consonants are always soft — cannot take hard vowels (except И)
    val alwaysSoft: Set<String> = setOf("Ч", "Щ", "Й")
}

/**
 * Generates all valid CV syllables respecting Russian orthography rules.
 */
object Syllables {
    val all: List<String> by lazy {
        buildList {
            for (c in Consonants.all) {
                for (v in Vowels.all) {
                    if (isValid(c, v)) add(c + v)
                }
            }
        }
    }

    private fun isValid(consonant: String, vowel: String): Boolean {
        // Always-hard consonants take only hard vowels (Ж/Ш also take И not Ы by convention)
        if (consonant in Consonants.alwaysHard) {
            if (vowel == "Ы") return false  // жы/шы/цы → invalid
            if (vowel in Vowels.soft && vowel != "И" && vowel != "Е") return false
        }
        // Always-soft consonants take only soft vowels
        if (consonant in Consonants.alwaysSoft) {
            if (vowel in Vowels.hard && vowel != "А" && vowel != "У") return false
            if (vowel == "Ы" || vowel == "Э") return false
        }
        return true
    }

    /** The two constituent letters of a syllable */
    fun lettersOf(syllable: String): Pair<String, String> {
        require(syllable.length == 2) { "Expected CV syllable, got: $syllable" }
        return Pair(syllable[0].toString(), syllable[1].toString())
    }
}
