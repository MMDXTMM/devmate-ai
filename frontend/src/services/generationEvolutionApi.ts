import { apiRequest, ApiError } from './apiClient'
import { getAuthSession, clearAuthSession } from './authSession'
import type { ProjectRevision, Verification, ChangePlan, RevisionComparison, Insight, LearningGuide, RuntimeGuide, ErrorHelp, GenerationTask } from '../types/generationEvolution'
const root = (sid:string) => `/api/generation-sessions/${encodeURIComponent(sid)}`
const revision = (sid:string, id:string) => `${root(sid)}/revisions/${encodeURIComponent(id)}`
export const generationEvolutionApi = {
 task: (sid:string,kind:string,referenceId:string) => apiRequest<GenerationTask|null>(`${root(sid)}/tasks/latest?${new URLSearchParams({kind,referenceId})}`),
 cancelTask: (sid:string,id:string) => apiRequest<GenerationTask>(`${root(sid)}/tasks/${encodeURIComponent(id)}/cancellations`,{method:'POST'}),
 runtime: (sid:string,id:string) => apiRequest<RuntimeGuide>(`${revision(sid,id)}/runtime-guide`),
 errorHelp: (sid:string,id:string,error:string,attemptKey:string) => apiRequest<Insight<ErrorHelp>>(`${revision(sid,id)}/error-help`,{method:'POST',body:JSON.stringify({error,attemptKey})}),
 latestErrorHelp: (sid:string,id:string) => apiRequest<Insight<ErrorHelp>|null>(`${revision(sid,id)}/error-help/latest`),
 guide: (sid:string,id:string,attemptKey:string) => apiRequest<Insight<LearningGuide>>(`${revision(sid,id)}/learning-guides`,{method:'POST',body:JSON.stringify({attemptKey})}),
 latestGuide: (sid:string,id:string) => apiRequest<Insight<LearningGuide>|null>(`${revision(sid,id)}/learning-guides/latest`),
 revisions: (sid:string,run:string) => apiRequest<ProjectRevision[]>(`${root(sid)}/runs/${encodeURIComponent(run)}/revisions`),
 selected: (sid:string,run:string) => apiRequest<ProjectRevision>(`${root(sid)}/runs/${encodeURIComponent(run)}/selection`),
 select: (sid:string,run:string,id:string) => apiRequest<ProjectRevision>(`${root(sid)}/runs/${encodeURIComponent(run)}/selection/${encodeURIComponent(id)}`,{method:'POST'}),
 plan: (sid:string,id:string,request:string,attemptKey:string) => apiRequest<ChangePlan>(`${revision(sid,id)}/change-plans`,{method:'POST',body:JSON.stringify({request,attemptKey})}),
 latestPlan: (sid:string,id:string) => apiRequest<ChangePlan|null>(`${revision(sid,id)}/change-plans/latest`),
 getPlan: (sid:string,id:string) => apiRequest<ChangePlan>(`${root(sid)}/change-plans/${encodeURIComponent(id)}`),
 approve: (sid:string,id:string) => apiRequest<ChangePlan>(`${root(sid)}/change-plans/${encodeURIComponent(id)}/confirmations`,{method:'POST'}),
 compare: (sid:string,id:string,beforeId:string) => apiRequest<RevisionComparison>(`${revision(sid,id)}/comparison?${new URLSearchParams({beforeId})}`),
 diff: (sid:string,id:string,beforeId:string,path:string) => apiRequest<{path:string;before:string;after:string}>(`${revision(sid,id)}/diff?${new URLSearchParams({beforeId,path})}`),
 root: (sid:string,run:string) => apiRequest<ProjectRevision>(`${root(sid)}/runs/${encodeURIComponent(run)}/revisions`,{method:'POST'}),
 revision: (sid:string,id:string) => apiRequest<ProjectRevision>(revision(sid,id)),
 file: (sid:string,id:string,path:string) => apiRequest<{path:string;content:string}>(`${revision(sid,id)}/files?${new URLSearchParams({path})}`),
 verify: (sid:string,id:string,attemptKey:string,repairLimit:number) => apiRequest<Verification>(`${revision(sid,id)}/verifications`,{method:'POST',body:JSON.stringify({attemptKey,repairLimit})}),
 latestCheck: (sid:string,id:string) => apiRequest<Verification|null>(`${revision(sid,id)}/verifications/latest`),
 async download(sid:string,id:string) {
  const auth=getAuthSession()
  const res=await fetch(`${revision(sid,id)}/download`,{headers:auth?{Authorization:`${auth.tokenType} ${auth.accessToken}`}:{},signal:AbortSignal.timeout(30000)}).catch(()=>{throw new ApiError('下载失败，请检查网络')})
  if(!res.ok) { if(res.status===401) clearAuthSession(); throw new ApiError('下载失败，请稍后重试') }
  return res.blob()
 },
}
