import {mount,flushPromises,enableAutoUnmount} from '@vue/test-utils'
import {afterEach,beforeEach,it,expect,vi} from 'vitest'
import GeneratedProjectVersions from './GeneratedProjectVersions.vue'
import {generationEvolutionApi as api} from '../services/generationEvolutionApi'
const original={id:'10',runId:'20',kind:'ORIGINAL',status:'SUCCEEDED',summary:'待办',files:[],warnings:[],createdAt:'',updatedAt:''}
const changed={...original,id:'11',parentId:'10',kind:'CHANGE',summary:'增加搜索'}
enableAutoUnmount(afterEach)
beforeEach(()=>{vi.spyOn(api,'latestPlan').mockResolvedValue(null);vi.spyOn(api,'revisions').mockResolvedValue([changed,original]);vi.spyOn(api,'selected').mockResolvedValue(original)})
afterEach(()=>vi.restoreAllMocks())
it('requires an explicit plan confirmation before generating a new version',async()=>{
 const plan={id:'30',revisionId:'10',request:'增加搜索',status:'PLANNED',plan:{summary:'增加搜索',changes:['修改入口'],risks:['待验证']}}
 vi.spyOn(api,'plan').mockResolvedValue(plan);const approve=vi.spyOn(api,'approve').mockResolvedValue({...plan,status:'SUCCEEDED',resultRevisionId:'11'})
 const w=mount(GeneratedProjectVersions,{props:{sessionId:'1',runId:'20',revisionId:'10'}});await flushPromises();await w.get('textarea').setValue('增加搜索');await w.get('form').trigger('submit');await flushPromises()
 expect(approve).not.toHaveBeenCalled();expect(w.text()).toContain('修改入口');await w.get('.change-plan .primary').trigger('click');await flushPromises();expect(approve).toHaveBeenCalledTimes(1)
})
it('restores an old version only after server confirmation and shows diffs as text',async()=>{
 vi.spyOn(api,'select').mockResolvedValue(changed);vi.spyOn(api,'compare').mockResolvedValue({beforeId:'10',afterId:'11',files:[{path:'main.py',change:'MODIFIED'}]});vi.spyOn(api,'diff').mockResolvedValue({path:'main.py',before:'old',after:'<script>unsafe()</script>'})
 vi.mocked(api.selected).mockResolvedValue(changed)
 const w=mount(GeneratedProjectVersions,{props:{sessionId:'1',runId:'20',revisionId:'11'}});await flushPromises();await w.findAll('button').find(b=>b.text().includes('查看与'))!.trigger('click');await flushPromises();await w.get('.diff-file').trigger('click');await flushPromises();expect(w.find('script').exists()).toBe(false);expect(w.text()).toContain('<script>unsafe()</script>')
 await w.get('select').setValue('10');await flushPromises();expect(api.select).toHaveBeenCalledWith('1','20','10')
})
