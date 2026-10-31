# P2 Task 10 (#81): automatic summaries, vivo Y17s, 2026-11-01

**Setup:**
- Gemma 3 1B int4 on LiteRT-LM 0.16.1.
- The summary runs in a **temporary second conversation** opened next to the chat conversation.
- Runs were started with the debug "Summarize now" button (the real trigger is 5 minutes idle after at least 4 new answers).

## Does a second conversation work next to the chat one?

Yes, in 0.16.1:
- no crash;
- the chat conversation kept its cache: the next chat turn's first word came in 1.67 s, with no reopen.

## Getting a usable summary

| Change | Result |
|---|---|
| Instruction as the temporary conversation's system instruction | "Okay..." (7 chars). Gemma 3's template has no system turn, so the instruction was dropped (as in S2) |
| Instruction inside the user message; transcript lines "You:" / "Bibi:" | "Bibi's sister was Sara." (who's who swapped) |
| Lines "User:" / "Bibi:", instruction says "write about User as you" (kept) | "You asked about your trip planning; Bibi suggested careful planning regarding Skardu's altitude and trekking preparedness. You want hiking experiences with your friend Ali." (172 chars) |
| The same, over older history that contained "my password is hunter2" | Refused by the never-store filter (PASSWORD); nothing saved |

## Time

- About 15 s for 3 turns plus answers, 22–36 s for a full 1,800-character transcript, 5.8 s for 1 turn. It only runs after 5 idle minutes.
- **A new message cancels a running summary, but not instantly.** During the transcript prefill the cancel took effect about 8 s later (summary stopped at 10.2 s, started 2 s before the send), then the reply's first word came 1.56 s later. Worst case, a message sent just as a summary starts waits up to about 10 s [VERIFY whether 0.16.1 can cancel mid-prefill].

## Afterwards

The test summaries and test chat turns were removed: the chat database was restored from the backup taken before Task 9, and the summary marker was set to the newest existing message.
