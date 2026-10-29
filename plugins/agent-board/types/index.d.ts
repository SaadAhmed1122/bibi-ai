export type AgentRow = {
  id: string
  type: string
  description: string
  status: 'running' | 'done'
  startedAt: number
  endedAt?: number
  summary?: string
}

declare module 'claude-code' {
  interface PluginState {
    'agent-board': { agents: Shaped<AgentRow[]> }
  }
}
