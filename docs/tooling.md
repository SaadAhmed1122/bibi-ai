# Developer tooling

Five agent tools are wired into this repository for the implementation phases (P1 onward). This file covers one-time setup on each machine and when each tool is used. The short version of the rules is in `CLAUDE.md` §9.

None of these tools ship in the app. Bibi itself stays offline (PRD 0.4). Context7, 21st, and Strix send queries or code to external services. Never send them user data, `.env` contents, or keystores.

## What is installed where

| Tool | What it gives | How it's wired in this repo | Needs on your machine |
|---|---|---|---|
| **Agent Skills** (addyosmani/agent-skills) | Lifecycle commands `/spec`, `/plan`, `/build`, `/test`, `/review`, `/ship`, plus about 25 auto-triggered engineering skills | `.claude/settings.json` → `extraKnownMarketplaces` + `enabledPlugins`. Claude Code offers to install it when you trust the folder. | Nothing extra |
| **Context7** (upstash/context7) | Current, version-specific library docs (MCP tools `resolve-library-id`, `query-docs`) | `.mcp.json` → `context7` (HTTP), pre-approved in `settings.json` | Optional `CONTEXT7_API_KEY` for higher rate limits |
| **Graphify** (Graphify-Labs/graphify) | A knowledge graph of the codebase (`graphify query/path/explain`) so the agent doesn't grep everything | `.claude/skills/graphify/`, the `## graphify` section in `CLAUDE.md`, and `PreToolUse` hooks in `settings.json` | `uv tool install graphifyy` (Python 3.10+). The hooks do nothing if it isn't installed. |
| **21st MCP** (21st.dev/mcp) | Search for UI components and inspiration | `.mcp.json` → `21st` (HTTP), pre-approved | `API_KEY_21ST` (free key at 21st.dev/mcp) |
| **Strix** (usestrix/strix) | AI security review of the source with proof-of-concept validation | `.claude/skills/` → `find-security-vulnerabilities-in-code`, `fix-security-vulnerabilities-with-strix`, `ci-security-scanning-with-strix` (pinned in `skills-lock.json`) | Docker running, the Strix CLI, `STRIX_LLM` + `LLM_API_KEY` |

## One-time setup (per machine)

Full laptop setup and how to resume from your phone or the cloud: [local-setup.md](local-setup.md).

1. **Graphify:**
   ```bash
   uv tool install graphifyy
   ```
2. **Strix CLI:** read the script before you run it.
   ```bash
   curl -sSL https://strix.ai/install -o strix-install.sh
   ```
   ```bash
   less strix-install.sh
   ```
   ```bash
   bash strix-install.sh
   ```
3. **Keys:** put the developer-tooling keys in your shell profile (for example `~/.zshrc`), not in the repo. Claude Code expands `${VAR}` in `.mcp.json` from the environment.
   ```bash
   export CONTEXT7_API_KEY=...   API_KEY_21ST=...   STRIX_LLM=...   LLM_API_KEY=...
   ```
4. **Trust the folder:** open Claude Code in `bibi-ai/` and accept the plugin and MCP prompts. Then check with `/mcp`, which should list `context7` and `21st`, and `/plugin`, which should list `agent-skills`.
5. **Build the first graph** once real code exists:
   ```bash
   graphify update .
   ```
   Or run `/graphify .` in Claude Code. The output goes to `graphify-out/`, which is git-ignored.

## When each tool is used

| Moment | Tool | Rule |
|---|---|---|
| Starting a feature or spike | Agent Skills `/spec` → `/plan` | The spec is checked against PRD Part 0.8 scope. The plan lists tasks with acceptance criteria. |
| Writing code | Agent Skills `/build` (incremental, one commit per task) and `/test` (TDD) | Permission Engine and tool-call parser are test-first (PRD 0.11) |
| Any library or API call | **Context7** | Look it up before writing code against Compose, Material 3, Room, WorkManager, Hilt/Koin, LiteRT-LM, llama.cpp, kotlinx.serialization, AlarmClock/WindowManager APIs, or Gradle/AGP. Pin the version from `libs.versions.toml`. Don't code library APIs from memory. |
| Exploring the codebase, before a multi-module change | **Graphify** | `graphify query` / `path` / `explain` before grepping. Run `graphify update .` after changes. |
| Designing a screen or component | **21st MCP** | Inspiration only. 21st returns React/Tailwind web components, so translate the layout idea into Compose with `core/designsystem` tokens. Never paste web code into the Android project. |
| Before merging a security-relevant change (Permission Engine, tool router, export/import, encryption, overlay touch handling) and at the P2 policy gate | **Strix** `find-security-vulnerabilities-in-code`, then `fix-security-vulnerabilities-with-strix` | Local CLI only. Don't use Strix Cloud uploads for this repo without a decision entry. |
| Before each merge | Agent Skills `/review` | Five-axis review. Findings are fixed or logged in `project_status.md`. |
| Release gate (P2→P3) | Agent Skills `/ship` + Strix | Combined with the PRD 0.9 Play policy checklist |

## Limits and caveats

- **21st** targets web stacks (React/Tailwind/shadcn). For a Compose app it's a mood board, not a code source. Hosted generation needs a paid AI tier on 21st [VERIFY plan].
- **Strix** is built mainly for web apps and APIs. Bibi has no server, so findings will come from white-box source review. How well it handles Kotlin and Android-specific issues (intent spoofing, exported components, overlay tapjacking) is unknown [VERIFY on first run]. Still run Android Lint security checks as the baseline.
- **Strix sends source code to the LLM provider** you configure. That's acceptable for this portfolio repo, but don't scan anything containing secrets.
- **Context7** content is community-indexed. If its docs disagree with the official Android docs, the official docs win [VERIFY on conflict].
- **Graphify** writes absolute paths into the hooks if you re-run `graphify claude install`. Restore the portable `command -v graphify` form in `.claude/settings.json` afterwards.
- **Context7 auth header:** `.mcp.json` sends `CONTEXT7_API_KEY`. The README also documents `Authorization: Bearer`. If `/mcp` shows an auth error, switch to the Bearer form [VERIFY].
