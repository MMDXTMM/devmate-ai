import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectFormModal from './ProjectFormModal.vue'

describe('ProjectFormModal', () => {
  it('contains keyboard focus, closes with Escape and returns focus to the opener', async () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    const wrapper = mount(ProjectFormModal, { attachTo: document.body, props: { open: true, saving: false } })
    await flushPromises()
    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.element.contains(document.activeElement)).toBe(true)
    const buttons = dialog.findAll('button')
    buttons.at(-1)!.element.focus()
    await dialog.trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(buttons[0]!.element)
    await dialog.trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('close')).toHaveLength(1)
    await wrapper.setProps({ open: false })
    await flushPromises()
    expect(document.activeElement).toBe(opener)
    wrapper.unmount()
    opener.remove()
  })
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
      defaultBranch: '',
    }))
  })
})
