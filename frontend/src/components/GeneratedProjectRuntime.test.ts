import {mount,flushPromises,enableAutoUnmount} from '@vue/test-utils'
import {it,expect,vi,afterEach} from 'vitest'
import GeneratedProjectRuntime from './GeneratedProjectRuntime.vue'
import {generationEvolutionApi as api} from '../services/generationEvolutionApi'
enableAutoUnmount(afterEach);afterEach(()=>vi.restoreAllMocks())
it('shows runtime steps without calling AI and allows a local checklist',async()=>{
 vi.spyOn(api,'runtime').mockResolvedValue({language:'Python',evidence:['main.py'],steps:[{title:'准备Python',instruction:'确认版本',commands:['python3 --version']}],notes:['未检测用户电脑']});vi.spyOn(api,'latestErrorHelp').mockResolvedValue(null);const explain=vi.spyOn(api,'errorHelp')
 const w=mount(GeneratedProjectRuntime,{props:{sessionId:'1',revisionId:'10'}});await flushPromises();expect(w.text()).toContain('python3 --version');expect(explain).not.toHaveBeenCalled();await w.get('input[type=checkbox]').setValue(true);expect(w.get('input').element).toHaveProperty('checked',true)
})
it('explains only after explicit submission and clears submitted diagnostic',async()=>{
 vi.spyOn(api,'runtime').mockResolvedValue({language:'Python',evidence:[],steps:[],notes:[]});vi.spyOn(api,'latestErrorHelp').mockResolvedValue(null);const explain=vi.spyOn(api,'errorHelp').mockResolvedValue({id:'1',revisionId:'10',kind:'ERROR_HELP',status:'RUNNING',createdAt:'',updatedAt:''})
 const w=mount(GeneratedProjectRuntime,{props:{sessionId:'1',revisionId:'10'}});await flushPromises();await w.get('textarea').setValue('ModuleNotFoundError');await w.get('form').trigger('submit');await flushPromises();expect(explain).toHaveBeenCalledWith('1','10','ModuleNotFoundError',expect.any(String));expect(w.get('textarea').element).toHaveProperty('value','')
})
