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
  const host = { identity: shallowRef(null), api: { context: vi.fn(async id => context(id)), history: vi.fn(async () => []), current: vi.fn(async () => null), transition: vi.fn() } }
  host.login = vi.fn(async user => { host.identity.value = { actorId: user, roles: ['ROLE_' + user.toUpperCase()] } })
  host.logout = vi.fn(async () => { host.identity.value = null })
  const router = createHostRouter(createMemoryHistory()); await router.push('/host/tickets/DEMO-002/precheck'); await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router], provide: { [precheckHostKey]: host } } }); wrappers.push(wrapper)
  expect(wrapper.text()).toContain('客户服务运营平台')
  expect(wrapper.text()).toContain('演示身份登录')
  expect(wrapper.text()).toContain('客服受理人员')
  expect(wrapper.text()).toContain('投诉处理专员')
  expect(wrapper.text()).toContain('质检审核人员（只读）')
  expect(wrapper.text()).toContain('不构成串行审批')
  expect(host.api.context).not.toHaveBeenCalled()
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(host.login).toHaveBeenCalledTimes(1)
  expect(host.api.context).toHaveBeenLastCalledWith('DEMO-002', expect.any(AbortSignal))
  expect(wrapper.find('.business-nav').exists()).toBe(true)
  expect(wrapper.find('.sidebar-filter').exists()).toBe(true)
  expect(wrapper.text()).toContain('工单核查处理')
  expect(wrapper.text()).toContain('合成投诉 DEMO-002')
  await router.push('/host/tickets/DEMO-003/precheck'); await flushPromises()
  expect(wrapper.text()).toContain('合成投诉 DEMO-003'); expect(wrapper.text()).not.toContain('BILL-002')
  await wrapper.findAll('.host-identity button').find(button => button.text() === '切换身份').trigger('click'); await flushPromises()
  expect(router.currentRoute.value.params.ticketId).toBe('DEMO-003')
  expect(wrapper.find('.precheck-module').exists()).toBe(false)
  expect(wrapper.find('#host-password').exists()).toBe(false)
  await wrapper.get('#host-account').setValue('reviewer'); await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(wrapper.text()).toContain('投诉处理专员'); expect(wrapper.text()).toContain('合成投诉 DEMO-003')
})
it('未知路由不静默切换到别的工单', async () => {
  const router = createHostRouter(createMemoryHistory()); await router.push('/invalid'); await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router], provide: { [precheckHostKey]: { identity: shallowRef({ actorId: 'auditor', roles: ['ROLE_AUDITOR'] }) } } } }); wrappers.push(wrapper)
  expect(wrapper.text()).toContain('页面不存在')
})
it('模拟宿主流转只提交业务事件并刷新工作台，不调用人工预审接口', async () => {
  const host={ identity:shallowRef({actorId:'operator',roles:['ROLE_OPERATOR']}), api:{
    context:vi.fn(async id=>context(id)),history:vi.fn(async()=>[]),current:vi.fn(async()=>null),
    transition:vi.fn(async(ticket,body)=>({ticketId:ticket,ticketVersion:body.ticketVersion,transitioned:true,precheckAccepted:true}))
  },logout:vi.fn() }
  const router=createHostRouter(createMemoryHistory()); await router.push('/host/tickets/DEMO-001/precheck'); await router.isReady()
  const wrapper=mount(App,{global:{plugins:[router],provide:{[precheckHostKey]:host}}}); wrappers.push(wrapper); await flushPromises()
  await wrapper.get('.host-transition').trigger('click'); await flushPromises()
  expect(host.api.transition).toHaveBeenCalledTimes(1)
  expect(host.api.transition.mock.calls[0][0]).toBe('DEMO-001')
  expect(wrapper.text()).toContain('AI 核查任务已在后台接收')
  expect(host.api.context).toHaveBeenCalledTimes(2)
})
