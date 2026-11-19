<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount,computed} from 'vue'
import {generationEvolutionApi as api} from '../services/generationEvolutionApi'
import {ApiError} from '../services/apiClient'
import type {GenerationTask} from '../types/generationEvolution'
const props=defineProps<{sessionId:string;kind:string;referenceId:string}>(),emit=defineEmits<{cancelled:[]}>()
const task=ref<GenerationTask|null>(null),busy=ref(false),error=ref('')
let alive=true,timer:ReturnType<typeof setTimeout>|undefined
const active=computed(()=>task.value&&['RUNNING','QUEUED'].includes(task.value.status))
const duration=computed(()=>{const seconds=Math.floor((task.value?.elapsedMs??0)/1000);return seconds<60?`${seconds} 秒`:`${Math.floor(seconds/60)} 分 ${seconds%60} 秒`})
onMounted(()=>void refresh());onBeforeUnmount(()=>{alive=false;clearTimeout(timer)})
async function refresh(attempt=0){clearTimeout(timer);try{const value=await api.task(props.sessionId,props.kind,props.referenceId);if(!alive)return;task.value=value;error.value='';if(value&&['RUNNING','QUEUED'].includes(value.status)&&attempt<450)timer=setTimeout(()=>void refresh(attempt+1),2000)}catch(e){if(alive)error.value=e instanceof ApiError?e.message:'进度暂时无法读取'}}
async function cancel(){if(busy.value||!task.value)return;busy.value=true;error.value='';try{const saved=await api.cancelTask(props.sessionId,task.value.id);if(!alive)return;task.value=saved;clearTimeout(timer);emit('cancelled')}catch(e){if(alive)error.value=e instanceof ApiError?e.message:'取消失败，请刷新确认'}finally{if(alive)busy.value=false}}
</script>
<template><aside v-if="task||error" class="task-progress" aria-label="任务进度与用量"><template v-if="task"><b>{{task.phase}}</b><p>{{active?'已等待':'总用时'}} {{duration}} · 模型调用 {{task.modelCalls}} 次</p><p v-if="task.usageComplete">输入 {{task.inputTokens??0}} / 输出 {{task.outputTokens??0}} Token</p><p v-else>Token 用量未知：接口尚未返回完整用量。</p><p>费用按你配置的服务计费，本页不估算金额。{{active?'取消后会停止后续处理；已发出的模型请求仍可能计费。':''}}</p><button v-if="active" class="button secondary" :disabled="busy" @click="cancel">{{busy?'正在取消…':'取消此任务'}}</button></template><p v-if="error" role="alert">{{error}}</p><button v-if="error" class="button secondary" @click="refresh()">重新读取进度</button></aside></template>
<style scoped>.task-progress{margin:14px 0;padding:14px 18px;border:1px solid var(--line);border-radius:12px;background:var(--surface-soft)}b{font-size:14px}p{margin:7px 0;font-size:12px;line-height:1.7;color:var(--ink-soft)}</style>
