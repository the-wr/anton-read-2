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
  └─ unlock Easy Words (all letters in word known)
  └─ unlock Hard Words (all letters in word known)
```

### Letters

33 letters introduced in **frequency order**:

```
о е а и н т с р в л к м д п у я ы з б г ь ч й х ж ё ш ю ц щ э ф ъ
```

> **Note:** `ь` and `ъ` are taught as letters but are transparent in unlock conditions — a word containing them only requires the other letters to be `known`.

### Easy Words

- **Every syllable is strictly CV** (consonant + vowel, no clusters, no ь/ъ, no standalone vowels) — ≤3 syllables (e.g., ма-ши-на, со-ба-ка, ре-бя-та)
- **Unlock condition**: all letters in the word are `known`
- Curated list of ~300 words: nouns, verbs, adjectives, names; no slang
- Always displayed with syllable boundaries visible: **МА·ШИ·НА**
- Preceded by a **syllable teaching phase** (see Word Mode Flow)

### Hard Words

- Common words from top Russian frequency lists, no syllable-structure restriction
- **Unlock condition**: all letters in the word are `known` (ь/ъ exempt)
- Curated list of ~1500 words: age-appropriate, all word types, no slang

---

## Modes

The parent selects a mode at the start of each session and can switch modes at any time during the session. All modes share the **same unified knowledge graph** — progress in one mode is reflected everywhere.

| Mode | Content Shown | Unlock Requirement |
|---|---|---|
| **Letters** | Letters only, in frequency order | — |
| **Easy Words** | Easy words with syllable teaching phase | All letters in word `known` |
| **Hard Words** | Words from top frequency list | All letters in word `known` |

Each mode tab/button displays the **count of currently unlocked items**.

---

## Easy Word Flow

When an easy word is selected:

1. **Syllable teaching phase**: each CV syllable of the word is shown individually, in random order
   - Correct → no state change (syllables are not tracked items)
   - Wrong → the two **letters** of that syllable (always exactly consonant + vowel) are both marked `FLAGGED`; word is removed from the queue for this session; skip to next item
2. **Word phase** (only if all syllables were correct): full word shown with visible syllable boundaries (e.g., **МА·ШИ·НА**), judged with Correct / Wrong
   - The word item follows the standard state machine

Hard words are shown directly — no syllable phase.

---

## Screen Layouts

### Main Session Screen (landscape)

```
┌─────────────────────────────────────────────────────────────────┐
│  [Letters 8] [Easy Words 5] [Hard Words 0]       [Stats] [Dbg] │
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
│  Sessions     12 total                                          │
└─────────────────────────────────────────────────────────────────┘
```

- Known letters shown **bold/full opacity**, not-yet-known shown **dimmed**
- Letters wrap across multiple lines if needed
- Scrollable screen

---

## Data Model (conceptual)

### `items` table
| field | type | notes |
|---|---|---|
| id | string | e.g., `letter:м`, `word_easy:машина`, `word_hard:машина` |
| type | enum | `letter`, `word_easy`, `word_hard` |
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
