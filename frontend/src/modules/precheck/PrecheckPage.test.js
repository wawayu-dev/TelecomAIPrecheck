import { mount, flushPromises } from '@vue/test-utils'
import { shallowRef } from 'vue'
import { afterEach, describe, it, expect, vi } from 'vitest'
import PrecheckPage from './PrecheckPage.vue'
import { precheckHostKey } from './api'
import { context, view, event } from './fixtures'
const wrappers = []
function setup(role = 'REVIEWER', initial = view(), ticketId = 'DEMO-001', contextOverride = null) {
  let current = initial, rows = initial ? [initial] : [], saved = null
  const host = { identity: shallowRef({ actorId: role.toLowerCase(), roles: ['ROLE_' + role] }), api: {
    context: vi.fn(async ticket => contextOverride && ticket === ticketId ? contextOverride : context(ticket)), history: vi.fn(async () => rows),
    current: vi.fn(async () => current), run: vi.fn(async id => rows.find(item => item.run.id === id) || current), review: vi.fn(async () => saved),
    timeline: vi.fn(async () => saved ? [{ event }, { event: { ...event, id: 2, action: 'REVIEW_CONFIRMED', actorId: 'reviewer', runVersion: 2 }, reviewChange: { aiResult: current.result, humanResult: saved } }] : [{ event }]),
    start: vi.fn(async () => { current = view(); rows=[current]; return current }),
    retry: vi.fn(async () => { current = { ...current, run: { ...current.run, status: 'ANALYZING', callStatus: 'PENDING', taskStatus: 'QUEUED', failureCode: null, version: current.run.version + 1 } }; rows=[current,...rows.filter(item=>item.run.id!==current.run.id)]; return current }),
    submit: vi.fn(async (_id, body) => { saved = { ...body, actorId: 'reviewer', version: 2, reviewedAt: '2026-10-17T12:00:00Z' }; current = { ...current, run: { ...current.run, status: 'REVIEWED', version: 2 } }; rows=[current,...rows.filter(item=>item.run.id!==current.run.id)]; return saved }),
  } }
  const wrapper = mount(PrecheckPage, { props: { ticketId }, global: { provide: { [precheckHostKey]: host } } })
  wrappers.push(wrapper)
  return { wrapper, host, setView(value) { current = value; rows = value ? [value] : [] }, setHistory(value, effective = value[0] || null) { rows = value; current = effective } }
}
function button(wrapper, text) { return wrapper.findAll('button').find(b => b.text() === text) }
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.useRealTimers() })
describe('预审业务模块', () => {
  it('从宿主路由上下文自动读取并展示快照、规则及引用', async () => {
    const { wrapper, host } = setup(); await flushPromises()
    expect(host.api.context).toHaveBeenCalledWith('DEMO-001', expect.any(AbortSignal))
    expect(wrapper.text()).toContain('合成投诉 DEMO-001')
    expect(wrapper.text()).toContain('AI 辅助处理建议')
    expect(wrapper.text()).toContain('AI 生成 · 仅供参考')
    expect(wrapper.text()).toContain('初步调查结论')
    expect(wrapper.text()).toContain('建议处理意见')
    expect(wrapper.text()).toContain('核实授权时间、验证方式和操作主体')
    expect(wrapper.text()).toContain('账单与订购金额不一致')
    expect(wrapper.text()).toContain('¥ 30.00')
    expect(wrapper.text()).not.toContain('证据核查结论')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(host.api.timeline).not.toHaveBeenCalled()
    expect(wrapper.findAll('input[type="text"]')).toHaveLength(0)
  })
  it('DEMO-002 直接展示谨慎表述，并可复制完整处理建议', async () => {
    const clipboard = { writeText: vi.fn(async () => {}) }
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: clipboard })
    const demoContext = context('DEMO-002'); demoContext.subscriptions.items[0].monthlyPrice = '30.00'
    const demoView = view('DEMO-002'); demoView.evidenceSnapshot = demoContext
    const { wrapper, host } = setup('REVIEWER', demoView, 'DEMO-002', demoContext); await flushPromises()
    expect(wrapper.text()).toContain('当前未发现计价异常，主动订购授权依据不足。')
    expect(wrapper.text()).toContain('当前未查询到关联授权凭据，尚不能确认是否经过客户有效授权')
    expect(wrapper.text()).toContain('ORDER-002')
    expect(wrapper.text()).toContain('BILL-002')
    await button(wrapper, '复制 AI 处理建议').trigger('click'); await flushPromises()
    expect(clipboard.writeText).toHaveBeenCalledOnce()
    expect(clipboard.writeText.mock.calls[0][0]).toContain('初步调查结论')
    expect(clipboard.writeText.mock.calls[0][0]).toContain('关联证据')
    expect(wrapper.text()).toContain('AI 处理建议已复制')
    expect(host.api.submit).not.toHaveBeenCalled()
  })
  it('引用并编辑允许修改、清空和复制，但不触发审核接口', async () => {
    const clipboard = { writeText: vi.fn(async () => {}) }
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: clipboard })
    const { wrapper, host } = setup(); await flushPromises()
    await button(wrapper, '引用并编辑').trigger('click')
    expect(wrapper.get('#ai-suggestion-editor').element.value).toContain('初步调查结论')
    await wrapper.get('#ai-suggestion-editor').setValue('人工重写的处理备注')
    await button(wrapper, '复制编辑后内容').trigger('click'); await flushPromises()
    expect(clipboard.writeText).toHaveBeenCalledWith('人工重写的处理备注')
    expect(host.api.submit).not.toHaveBeenCalled()
  })
  it('模型失败不虚构建议，并允许有权限人员授权异步重试', async () => {
    const failed=view(); failed.run.callStatus='FAILED'; failed.run.taskStatus='FAILED'; failed.run.failureCode='MODEL_TIMEOUT'; failed.result=null
    const {wrapper,host}=setup('REVIEWER',failed); await flushPromises()
    expect(wrapper.text()).toContain('模型响应超时')
    expect(wrapper.text()).not.toContain('建议处理意见')
    expect(wrapper.text()).toContain('当前没有可用的 AI 处理建议')
    await button(wrapper,'重新执行').trigger('click'); await flushPromises()
    expect(host.api.retry).toHaveBeenCalledWith('run-1',1)
    expect(wrapper.text()).toContain('已授权重新执行 AI 核查')
    expect(wrapper.text()).toContain('系统正在准备核查建议')
  })
  it('默认读取后端判定的当前有效版本，旧版本仅可追溯', async () => {
    const current=view('DEMO-001','PENDING_REVIEW','run-current')
    const stale=view('DEMO-001','PENDING_REVIEW','run-stale'); stale.run.currentResult=false; stale.run.ticketVersion=1
    current.run.ticketVersion=2
    const {wrapper,setHistory}=setup('REVIEWER',current); await flushPromises()
    setHistory([stale,current],current)
    await button(wrapper,'刷新').trigger('click'); await flushPromises()
    expect(wrapper.find('.history-cards article.active').text()).toContain('run-curr')
    await wrapper.findAll('.history-cards article')[0].find('button').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('该材料已被新版本替代')
    expect(wrapper.find('form').exists()).toBe(false)
  })
  it('切换工单后忽略旧请求的迟到响应', async () => {
    const { wrapper, host, setView } = setup(); await flushPromises()
    let resolve
    host.api.context.mockImplementationOnce(() => new Promise(done => { resolve = done }))
    await button(wrapper, '刷新').trigger('click')
    setView(null); await wrapper.setProps({ ticketId: 'DEMO-002' }); await flushPromises()
    resolve(context('DEMO-001')); await flushPromises()
    expect(wrapper.text()).toContain('合成投诉 DEMO-002')
    expect(wrapper.text()).not.toContain('BILL-001')
  })
  it('当前数据与历史快照明确区分，引用自动切回快照', async () => {
    const { wrapper, host } = setup(); await flushPromises()
    const changed = structuredClone(context()); changed.complaint = '宿主当前的新投诉'; changed.bills.items[0].amount = '35.00'
    host.api.context.mockResolvedValue(changed)
    await button(wrapper, '刷新').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('发现 1 类数据变更')
    expect(wrapper.text()).toContain('当前业务数据')
    await wrapper.findAll('.evidence-link')[0].trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('核查时证据快照')
  })
  it('接口失败不显示旧数据，允许重试', async () => {
    const { wrapper, host } = setup(); await flushPromises()
    host.api.context.mockRejectedValueOnce({ code: 'CONTEXT_UNAVAILABLE', traceId: 'trace-failure' })
    await button(wrapper, '刷新').trigger('click'); await flushPromises()
    expect(wrapper.get('[role=alert]').text()).toContain('trace-failure')
    expect(wrapper.text()).not.toContain('BILL-001')
    await button(wrapper, '刷新数据').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('BILL-001')
  })
  it('模型文本按纯文本渲染，不能注入 HTML', async () => {
    const data = view(); data.result.evidenceGap = '<img src=x onerror=alert(1)>'
    const { wrapper } = setup('AUDITOR', data); await flushPromises()
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists()).toBe(false)
  })
  it('进行中记录自动刷新，完成后展示辅助建议', async () => {
    vi.useFakeTimers()
    const data = view('DEMO-001', 'ANALYZING'); data.run.callStatus = 'RUNNING'; data.run.taskStatus = 'RUNNING'; data.result = null
    const { wrapper, setView } = setup('REVIEWER', data); await flushPromises()
    expect(button(wrapper, '复制 AI 处理建议')).toBeUndefined()
    setView(view()); await vi.advanceTimersByTimeAsync(4000); await flushPromises()
    expect(button(wrapper, '复制 AI 处理建议')).toBeTruthy()
  })
  it('角色变更后重新校验页面权限并移除旧审计', async () => {
    const { wrapper, host } = setup(); await flushPromises()
    host.identity.value = { actorId: 'operator', roles: ['ROLE_OPERATOR'] }; await flushPromises()
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('system:model')
  })
})
