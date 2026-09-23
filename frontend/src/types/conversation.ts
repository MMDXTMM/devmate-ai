export interface AgentConversation {
  id: string
  projectId: string
  title: string
  status: 'ACTIVE' | 'CLOSED'
  revision: string
  provider: string
  modelName: string
  promptVersion: string
  createdAt: string
  updatedAt: string
}

export interface ConversationEvidence {
  chunkId: string
  filePath: string
  symbolName?: string
  startLine?: number
  endLine?: number
  excerpt: string
}

export interface ConversationMessage {
  id: string
  conversationId: string
  sequenceNo: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL'
  content: string
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  modelName?: string
  evidence: ConversationEvidence[]
  errorMessage?: string
  latencyMs?: number
  createdAt: string
}

export interface ConversationStreamHandlers {
  onMessage?: (value: { conversationId: string; messageId: string; status: string }) => void
  onEvidence?: (value: ConversationEvidence[]) => void
  onToken?: (value: { content: string }) => void
  onDone?: (value: ConversationMessage) => void
  onFailed?: (value: { messageId: string; message: string }) => void
}

