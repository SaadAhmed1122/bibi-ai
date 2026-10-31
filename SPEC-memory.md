# Spec: memory (P2)

Shared rules: [SPEC-P2.md](SPEC-P2.md). Background: [docs/features/memory.md](docs/features/memory.md), PRD 0.8 and §14.

## Objective
Bibi remembers what the user asks it to, plus short summaries of past chats, and uses them in later answers. The user can see, search, pin, edit, delete and export everything, and old items expire after 90 days unless pinned.

## Scope
- **Storage (Room migration 1 → 2, a major merge):** `memory_item(id, profileId = 1, kind FACT|SUMMARY, text, pinned, createdAt, updatedAt)` plus a Room `@Fts4` table on `text` for keyword search. PRD §18.3.3 says FTS5; Room only generates FTS3/4, and FTS4 is enough for keyword search (recorded as a deviation). Migration test with the exported schemas (built: Room auto migration; the test creates version 1 from `1.json` by hand because `MigrationTestHelper`'s Android constructor needs instrumentation, then opens it as version 2, which validates against `2.json`).
- **Writes:**
  - "Remember this" on a long-press of any message, and phrases like "remember that …", "my name is …" (deterministic, no model). Bibi replies "Got it, I'll remember that."
  - A never-store filter runs before every write: card numbers (13–19 digits passing Luhn), passwords ("password is …"), government ID patterns. A blocked write gets a short explanation.
  - Safe mode (privacy module) turns all writes off.
- **Automatic summaries:** after the user has been idle 5 minutes with at least 4 new turns, `:inference` makes a one-or-two-sentence summary of those turns and saves it as SUMMARY. Never during a reply; a new message cancels it. Whether LiteRT-LM 0.16.1 allows a second conversation next to the chat one is checked with Context7 and the sources first; if not, the chat conversation is closed and reopened afterwards (≈ 7 s, only while idle) [VERIFY].
- **Use in answers:**
  - pinned items go into the system prompt when the conversation opens (cap ≈ 150 estimated tokens);
  - the top 3 keyword matches for the user's message are added to that turn's text sent to the model (not shown or saved), cap ≈ 100 estimated tokens.
- **Retention:** options 1 week, 1 month, 3 months (default), 1 year, forever (FR-093). A daily WorkManager job (`androidx.work`, new dependency) deletes expired unpinned items. Deletes are real deletes plus `PRAGMA secure_delete` (best effort on flash; UI copy doesn't over-claim).
- **Memory screen** (Home → Memory): list, search, pin/unpin, edit, delete, retention picker, Export to `bibimem.json` through the system file picker (no permission).

## Acceptance criteria
1. Migration test 1 → 2 keeps every P1 message. DAO tests: FTS search, retention delete keeps pinned items, boundary day.
2. Unit tests: never-store filter (positive and negative cases), phrase detection, retrieval budget, export JSON round-trip.
3. On the vivo: "remember that my sister is Sara" → later "what's my sister's name?" answers Sara after a force-stop. A summary appears after idle; a reply is never delayed by it (TTFT with and without a pending summary, in the PR). Both orientations.

## Out of scope
Semantic search and embeddings (open item 9), the six §14 memory types, encrypted export, import, "Recently deleted", multiple profiles (the `profileId` column exists).
