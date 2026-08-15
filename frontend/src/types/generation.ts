export type GenerationSessionStatus = 'CLARIFYING' | 'CONFIRMED'
export type GenerationSpecStatus = 'DRAFT' | 'CONFIRMED'
export type RequirementQuestionCategory = 'BUSINESS' | 'TECHNICAL' | 'TRADEOFF'
export type RequirementInputType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'FREE_TEXT'
export type RequirementDecisionMode =
  | 'USER_SELECTED'
  | 'USER_ACCEPTED_RECOMMENDATION'
  | 'AI_DEFAULTED'
  | 'CUSTOM'
  | 'LEGACY_TEXT'

export interface RequirementOption {
  id: string
  label: string
  description: string
  impact: string
  recommended: boolean
}

export interface RequirementQuestion {
  id: string
  category?: RequirementQuestionCategory
  inputType?: RequirementInputType
  question: string
  reason: string
  aiRecommendation?: string
  recommendationReason?: string
  options?: RequirementOption[]
  required: boolean
  allowCustomAnswer?: boolean
  legacy?: boolean
}

export interface RequirementAnswer {
  questionId: string
  decisionMode?: RequirementDecisionMode
  selectedOptionIds?: string[]
  customAnswer?: string
  answer: string
}

export interface GenerationSpec {
  id: string
  versionNo: number
  requirementSummary: string
  architectureSummary: string
  assumptions: string[]
  questions: RequirementQuestion[]
  answers: RequirementAnswer[]
  status: GenerationSpecStatus
  promptVersion: string
  createdAt: string
}

export interface GenerationSession {
  id: string
  originalRequirement: string
  status: GenerationSessionStatus
  latestVersionNo: number
  confirmedVersionId?: string
  latestSpec: GenerationSpec
  createdAt: string
  updatedAt: string
}

export interface ClarificationAnswerForm {
  questionId: string
  decisionMode?: RequirementDecisionMode
  selectedOptionIds?: string[]
  customAnswer?: string
  answer?: string
}
