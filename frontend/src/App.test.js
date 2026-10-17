import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi, afterEach } from 'vitest'
import App from './App.vue'
import { getTicketContext } from './api/context'
vi.mock('./api/context', () => ({ getTicketContext: vi.fn() }))
const fixture = {
  ticketId: 'DEMO-002', complaint: '合成投诉',
  bills: { items: [{ evidenceId: 'BILL-002', amount: '30.00', currency: 'CNY', billingPeriod: '2026-10' }] },
  subscriptions: { items: [] }, authorizations: { status: 'NOT_FOUND', items: [] },
}
afterEach(() => vi.resetAllMocks())
describe('上下文联调页', () => {
  it('展示真实返回的缺失状态且不宣称违规', async () => {
    getTicketContext.mockResolvedValue(fixture)
    const wrapper = mount(App)
    await wrapper.get('select').setValue('DEMO-002')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(getTicketContext).toHaveBeenCalledWith('DEMO-002')
    expect(wrapper.text()).toContain('未找到')
    expect(wrapper.text()).toContain('证据未找到不代表违规扣费')
    expect(wrapper.text()).toContain('30.00 CNY')
    await wrapper.get('select').setValue('DEMO-001')
    expect(wrapper.text()).not.toContain('BILL-002')
  })
  it('加载期间阻止重复请求', async () => {
    let resolve
    getTicketContext.mockImplementation(() => new Promise(done => { resolve = done }))
    const wrapper = mount(App)
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')
    expect(getTicketContext).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button').element.disabled).toBe(true)
    resolve(fixture)
    await flushPromises()
    expect(wrapper.get('button').element.disabled).toBe(false)
  })
  it('失败后清除旧数据并允许重试', async () => {
    getTicketContext.mockResolvedValueOnce(fixture).mockRejectedValueOnce(new Error('读取失败'))
    const wrapper = mount(App)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role=alert]').text()).toBe('读取失败')
    expect(wrapper.text()).not.toContain('BILL-002')
    expect(wrapper.get('button').element.disabled).toBe(false)
  })
})
