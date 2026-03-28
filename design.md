# Russian Reading App — Design Document

## Overview

An Android tablet app for daily parent-guided Russian reading practice with a 6-year-old child. The parent holds the tablet, the child reads aloud, and the parent taps Correct or Wrong. Content unlocks progressively as items are mastered.

---

## Core Learning Model

### Item States

Each item (letter, syllable, or word) moves through the following states:

```
new → [in-session learning] → session_learned → retention_pending → known
                                                                      ↓
                                                               flagged_for_review
```

| State | Description |
|---|---|
| `new` | Never attempted or reset after incomplete session |
| `in_session_1` | 1 correct answer in the current session |
| `session_learned` | 2 correct answers in the current session |
| `retention_pending` | Session ended with `session_learned`; awaiting next-session confirmation |
| `known` | Correct once in a new session after `retention_pending` |
| `flagged_for_review` | `known` item answered wrong; shown more frequently |

### Rules

- **Correct answer**: advances state within the above flow
- **Wrong answer**: no regression, no progress — item stays in current state
- **Wrong on `known` item**: moves to `flagged_for_review`; re-graduates to `known` after 1 correct in any future session
- **Session ends with `in_session_1`**: resets to `new` — partial progress within a session doesn't carry over
- **Session ends with `session_learned`**: advances to `retention_pending`

### Session Boundary

A new session begins when the app starts after a minimum gap of **3 hours** since the last session ended. Reopening the app within 3 hours continues the existing session. A **debug button** on the main screen allows forcing a new session for testing.

### Item Pool Composition During a Session

Each item shown is drawn from the following priority mix:
- `retention_pending` items: highest priority (confirm retention)
- `in_session` / `new` items: primary learning content
- `known` / `flagged_for_review` items: ~15% spot-checks

If a mode has no new or pending items, it fills entirely with known items for maintenance review.

---

## Content Hierarchy

```
Letters
  └─ unlock Syllables (CV pairs, hard and soft variants)
       └─ unlock Easy Words (all syllables known)
            │    shown in: "Syllables + Easy Words" (with syllable phase)
            │              "Easy Words" (word directly, no syllable phase)
            └─ unlock Hard Words (all letters in word known)
```

### Letters

33 letters introduced in **frequency order** (optimizes how quickly syllables unlock):

```
о е а и н т с р в л к м д п у я ы з б г ь ч й х ж ё ш ю ц щ э ф ъ
```

> **Note:** `ь` (soft sign) and `ъ` (hard sign) are not syllable-forming. They are taught as letters but do not participate in syllable unlocking directly — their role is explained contextually when encountered in words.

### Syllables

- Format: **consonant + vowel** (CV), hard and soft variants are separate items
  - Hard: ма, мо, му, мы, мэ
  - Soft: мя, мё, мю, ми, ме
- **Unlock condition**: both the consonant letter and the vowel letter must be `known`
- Standalone vowels (а, о, у, и, э, ы, я, ё, ю, е) are considered known when the letter is known; they are not separate syllable items
- Invalid combinations in Russian orthography (жы, шы, etc.) are excluded

Approximate total: ~150–180 usable CV pairs.

### Easy Words

- Structure: words composed **entirely of known syllables**, max ~3 syllables, CV-dominant structure (e.g., ма-ши-на, со-ба-ка, ре-бя-та)
- **Unlock condition**: all syllables in the word are `known`
- Curated list of ~100 words: common nouns, verbs, adjectives, names; no slang
- Syllable boundaries are always visible: **МА·ШИ·НА**

### Hard Words

- Common Russian words from top-1000 frequency lists
- No syllable-structure restriction
- **Unlock condition**: all letters contained in the word are `known`
- Curated for age-appropriateness; nouns, verbs, adjectives, names; no slang

---

## Modes

The parent selects a mode at the start of each session and can switch modes at any time during the session. All modes share the **same unified knowledge graph** — progress in one mode is reflected everywhere.

| Mode | Content Shown | Unlock Requirement |
|---|---|---|
| **Letters** | Letters only, in frequency order | — |
| **Syllables** | CV syllable pairs | Both letters `known` |
| **Syllables + Easy Words** | Syllables interleaved with easy words (syllable phase first) | All syllables in word `known` |
| **Easy Words** | Easy words only, no syllable phase — word shown directly | All syllables in word `known` |
| **Hard Words** | Words from frequency list | All letters in word `known` |

Each mode tab/button displays the **count of currently unlocked items** (e.g., "Syllables 24").

---

## Word Mode Flows

### Syllables + Easy Words mode

When a word is selected for display:

1. **Syllable phase**: each syllable of the word is shown individually, in random order, judged with Correct / Wrong buttons
   - Correct → syllable progresses per general learning rules
   - Wrong → word is immediately **locked** for this session; the syllable's state updates per general rules (no regression, no progress); word does not advance to display phase
2. **Word phase** (only if all syllables were correct): the full word is shown with visible syllable boundaries (e.g., **МА·ШИ·НА**), judged with Correct / Wrong buttons
   - The word item itself follows the same state machine as any other item

### Easy Words mode

Word is shown directly with syllable boundaries visible (no syllable phase). Judged with Correct / Wrong. Same state machine applies to the word item.

---

## Screen Layouts

### Main Session Screen (landscape)

```
┌─────────────────────────────────────────────────────────────────┐
│  [Letters 8] [Syllables 24] [Syl+Words 3] [Words 5] [Hard 0]  [Stats] [Dbg] │
│                                                                 │
│                                                                 │
│                                                                 │
│                           М А                                  │  ← enormous Cyrillic text
│                                                                 │
│                                                                 │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤  ← 80% / 20% split
│       [✓  Correct]                    [✗  Wrong]               │  ← lower 20%
└─────────────────────────────────────────────────────────────────┘
```

- **Correct** is on the **left**, **Wrong** on the **right**
- Upper 80% is pure content space — font can be as large as needed (target: fills ~50% of height for single syllables/letters)
- Lower 20% contains only the two full-width buttons, split evenly
- **Stats** and **Debug** (force new session) are in the **top-right corner**, small
- Mode tabs span the top bar alongside Stats/Debug

### Statistics Screen

```
┌─────────────────────────────────────────────────────────────────┐
│  ← Back                        Statistics                       │
│                                                                 │
│  Letters      ████████░░░░░░░░░░░░  8 / 33 known               │
│               о  е  а  и  н  т  с  р  [░м  ░д  ░п  ░у ...]    │  ← known=bold, unknown=dim
│                                                                 │
│  Syllables    ████░░░░░░░░░░░░░░░░  24 / 178 unlocked           │
│               ██░░░░░░░░░░░░░░░░░░  6 / 24 known               │
│               на  ро  та  са  ве  ло  [░ма  ░со  ░ре ...]      │  ← known first, then unlocked
│                                                                 │
│  Sessions     12 total                                          │
└─────────────────────────────────────────────────────────────────┘
```

- Known items are shown **bold/full opacity**, unlocked-but-not-known are **dimmed**
- Items wrap across multiple lines if needed
- Scrollable screen

---

## Data Model (conceptual)

### `items` table
| field | type | notes |
|---|---|---|
| id | string | e.g., `letter:м`, `syllable:ма`, `word:машина` |
| type | enum | `letter`, `syllable`, `word_easy`, `word_hard` |
| content | string | display text |
| state | enum | `new`, `in_session_1`, `session_learned`, `retention_pending`, `known`, `flagged` |
| correct_total | int | lifetime correct count |
| last_seen_session | int | session ID of last appearance |
| in_session_correct | int | resets each session |

### `sessions` table
| field | type | notes |
|---|---|---|
| id | int | auto-increment |
| started_at | timestamp | |
| ended_at | timestamp | null if current |

### Unlock graph
Computed at runtime from item states — no need to store separately.

---

## Content Lists

### Letter frequency order
```
о е а и н т с р в л к м д п у я ы з б г ь ч й х ж ё ш ю ц щ э ф ъ
```

### Word lists
Generated and curated; tagged by type and syllable structure:
- **Easy words** (~300): CV-dominant, ≤3 syllables, common nouns/verbs/adjectives/names
- **Hard words** (~1500): top Russian frequency list, age-appropriate, all word types

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Local storage**: Room (SQLite ORM)
- **Architecture**: MVVM with ViewModel + StateFlow
- **No backend, no audio, no network required**
- **Target**: Android tablet, landscape orientation, API 24+

---

## Out of Scope (for now)

- Audio pronunciation
- Multiple child profiles
- Cloud sync
- Stress marks on words
- Animations or rewards
