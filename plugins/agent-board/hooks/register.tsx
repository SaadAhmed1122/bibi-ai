import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'

import type { AgentRow } from '../types'

const PANE = 'agent-board'
// Change the shape tag when AgentRow's fields change: stored rows outlive a reload.
const agents = atom({ plugin: 'agent-board', key: 'agents' } as const, [], {
  shape: 'agents-1',
})

const clip = (text: string, max: number) =>
  text.length > max ? `${text.slice(0, max - 1)}…` : text

const clockTime = (ms: number) => {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const duration = (ms: number) => {
  const s = Math.max(0, Math.round(ms / 1000))
  return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${s % 60}s`
}

/** Status line: how many agents are working right now (cleared when none). */
async function refreshStatus($: EngineInterface) {
  const running = (await read($, agents)).filter(a => a.status === 'running').length
  $.ui.status(running > 0 ? `🤖 ${running} agent${running === 1 ? '' : 's'} working` : undefined)
}

export const register: Register = on => {

  on('session.start', async ($, e, next) => {
    await $.command.register({
      name: 'agent-board',
      description: 'Show which subagents are working and what they are doing',
    })
    // Pick up agents that were already running when this mod (re)loaded.
    const now = await $.clock.now()
    const live = (await $.agent.list()).filter(a => a.status === 'running' || a.status === 'pending')
    if (live.length > 0) {
      await update($, agents, list => {
        const known = new Set(list.map(r => r.id))
        const added: AgentRow[] = live
          .filter(a => !known.has(a.id))
          .map(a => ({ id: a.id, type: a.type, description: a.description, status: 'running', startedAt: now }))
        return [...list, ...added].slice(-50)
      })
    }
    await refreshStatus($)
    void $.ui.open({ id: PANE, title: 'Agents' })

    return next(e)
  })

  on('command.run', { command: 'agent-board' }, async $ => {
    await $.ui.open({ id: PANE, title: 'Agents' })
    const running = (await read($, agents)).filter(a => a.status === 'running')

    return {
      text: running.length === 0
        ? 'Agents pane opened. No agent is working right now.'
        : `Agents pane opened. Working now: ${running.map(a => `${a.type} (${a.description || 'no description'})`).join('; ')}`,
    }
  })

  // Both lifecycle hooks let the event through first and only then update the
  // board, so a failure here can never block or delay an agent.
  on('classic.SubagentStart', async ($, e, next) => {
    const result = await next(e)
    try {
      const info = (await $.agent.list()).find(a => a.id === e.agent_id)
      const row: AgentRow = {
        id: e.agent_id,
        type: e.agent_type || info?.type || 'agent',
        description: info?.description ?? '',
        status: 'running',
        startedAt: await $.clock.now(),
      }
      await update($, agents, list => [...list.filter(r => r.id !== row.id), row].slice(-50))
      $.ui.toast(`▶ ${row.type} started${row.description ? `: ${clip(row.description, 60)}` : ''}`)
      await refreshStatus($)
    } catch {
      // The board is informational; ignore its own errors.
    }

    return result
  })

  on('classic.SubagentStop', async ($, e, next) => {
    const result = await next(e)
    try {
      const now = await $.clock.now()
      const summary = e.last_assistant_message ? clip(e.last_assistant_message.replace(/\s+/g, ' ').trim(), 200) : undefined
      let finished: AgentRow | undefined
      await update($, agents, list => {
        const existing = list.find(r => r.id === e.agent_id)
        // An agent that started before this mod loaded has no row yet: add one.
        const base: AgentRow = existing ?? { id: e.agent_id, type: e.agent_type || 'agent', description: '', status: 'running', startedAt: now }
        finished = { ...base, status: 'done', endedAt: now, summary }
        return [...list.filter(r => r.id !== e.agent_id), finished].slice(-50)
      })
      const label = `✓ ${finished?.type ?? e.agent_type} finished${finished?.description ? `: ${clip(finished.description, 60)}` : ''}`
      if (!(await $.ui.notify(label)).isSent) $.ui.toast(label)
      await refreshStatus($)
    } catch {
      // The board is informational; ignore its own errors.
    }

    return result
  })

  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const { Box, Button, Text } = $.ui.resolve(e)
    const list = await read($, agents)
    const now = await $.clock.now()
    const running = list.filter(a => a.status === 'running').sort((a, b) => a.startedAt - b.startedAt)
    const done = list.filter(a => a.status === 'done').sort((a, b) => (b.endedAt ?? 0) - (a.endedAt ?? 0))
    const room = Math.max(1, (e.viewport?.rows ?? 30) - 6)

    return (
      <Box flexDirection="column">
        <Text bold>Working now ({running.length})</Text>
        {running.length === 0 && <Text dimColor>No agent is working.</Text>}
        {running.map(a => (
          <Box key={a.id} flexDirection="column">
            <Text>⏳ {a.type} · since {clockTime(a.startedAt)} ({duration(now - a.startedAt)})</Text>
            {a.description !== '' && <Text dimColor>   {clip(a.description, 90)}</Text>}
          </Box>
        ))}
        <Text bold>Finished ({done.length})</Text>
        {done.length === 0 && <Text dimColor>Nothing finished yet.</Text>}
        {done.slice(0, room).map(a => (
          <Box key={a.id} flexDirection="column">
            <Text>✅ {a.type} · {clockTime(a.endedAt ?? a.startedAt)} · took {duration((a.endedAt ?? a.startedAt) - a.startedAt)}</Text>
            {a.description !== '' && <Text dimColor>   {clip(a.description, 90)}</Text>}
            {a.summary !== undefined && <Text dimColor>   → {clip(a.summary, 140)}</Text>}
          </Box>
        ))}
        {done.length > 0 && (
          <Button
            key="clear"
            label="Clear finished"
            onPress={() => update($, agents, l => l.filter(r => r.status !== 'done'))}
          />
        )}
      </Box>
    )
  })
}
