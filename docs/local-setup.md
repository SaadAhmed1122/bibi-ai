# Working locally, and resuming when you're away

How to get the same Claude Code setup on your own laptop that the cloud sessions use, and how to carry on from your phone or the cloud when you're not at the laptop. Everything project-specific lives in this repo, so a clone plus the steps below gives you the same agents, skills, plugins and rules.

## What the repo already brings
| Piece | Where | Loads how |
|---|---|---|
| Project rules | `CLAUDE.md`, `.claude/CLAUDE.md` | Automatically in every session |
| Subagents: spike-analyst, android-engineer, scope-guard, docs-keeper, repo-keeper | `.claude/agents/` | Automatically |
| Skills: graphify, three Strix skills | `.claude/skills/` | Automatically |
| Agent Skills plugin (`/spec`, `/plan`, `/build`, `/review`, …) | `.claude/settings.json` | Prompt on first open → accept |
| **agent-board** (Agents pane, toasts, `/agent-board`) | `plugins/agent-board/`, listed by `.claude-plugin/marketplace.json` | Prompt on first open → accept |
| impeccable, ui-ux-pro-max, last30days, humanizer, ponytail (design, research and writing skills; see tooling.md) | `.claude/settings.json` | Prompt on first open → accept |
| Context7 and 21st MCP servers | `.mcp.json` | Need keys in your shell environment (below) |
| Graphify hooks | `.claude/settings.json` | Do nothing until `graphify` is installed |

## One-time setup on your laptop
1. **Claude Code:** the CLI or the Claude Desktop app, signed in with your account.
2. **Git access:** you cloned the private repo, so it works. The agent-board marketplace is fetched from the same private repo, so it uses the same access.
3. **Android:**
   - Android Studio (2026 stable) with JDK 17.
   - The phone with USB debugging on.

   On your laptop the spikes build normally with Gradle; the type-check workaround used in the cloud isn't needed.
4. **Python tools:**
   ```bash
   uv tool install graphifyy
   ```
5. **Node 22** (only for `tools/render_svg.js` and the design scripts).
6. **API keys:** in your shell profile (for example `~/.zshrc`), never in the repo:
   ```bash
   export CONTEXT7_API_KEY=...   API_KEY_21ST=...
   ```
   Context7's key is optional (higher rate limits). The Strix keys (`STRIX_LLM`, `LLM_API_KEY`) are only needed for security scans. See [tooling.md](tooling.md).
7. **Optional:** Strix CLI and Docker, for `find-security-vulnerabilities-in-code`.

## First run
1. Update your clone: `git fetch --all --prune`, then `git checkout main && git pull`.
2. Open Claude Code in the repo folder (`claude` in a terminal there, or open the folder in the Desktop app).
3. Accept the prompts: trust the folder, install `agent-skills`, `agent-board`, `impeccable`, `ui-ux-pro-max`, `last30days`, `humanizer` and `ponytail`, enable the `context7` and `21st` MCP servers.
4. Check that everything loaded:
   - `/agents` lists the five project agents.
   - `/plugin` shows `agent-skills`, `agent-board`, `impeccable`, `ui-ux-pro-max`, `last30days`, `humanizer` and `ponytail`.
   - `/mcp` shows `context7` and `21st`.
   - `/agent-board` opens the Agents pane.
5. Optional: build the code map with `graphify update .`. Its output in `graphify-out/` is git-ignored.

## Where the work stands
The cloud sessions pushed everything; nothing lives only in the cloud. All four spike apps are merged to `main` (open each spike folder on its own in Android Studio). Merged branches are kept for now (owner's choice, 2026-10-30).

| Work | Where | PR |
|---|---|---|
| S1 overlay | `main` → `spikes/s1-overlay/` | #10 (merged) |
| S2 local LLM | `main` → `spikes/s2-llm/` | #12, #22 (merged) |
| S2 chat-mode soak | `feature/5-s2-conversation-soak` | #23 (open) |
| S3a + S3b voice | `main` → `spikes/s3-voice/` | #14 (merged) |
| S4 actions | `main` → `spikes/s4-tools/` | #13, #19 (merged) |
| `TESTING.md`, this guide, plugins | `main` | #15, #16, #18, #20, #21 (merged) |

Current state and next steps: [project_status.md](project_status.md).

To have several branches open at once without switching (as the cloud sessions did), use `git worktree add ../bibi-s2 spike/S2-llm`.

## Resuming when you're away from the laptop
**A. Keep the laptop session going (Remote Control).** Use this when the laptop stays on and online.
- Turn it on in the Desktop app (*Settings → Claude Code → Remote Control*), or run `claude remote-control` in a terminal in the repo folder.
- The session then shows up in the Claude Code app on your phone, and you keep working on your laptop's files and tools.
- It stops if the laptop sleeps or goes offline.

**B. Use a cloud session.** Use this when the laptop is off.
- Start a Claude Code session on the web or in the mobile app with `SaadAhmed1122/bibi-ai` selected.
- It works on GitHub, not on your laptop's files, so **push before you leave** (or ask `repo-keeper` to).
- When you're back, `git pull` the branches it worked on.

**Before you leave the laptop:**
1. Commit and push the current branch.
2. Make sure `docs/project_status.md` → "Last session stopping point" and "Next step" are current (CLAUDE.md §6).

The next session, local or cloud, starts by reading that file.
