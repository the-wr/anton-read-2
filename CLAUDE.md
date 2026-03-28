# AntonRead — CLAUDE.md

Russian reading app for a 6-year-old child. Parent-guided: parent holds tablet, child reads aloud, parent taps Correct or Wrong. Landscape tablet, Android native.

## Build & Deploy

```bash
# Build
gradle assembleDebug

# Install + launch (emulator must be running)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.antonread.app/.MainActivity

# One-liner
gradle assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.antonread.app/.MainActivity
```

**SDK**: `c:/SDK/Android/` (`ANDROID_HOME`)
**Gradle**: system Gradle 9.3.1 (`C:/SDK/gradle/gradle-9.3.1/bin/gradle`)
**Java**: system Java 25
**AGP**: 8.13.2 (NOT 9.x — see Gotchas)
**Min SDK**: 24, **Target**: 35, **Compile**: 35

## Project Structure

```
app/src/main/java/com/antonread/app/
├── MainActivity.kt                  — entry point, simple if/else navigation
├── data/
│   ├── model/ItemState.kt           — ItemState, ItemType, Mode enums
│   ├── db/                          — Room: Entities, ItemDao, SessionDao, AppDatabase
│   ├── content/
│   │   ├── Catalogue.kt             — Letters, Vowels, Consonants, Syllables objects
│   │   ├── Seeder.kt                — seeds DB on first launch (INSERT OR IGNORE)
│   │   ├── EasyWords.kt             — ~300 CV words with syllable breakdowns (МА·ШИ·НА)
│   │   └── HardWords.kt             — ~1500 frequency-list words
│   └── repository/LearningRepository.kt  — all learning logic, unlock rules, session mgmt
└── ui/
    ├── theme/Theme.kt               — colors, AppTheme wrapper
    ├── session/
    │   ├── SessionViewModel.kt      — pool mgmt, answer handling, word flow
    │   └── SessionScreen.kt         — main screen (80/20 split, mode tabs, answer buttons)
    └── stats/StatsScreen.kt         — letters + syllables with chips and progress bars
```

No NavController — navigation is a single `var showStats: Boolean` in `AppNavigation`.

## Learning Model

### Item States
```
NEW → IN_SESSION_1 → SESSION_LEARNED → RETENTION_PENDING → KNOWN
                                                             ↓ (wrong answer)
                                                           FLAGGED
```

- **2 correct in one session** → `SESSION_LEARNED`
- **Session ends** with `SESSION_LEARNED` → `RETENTION_PENDING`
- **Session ends** with `IN_SESSION_1` → back to `NEW` (partial progress lost)
- **1 correct in a later session** (≥3h gap) → `KNOWN`
- **Wrong on `KNOWN`** → `FLAGGED`; recovers to `KNOWN` on next correct in any session
- **Wrong on anything else** → no state change, no regression

### Session Boundary
New session = app start after **3-hour gap** from last session end. Debug button forces new session immediately.

### Item Pool per Session
Priority order: `RETENTION_PENDING` → `NEW`/`IN_SESSION_1` → `KNOWN`/`FLAGGED` (~15% spot-check). Pool refills from DB when exhausted (no infinite recursion — `advance()` refills inline; `loadPool()` does NOT call `advance()` recursively).

## Content & Unlock Rules

### Letter order (frequency-optimised)
`О Е А И Н Т С Р В Л К М Д П У Я Ы З Б Г Ь Ч Й Х Ж Ё Ш Ю Ц Щ Э Ф Ъ`

Ь and Ъ are letters but not syllable-forming — never part of a syllable unlock condition.

### Unlock conditions
| Type | Condition |
|---|---|
| Letter | Always available (shown in frequency order) |
| Syllable (CV) | Both consonant letter AND vowel letter are `KNOWN` |
| Easy word | All syllables in the word are `KNOWN` |
| Hard word | All letters in the word are `KNOWN` |

### Syllable rules
- Hard/soft variants are separate items: `МА` ≠ `МЯ`
- Always-hard consonants (Ж Ш Ц): exclude Ы, soft vowels except И/Е
- Always-soft consonants (Ч Щ Й): exclude Ы, Э, most hard vowels
- Standalone vowels are NOT syllable items — they're "known" when the letter is known

### Word content format
- Easy words stored as syllable string: `"МА·ШИ·НА"` (separator is `·` U+00B7)
- Hard words stored as plain word: `"МАШИНА"`
- Item ID format: `"letter:М"`, `"syllable:МА"`, `"word_easy:МАШИНА"`, `"word_hard:МАШИНА"`

## Modes

| Mode | Enum | Content |
|---|---|---|
| Буквы | `LETTERS` | Letters in frequency order |
| Слоги | `SYLLABLES` | CV pairs |
| Слоги+ | `SYLLABLES_AND_WORDS` | Syllables + easy words with syllable phase |
| Слова | `EASY_WORDS` | Easy words shown directly (no syllable phase) |
| Слова★ | `HARD_WORDS` | Hard words |

All modes share one knowledge graph. Mode tab shows unlocked count.

### Syllables+Words word flow
1. Show each syllable individually (random order) — judged normally
2. Any syllable wrong → word locked for session, skip to next item
3. All syllables correct → show full word with `·` boundaries, judge it

## UI Layout

**Session screen**: top bar (mode chips + Stats + DBG buttons) → 80% content area (auto-scaled font) → 20% answer buttons. Correct = LEFT, Wrong = RIGHT.

**Font scaling** (`ItemDisplay`): `BoxWithConstraints` computes font size based on character count vs available width. Target 80% of width, ~0.55 width/height ratio for Cyrillic bold. Clamped 48–220sp.

**Insets**: `enableEdgeToEdge()` in MainActivity. Top bar adds `statusBarHeight` to top padding. Button row adds `navigationBarHeight` to bottom padding and respects side insets. Stats screen uses `Scaffold`+`TopAppBar` which handles insets automatically.

**Stats screen**: progress bars for letters and syllables only (not words). Known items shown bold, unlocked-not-known shown dimmed, locked not shown.

## Key Gotchas

- **AGP 9.x is incompatible** with `kotlin-android` plugin and KSP together. Use AGP 8.x. Current: 8.13.2.
- `loadPool()` must NOT call `advance()` when pool is empty after refill — causes infinite mutual recursion with rapid UI flickering. `advance()` refills the pool inline.
- `setMode()` cancels the in-flight `loadJob` before launching a new one — prevents concurrent pool loads from stomping each other.
- `repo.itemDao` is a property getter, not a function. Write `repo.itemDao.getAll()`, not `repo.itemDao().getAll()`.
- Easy word content in DB is the syllable string (`МА·ШИ·НА`), not the plain word. The plain word is reconstructed by stripping `·`.
- DB is seeded on every launch with `INSERT OR IGNORE` — safe to re-run.

## Out of Scope
Audio, multiple profiles, cloud sync, stress marks, animations/rewards.
