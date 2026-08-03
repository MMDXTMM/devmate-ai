import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectFormModal from './ProjectFormModal.vue'

describe('ProjectFormModal', () => {
  it('rejects a Git project without repository url', async () => {
    const wrapper = mount(ProjectFormModal, {
      props: { open: true, project: null, saving: false },
    })

    await wrapper.get('input[placeholder="例如：devmate-ai"]').setValue('demo')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toContain('Git 项目必须填写仓库地址')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('emits a normalized project form', async () => {
    const wrapper = mount(ProjectFormModal, {
      props: { open: true, project: null, saving: false },
    })

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('  demo  ')
    await inputs[1].setValue('  https://github.com/example/demo.git  ')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual(expect.objectContaining({
      name: 'demo',
      sourceType: 'GIT',
      sourceLocation: 'https://github.com/example/demo.git',
      defaultBranch: 'main',
    }))
  })
})
