# Feature: Memory

## Purpose
Bibi remembers what the user chooses to keep, entirely on device. Memory must be small, correctable, explainable, and deletable (PRD §14.1).

## Requirements
- **PRD 0.8 (MVP behavior):**
  - Manual "remember this".
  - Automatic short summaries.
  - Pin, search, delete, and export.
  - **Default retention of 90 days**, user-adjustable.
- **Only if a gate passes** (0.8): import of an exported backup.
- **Retention:** FR-093 offers 1 week, 1 month, 3 months (default), 1 year, or forever. Pinned items are excluded. 0.10 keeps the 90-day default.
- **FR-094:** pin, unpin, edit, delete, and search.
- **FR-097:** export as JSON and as an encrypted archive (§14.7).
- **FR-099:** a memory dashboard.
- **FR-181:** "Erase everything" with secure deletion.
- **FR-092:** a consent policy — Ask for sensitive items, Auto for non-sensitive items (§14.4).
- **Never stored** (§14.4): government ID numbers, full card numbers, passwords.
- **PRD §18:** one encrypted Room database. User tables carry `profileId` and `deletedAt`. A purge job runs `secure_delete` and `VACUUM`. Migrations are tested.

## Design notes
- **Write path** (§14.4):
  - A deterministic pre-filter ("remember that…", "my name is…") gives zero-LLM-cost writes.
  - Summaries and extraction run after the response, at low priority, and are deferrable. On T1 they may wait until the phone is charging (§13.7).
- **Storage** (§14.3, §18.3.3): memory tables plus an FTS5 virtual table for keyword search.
  - A vector index with an embedding model is described in §14.3 but costs RAM and a model. MVP uses FTS5 first; semantic search is [OPEN] pending the S2 RAM headroom.
- **Retrieval** (§14.5): pinned items, then top-K FTS results, within a token budget, into the Prompt Builder.
- **Retention job** (§14.6): WorkManager, requires charging and device idle. Deletes expired unpinned rows, then purges.
- **Recently deleted** (§14.6): a 24-hour soft-delete window, plus a "Delete now" option that deletes securely.
- **Export** (§14.7): readable `bibimem.json`, or an encrypted `.bibimem` (Argon2id → AES-256-GCM). Embeddings aren't exported.
- **Profiles:** single profile in MVP, but `profileId` is in the schema from day one (FR-101).

## Risks
- **RAM and battery:** extraction and summarization reuse the LLM. On a 4 GB device they must never run during a user turn.
- **Extraction quality:** small models produce noisy facts. Default sensitive items to Ask.
- **Secure deletion on flash storage** is best-effort. Don't over-claim it in UI copy [VERIFY wording].
- **Scope creep:** the §14 taxonomy has six memory types; 0.8 asks for much less.

## Open questions
- Keyword-only search, or keyword plus semantic, in MVP? [OPEN]
- Which memory types does MVP expose in the UI (facts and summaries only?) [OPEN]
- Is the 24-hour "Recently deleted" window in MVP scope? 0.8 doesn't mention it [OPEN].
- Encrypted export in MVP, or plain JSON first? [OPEN]

## Test plan
- **Unit tests:** retention policy (expired, pinned, and boundary-day cases), the "never store" pattern filter, and the consent policy.
- **Room:** `MigrationTestHelper` for every migration; DAO tests for FTS search.
- **Export round-trip equality test** (FR-097). Import tests apply only if the gate passes.
- **Secure deletion check:** PRD Appendix D.4.
- **Manual (A23):** extraction never delays a reply. Measure reply latency with and without the job queued.
