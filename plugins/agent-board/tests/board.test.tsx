import { expect, test } from 'claude-code/testing'

const PANE = {
  plugin: 'agent-board',
  component: 'Pane',
  requestId: 'agent-board',
  props: {
    title: 'Agents',
    isFocused: false,
    bodyColumns: 60,
    placement: 'dock',
    scroll: { offset: 0, bodyRows: 20 },
    view: {},
  },
} as const

const AGENT = {
  id: 'a1',
  type: 'spike-analyst',
  description: 'Analyze S1 vivo log',
  status: 'running',
} as const

test('an agent shows as working, then as finished with its summary', async ($, on) => {
  // Nothing sits beneath the plugin: these stand for the engine.
  on('classic.SubagentStart', () => ({}))
  on('classic.SubagentStop', () => ({}))
  on('clock.now', () => ({ value: 1_761_750_000_000 }))
  on('agent.list', () => ({ value: [AGENT] }))
  on('ui.notify', () => ({ value: { isSent: false, reason: 'no channel' } }))

  await $.classic.SubagentStart({ agent_id: 'a1', agent_type: 'spike-analyst' })

  for (const surface of ['terminal', 'desktop'] as const) {
    const ui = await $.ui.mount({ ...PANE, surface })
    expect((await ui.find({ type: 'Text', text: /Working now/ }))?.text).toBe('Working now (1)')
    expect((await ui.find({ type: 'Text', text: /Analyze S1/ }))?.text).toContain('Analyze S1 vivo log')
    await ui.unmount()
  }

  await $.classic.SubagentStop({
    agent_id: 'a1',
    agent_type: 'spike-analyst',
    agent_transcript_path: '/tmp/a1.jsonl',
    stop_hook_active: false,
    last_assistant_message: 'S1 stability likely pass; baseline run needed.',
  })

  for (const surface of ['terminal', 'desktop'] as const) {
    const ui = await $.ui.mount({ ...PANE, surface })
    expect((await ui.find({ type: 'Text', text: /Working now/ }))?.text).toBe('Working now (0)')
    expect((await ui.find({ type: 'Text', text: /Finished/ }))?.text).toBe('Finished (1)')
    expect((await ui.find({ type: 'Text', text: /baseline run needed/ }))?.text).toContain('S1 stability likely pass')
    await ui.unmount()
  }
})

test('a stop for an agent the board never saw start still lands in Finished', async ($, on) => {
  // Nothing sits beneath the plugin: these stand for the engine.
  on('classic.SubagentStart', () => ({}))
  on('classic.SubagentStop', () => ({}))
  on('clock.now', () => ({ value: 1_761_750_000_000 }))
  on('agent.list', () => ({ value: [] }))
  on('ui.notify', () => ({ value: { isSent: true } }))

  await $.classic.SubagentStop({
    agent_id: 'b2',
    agent_type: 'docs-keeper',
    agent_transcript_path: '/tmp/b2.jsonl',
    stop_hook_active: false,
  })

  const ui = await $.ui.mount({ ...PANE, surface: 'desktop' })
  expect((await ui.find({ type: 'Text', text: /docs-keeper/ }))?.text).toContain('✅ docs-keeper')
  await ui.unmount()
})
