import {mount,flushPromises,enableAutoUnmount} from '@vue/test-utils'
import {afterEach,beforeEach,describe,it,expect,vi} from 'vitest'
import GeneratedProjectTools from './GeneratedProjectTools.vue'
import {generationEvolutionApi as api} from '../services/generationEvolutionApi'
const root={id:'10',runId:'20',kind:'ORIGINAL',status:'SUCCEEDED',files:[],warnings:[],createdAt:'2026-11-14',updatedAt:'2026-11-14'}
enableAutoUnmount(afterEach)
beforeEach(()=>{vi.spyOn(api,'root').mockResolvedValue(root);vi.spyOn(api,'latestCheck').mockResolvedValue(null)})
afterEach(()=>vi.restoreAllMocks())
describe('generated project verification',()=>{
 it('does not execute on opening, then shows blocked evidence without pretending success',async()=>{
  const verify=vi.spyOn(api,'verify').mockResolvedValue({id:'30',revisionId:'10',status:'BLOCKED',repairs:0,repairLimit:0,createdAt:'',updatedAt:''})
  const wrapper=mount(GeneratedProjectTools,{props:{sessionId:'1',runId:'20'},global:{stubs:{GeneratedProjectVersions:true,GeneratedProjectLearning:true,GeneratedProjectRuntime:true}}})
  expect(api.root).not.toHaveBeenCalled();await wrapper.get('header button').trigger('click');await flushPromises();expect(verify).not.toHaveBeenCalled()
  vi.mocked(api.latestCheck).mockResolvedValue({id:'30',revisionId:'10',status:'BLOCKED',repairs:0,repairLimit:0,result:{status:'BLOCKED',profile:'UNSUPPORTED',command:'',output:'需要环境',durationMs:0,testsExecuted:false},createdAt:'',updatedAt:''})
  await wrapper.get('.tool-section .primary').trigger('click');await flushPromises()
  expect(verify).toHaveBeenCalledWith('1','10',expect.any(String),0);expect(wrapper.text()).toContain('暂时无法验证');expect(wrapper.text()).toContain('需要环境')
 })
 it('keeps errors readable and avoids double initialization',async()=>{
  let resolve!:(v:typeof root)=>void;vi.mocked(api.root).mockImplementation(()=>new Promise(r=>{resolve=r}))
  const w=mount(GeneratedProjectTools,{props:{sessionId:'1',runId:'20'},global:{stubs:{GeneratedProjectVersions:true,GeneratedProjectLearning:true,GeneratedProjectRuntime:true}}});await w.get('header button').trigger('click');await w.get('header button').trigger('click');expect(api.root).toHaveBeenCalledTimes(1);resolve(root);await flushPromises();expect(w.find('select').exists()).toBe(true)
 })
})
