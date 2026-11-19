import {mount,flushPromises,enableAutoUnmount} from '@vue/test-utils'
import {it,expect,vi,afterEach} from 'vitest'
import GenerationTaskProgress from './GenerationTaskProgress.vue'
import {generationEvolutionApi as api} from '../services/generationEvolutionApi'
const task={id:'1',kind:'GENERATE',referenceId:'10',status:'RUNNING',phase:'AI 正在生成源码',modelCalls:1,usageComplete:false,elapsedMs:65000,createdAt:''}
enableAutoUnmount(afterEach);afterEach(()=>vi.restoreAllMocks())
it('shows unknown usage honestly and cancels only after explicit click',async()=>{
 vi.spyOn(api,'task').mockResolvedValue(task);const cancel=vi.spyOn(api,'cancelTask').mockResolvedValue({...task,status:'CANCELLED',phase:'已取消'})
 const w=mount(GenerationTaskProgress,{props:{sessionId:'2',kind:'GENERATE',referenceId:'10'}});await flushPromises();expect(w.text()).toContain('1 分 5 秒');expect(w.text()).toContain('Token 用量未知');expect(cancel).not.toHaveBeenCalled();await w.get('button').trigger('click');await flushPromises();expect(w.text()).toContain('已取消');expect(w.emitted('cancelled')).toHaveLength(1)
})
it('displays only provider-reported usage for completed tasks',async()=>{
 vi.spyOn(api,'task').mockResolvedValue({...task,status:'SUCCEEDED',phase:'完成',usageComplete:true,inputTokens:13,outputTokens:7})
 const w=mount(GenerationTaskProgress,{props:{sessionId:'2',kind:'GENERATE',referenceId:'10'}});await flushPromises();expect(w.text()).toContain('输入 13 / 输出 7 Token');expect(w.find('button').exists()).toBe(false)
})
