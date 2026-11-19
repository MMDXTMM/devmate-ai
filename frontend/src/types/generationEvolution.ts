export interface ProjectRevision {
 id: string; runId: string; parentId?: string; kind: string; status: string; request?: string;
 summary?: string; runInstructions?: string; files: {path:string; characters:number}[]; warnings: string[];
 errorMessage?: string; createdAt:string; updatedAt:string;
}
export interface Verification {
 id:string; revisionId:string; status:'RUNNING'|'PASSED'|'FAILED'|'BLOCKED'|'CANCELLED'; repairs:number; repairLimit:number;
 resultRevisionId?:string; errorMessage?:string; createdAt:string; updatedAt:string;
 result?: {status:string; profile:string; command:string; exitCode?:number; output:string; durationMs:number; testsExecuted:boolean};
}
export interface ChangePlan {id:string; revisionId:string; request:string; status:string; plan?:{summary:string;changes:string[];risks:string[]}; resultRevisionId?:string;errorMessage?:string}
export interface RevisionComparison {beforeId:string;afterId:string;files:{path:string;change:string}[]}
export interface LearningGuide {purpose:string; modules:{name:string;responsibility:string;paths:string[]}[];flow:{title:string;explanation:string;paths:string[]}[];readingOrder:{title:string;explanation:string;paths:string[]}[];limits:string[]}
export interface Insight<T> {id:string;revisionId:string;kind:string;status:string;content?:T;errorMessage?:string;createdAt:string;updatedAt:string}
export interface RuntimeGuide {language:string;evidence:string[];steps:{title:string;instruction:string;commands:string[]}[];notes:string[]}
export interface ErrorHelp {explanation:string;possibleCauses:string[];steps:{title:string;instruction:string;command:string}[];limits:string[]}
export interface GenerationTask {id:string;kind:string;referenceId:string;status:string;phase:string;modelCalls:number;inputTokens?:number;outputTokens?:number;usageComplete:boolean;elapsedMs:number;createdAt:string;startedAt?:string;finishedAt?:string}
