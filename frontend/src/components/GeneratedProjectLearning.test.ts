import {mount,flushPromises,enableAutoUnmount} from '@vue/test-utils'
import {it,expect,vi,afterEach} from 'vitest'
import GeneratedProjectLearning from './GeneratedProjectLearning.vue'
import {generationEvolutionApi as api} from '../services/generationEvolutionApi'
enableAutoUnmount(afterEach);afterEach(()=>vi.restoreAllMocks())
it('reads saved guide without generating and opens exact source references',async()=>{
 const content={purpose:'个人待办',modules:[{name:'入口',responsibility:'处理命令',paths:['main.py']}],flow:[],readingOrder:[],limits:['未执行']}
 vi.spyOn(api,'latestGuide').mockResolvedValue({id:'1',revisionId:'10',kind:'GUIDE',status:'SUCCEEDED',content,createdAt:'',updatedAt:''});const generate=vi.spyOn(api,'guide')
 const w=mount(GeneratedProjectLearning,{props:{sessionId:'1',revisionId:'10'}});await flushPromises();expect(generate).not.toHaveBeenCalled();expect(w.text()).toContain('个人待办');await w.get('.source-link').trigger('click');expect(w.emitted('file')?.[0]).toEqual(['main.py'])
})
it('keeps unavailable analysis visible without manufacturing a report',async()=>{
 vi.spyOn(api,'latestGuide').mockResolvedValue({id:'1',revisionId:'10',kind:'GUIDE',status:'FAILED',errorMessage:'引用不合法',createdAt:'',updatedAt:''})
 const w=mount(GeneratedProjectLearning,{props:{sessionId:'1',revisionId:'10'}});await flushPromises();expect(w.text()).toContain('引用不合法');expect(w.find('.source-link').exists()).toBe(false)
})
