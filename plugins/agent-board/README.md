# agent-board

A Claude Code mod that shows which subagents are working, what each was asked to do, and when it finishes. Made for the Bibi AI project, where Claude delegates to project subagents (spike-analyst, android-engineer, scope-guard, docs-keeper) and plugin agents.

- **Agents pane**: working now (type, task, start time, running time) and finished (when, how long, a one-line summary of the agent's last message). "Clear finished" empties the finished list.
- **Toasts** when an agent starts or finishes; a finish also goes to your notification channel when one is available.
- **Status line**: "🤖 N agents working" while any agent runs.
- **/agent-board** opens the pane and lists who is working.

## Install
In this repo it's enabled automatically by `.claude/settings.json` (accept the plugin prompt when Claude Code first opens the folder). Anywhere else, at a Claude Code prompt:

```
/plugin install agent-board --marketplace SaadAhmed1122/bibi-ai
```

The repo is private, so that needs GitHub access to it on the machine.
