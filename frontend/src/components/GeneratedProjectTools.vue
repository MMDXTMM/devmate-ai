<script setup lang="ts">
import GenerationTaskProgress from './GenerationTaskProgress.vue'
import GeneratedProjectRuntime from './GeneratedProjectRuntime.vue'
import GeneratedProjectLearning from './GeneratedProjectLearning.vue'
import GeneratedProjectVersions from './GeneratedProjectVersions.vue'
import { onBeforeUnmount, ref, computed } from 'vue'
import { generationEvolutionApi as api } from '../services/generationEvolutionApi'
import { useModelAccess } from '../composables/useModelAccess'
import { ApiError } from '../services/apiClient'
import type { ProjectRevision, Verification } from '../types/generationEvolution'
const props=defineProps<{sessionId:string;runId:string}>()
const ensureModel=useModelAccess()
const opened=ref(false), busy=ref(false), error=ref(''), revision=ref<ProjectRevision|null>(null), check=ref<Verification|null>(null)
const repairLimit=ref(0), preview=ref(''), path=ref(''), fileBusy=ref(false)
let alive=true, timer:ReturnType<typeof setTimeout>|undefined, ticket=0, attemptKey=''
onBeforeUnmount(()=>{alive=false;ticket++;clearTimeout(timer)})
const checkLabel=computed(()=>(({RUNNING:'正在隔离验证',PASSED:check.value?.result?.testsExecuted?'测试已通过':'编译已通过（未执行测试）',FAILED:'验证未通过',BLOCKED:'暂时无法验证',CANCELLED:'验证已取消'} as Record<string,string>)[check.value?.status??'']??'尚未验证'))
const message=(e:unknown)=>e instanceof ApiError?e.message:'操作失败，请稍后重试'
async function open() {
 if(busy.value)return;busy.value=true;error.value=''
 try { const r=await api.root(props.sessionId,props.runId);if(!alive)return;revision.value=r;opened.value=true;await refreshCheck() }
 catch(e){if(alive)error.value=message(e)}finally{if(alive)busy.value=false}
}
async function refreshCheck(attempt=0) {
 clearTimeout(timer);if(!revision.value)return
 const id=revision.value.id
 try {const c=await api.latestCheck(props.sessionId,id);if(!alive||revision.value?.id!==id)return;check.value=c
 if(c?.status==='RUNNING'&&attempt<450)timer=setTimeout(()=>void refreshCheck(attempt+1),2000)
 }catch(e){if(alive)error.value=message(e)}
}
async function verify() {
 if(busy.value||!revision.value||check.value?.status==='RUNNING')return;busy.value=true;error.value=''
 try {
 if(repairLimit.value>0&&!await ensureModel('失败后有限AI修复'))return
 if(!alive)return
 if(!attemptKey)attemptKey=crypto.randomUUID()
 const c=await api.verify(props.sessionId,revision.value.id,attemptKey,repairLimit.value)
 if(!alive)return;check.value=c;attemptKey='';await refreshCheck()
 }catch(e){if(alive)error.value=message(e)}finally{if(alive)busy.value=false}
}
function switchRevision(value:ProjectRevision){ticket++;revision.value=value;check.value=null;path.value='';preview.value='';fileBusy.value=false;attemptKey='';clearTimeout(timer);void refreshCheck()}
async function viewRepair() {
 if(!check.value?.resultRevisionId||busy.value)return;busy.value=true
 try {const r=await api.select(props.sessionId,props.runId,check.value.resultRevisionId);if(!alive)return;revision.value=r;check.value=null;preview.value='';path.value='';await refreshCheck()}
 catch(e){if(alive)error.value=message(e)}finally{if(alive)busy.value=false}
}
async function readFile(value:string) {
 if(!revision.value)return;const seq=++ticket;const id=revision.value.id;path.value=value;preview.value='';fileBusy.value=true
 try {const f=await api.file(props.sessionId,id,value);if(alive&&seq===ticket)preview.value=f.content}
 catch(e){if(alive&&seq===ticket)error.value=message(e)}finally{if(alive&&seq===ticket)fileBusy.value=false}
}
async function download() {
 if(!revision.value||busy.value)return;busy.value=true
 try {const blob=await api.download(props.sessionId,revision.value.id);if(!alive)return;const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`revision-${revision.value.id}.zip`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
 catch(e){if(alive)error.value=message(e)}finally{if(alive)busy.value=false}
}
</script>
<template>
 <section class="project-tools panel">
  <header><h2>继续完善这个项目</h2><p>先检查，再根据真实结果修复。原始源码会保留。</p><button v-if="!opened" class="button secondary" :disabled="busy" @click="open">{{busy?'正在读取…':'打开验证与修复'}}</button></header>
  <p v-if="error" class="notice error" role="alert">{{error}}</p>
  <template v-if="opened&&revision">
   <section class="tool-section"><h3>隔离验证与修复</h3><p>支持无外部依赖的 Python unittest、Node 原生 JS 测试和 Java 源码编译。其他项目会说明暂时无法验证的原因。</p>
    <label>失败后如何处理 <select v-model.number="repairLimit" :disabled="busy||check?.status==='RUNNING'" aria-label="失败后修复次数"><option :value="0">仅验证，不调用 AI</option><option :value="1">最多修复 1 次 · 使用 API 额度</option><option :value="2">最多修复 2 次 · 使用 API 额度</option></select></label>
    <button class="button primary" :disabled="busy||check?.status==='RUNNING'" @click="verify">{{check?.status==='RUNNING'?'正在验证…':'开始验证'}}</button>
    <button v-if="check?.status==='RUNNING'" class="button secondary" @click="refreshCheck()">刷新结果</button>
    <GenerationTaskProgress v-if="check" :key="check.id" :session-id="sessionId" kind="VERIFY" :reference-id="check.id" @cancelled="refreshCheck()" />
    <div v-if="check" class="verification-result" role="status"><h4>{{checkLabel}}</h4><p>已修复 {{check.repairs}} / {{check.repairLimit}} 次</p><p v-if="check.errorMessage">{{check.errorMessage}}</p><template v-if="check.result"><p>{{check.result.profile}} · {{(check.result.durationMs/1000).toFixed(1)}} 秒 · 退出码 {{check.result.exitCode??'无'}}</p><code>{{check.result.command}}</code><pre>{{check.result.output}}</pre></template>
     <button v-if="check.resultRevisionId&&check.resultRevisionId!==revision.id" class="button secondary" @click="viewRepair">查看修复后的独立版本</button>
    </div>
   </section>
   <GeneratedProjectRuntime :key="revision.id" :session-id="sessionId" :revision-id="revision.id" />
   <GeneratedProjectLearning :key="revision.id" :session-id="sessionId" :revision-id="revision.id" @file="readFile" />
   <GeneratedProjectVersions :key="revision.id" :session-id="sessionId" :run-id="runId" :revision-id="revision.id" @select="switchRevision" />
   <section v-if="revision.kind!=='ORIGINAL'||path" class="tool-section"><h3>{{revision.kind==='REPAIR'?'修复版本':'项目版本'}} · {{revision.id}}</h3><p>{{revision.summary}}</p><button class="button secondary" :disabled="busy" @click="download">下载此版本 ZIP</button><div class="revision-files"><button v-for="file in revision.files" :key="file.path" @click="readFile(file.path)">{{file.path}}</button></div><h4>{{path}}</h4><p v-if="fileBusy">正在读取…</p><pre v-else>{{preview}}</pre></section>
  </template>
 </section>
</template>
<style scoped>
.project-tools {margin-top:24px;padding:24px;min-width:0}.project-tools header h2{font-size:20px;margin-top:0}.project-tools p{font-size:13px;line-height:1.8;color:var(--ink-soft)}.tool-section{border-top:1px solid var(--line);margin-top:22px;padding-top:18px}.tool-section h3{font-size:17px}.tool-section label{display:flex;flex-wrap:wrap;gap:12px;align-items:center;margin-bottom:14px;font-size:13px}.tool-section select{max-width:100%;padding:10px;border:1px solid var(--line);border-radius:10px;background:var(--surface)}.tool-section .button{margin:4px 8px 4px 0}.verification-result{padding:18px;margin-top:16px;background:var(--surface-soft);border-radius:14px}pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:380px;overflow:auto;background:var(--code-bg);color:var(--code-ink);padding:16px;border-radius:10px;font-size:12px;line-height:1.7}code{font-size:12px;overflow-wrap:anywhere}.revision-files{display:flex;flex-wrap:wrap;gap:8px;margin:14px 0}.revision-files button{padding:8px;border:1px solid var(--line);background:var(--surface);border-radius:8px;cursor:pointer}
</style>
