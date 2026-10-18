import { mount, flushPromises } from '@vue/test-utils'
import { shallowRef } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { it, expect, vi, afterEach } from 'vitest'
import App from './App.vue'
import { createHostRouter } from './router'
import { precheckHostKey } from './modules/precheck'
import { context } from './modules/precheck/fixtures'
const wrappers = []
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))
it('宿主只登录一次，通过路由传入工单；身份切换不改变工单路由', async () => {
  const host = { identity: shallowRef(null), api: { context: vi.fn(async id => context(id)), history: vi.fn(async () => []) } }
  host.login = vi.fn(async user => { host.identity.value = { actorId: user, roles: ['ROLE_' + user.toUpperCase()] } })
  host.logout = vi.fn(async () => { host.identity.value = null })
  const router = createHostRouter(createMemoryHistory()); await router.push('/host/tickets/DEMO-002/precheck'); await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router], provide: { [precheckHostKey]: host } } }); wrappers.push(wrapper)
  expect(host.api.context).not.toHaveBeenCalled()
  await wrapper.get('#host-password').setValue('password')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(host.login).toHaveBeenCalledTimes(1)
  expect(host.api.context).toHaveBeenLastCalledWith('DEMO-002', expect.any(AbortSignal))
  expect(wrapper.text()).toContain('合成投诉 DEMO-002')
  await router.push('/host/tickets/DEMO-003/precheck'); await flushPromises()
  expect(wrapper.text()).toContain('合成投诉 DEMO-003'); expect(wrapper.text()).not.toContain('BILL-002')
  await wrapper.get('.host-identity button').trigger('click'); await flushPromises()
  expect(router.currentRoute.value.params.ticketId).toBe('DEMO-003')
  expect(wrapper.find('.precheck-module').exists()).toBe(false)
  expect(wrapper.get('#host-password').element.value).toBe('')
  await wrapper.get('#host-account').setValue('reviewer'); await wrapper.get('#host-password').setValue('password'); await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(wrapper.text()).toContain('业务审核员'); expect(wrapper.text()).toContain('合成投诉 DEMO-003')
})
it('未知路由不静默切换到别的工单', async () => {
  const router = createHostRouter(createMemoryHistory()); await router.push('/invalid'); await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router], provide: { [precheckHostKey]: { identity: shallowRef({ actorId: 'auditor', roles: ['ROLE_AUDITOR'] }) } } } }); wrappers.push(wrapper)
  expect(wrapper.text()).toContain('页面不存在')
})
