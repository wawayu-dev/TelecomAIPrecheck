import { mount, flushPromises } from '@vue/test-utils'
import { shallowRef } from 'vue'
import { afterEach, describe, it, expect, vi } from 'vitest'
import PrecheckPage from './PrecheckPage.vue'
import { precheckHostKey } from './api'
import { context, view, event } from './fixtures'
const wrappers = []
function setup(role = 'REVIEWER', initial = view()) {
  let current = initial, rows = initial ? [initial] : [], saved = null
  const host = { identity: shallowRef({ actorId: role.toLowerCase(), roles: ['ROLE_' + role] }), api: {
    context: vi.fn(async ticket => context(ticket)), history: vi.fn(async () => rows),
    current: vi.fn(async () => current), run: vi.fn(async id => rows.find(item => item.run.id === id) || current), review: vi.fn(async () => saved),
    timeline: vi.fn(async () => saved ? [{ event }, { event: { ...event, id: 2, action: 'REVIEW_CONFIRMED', actorId: 'reviewer', runVersion: 2 }, reviewChange: { aiResult: current.result, humanResult: saved } }] : [{ event }]),
    start: vi.fn(async () => { current = view(); rows=[current]; return current }),
    retry: vi.fn(async () => { current = { ...current, run: { ...current.run, status: 'ANALYZING', callStatus: 'PENDING', taskStatus: 'QUEUED', failureCode: null, version: current.run.version + 1 } }; rows=[current,...rows.filter(item=>item.run.id!==current.run.id)]; return current }),
    submit: vi.fn(async (_id, body) => { saved = { ...body, actorId: 'reviewer', version: 2, reviewedAt: '2026-10-17T12:00:00Z' }; current = { ...current, run: { ...current.run, status: 'REVIEWED', version: 2 } }; rows=[current,...rows.filter(item=>item.run.id!==current.run.id)]; return saved }),
  } }
  const wrapper = mount(PrecheckPage, { props: { ticketId: 'DEMO-001' }, global: { provide: { [precheckHostKey]: host } } })
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
    expect(wrapper.text()).toContain('关键疑点')
    expect(wrapper.text()).toContain('建议操作')
    expect(wrapper.text()).toContain('核实授权时间、验证方式和操作主体')
    expect(wrapper.text()).toContain('账单与订购金额不一致')
    expect(wrapper.text()).toContain('¥ 30.00')
    expect(host.api.timeline).not.toHaveBeenCalled()
    expect(wrapper.findAll('input')).toHaveLength(0)
  })
  it('成功预审可以人工修订并携带版本提交，防止重复提交', async () => {
    const { wrapper, host } = setup(); await flushPromises()
    await wrapper.get('#review-summary').setValue('人工修正摘要')
    await wrapper.get('#review-reason').setValue('核查依据')
    let resolve; const original = host.api.submit.getMockImplementation()
    host.api.submit.mockImplementation((...args) => new Promise(done => { resolve = async () => done(await original(...args)) }))
    await wrapper.get('form').trigger('submit'); await wrapper.get('form').trigger('submit')
    expect(host.api.submit).toHaveBeenCalledTimes(1)
    expect(host.api.submit).toHaveBeenCalledWith('run-1', { expectedVersion: 1, decision: 'CONFIRMED', summary: '人工修正摘要', recommendation: 'VERIFY_AUTHORIZATION', reason: '核查依据' })
    expect(wrapper.get('#review-summary').element.disabled).toBe(true)
    await resolve(); await flushPromises()
    expect(wrapper.text()).toContain('已确认')
    expect(wrapper.text()).toContain('人工修正摘要')
    expect(wrapper.text()).toContain('账单金额与订购价格相差10元')
    expect(wrapper.text()).toContain('核查依据')
    expect(wrapper.find('form').exists()).toBe(false)
  })
  it('模型失败可人工驳回，不虚构 AI 结果', async () => {
    const failed = view(); failed.run.callStatus = 'FAILED'; failed.run.taskStatus = 'FAILED'; failed.run.failureCode = 'MODEL_TIMEOUT'; failed.result = null
    const { wrapper, host } = setup('REVIEWER', failed); await flushPromises()
    expect(wrapper.text()).toContain('模型响应超时')
    expect(wrapper.text()).not.toContain('账单金额与订购价格相差10元')
    await wrapper.get('#review-summary').setValue('需继续调查')
    await wrapper.get('#review-reason').setValue('缺少核查材料')
    await button(wrapper, '要求补充材料').trigger('click'); await flushPromises()
    expect(host.api.submit.mock.calls[0][1].decision).toBe('REJECTED')
    expect(wrapper.text()).toContain('待补充材料')
  })
  it('失败任务不阻塞人工填写，并允许有权限人员授权异步重试', async () => {
    const failed=view(); failed.run.callStatus='FAILED'; failed.run.taskStatus='FAILED'; failed.run.failureCode='MODEL_TIMEOUT'; failed.result=null
    const {wrapper,host}=setup('REVIEWER',failed); await flushPromises()
    expect(wrapper.find('form').exists()).toBe(true)
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
    expect(wrapper.find('#precheck-round').element.value).toBe('run-current')
    await wrapper.get('#precheck-round').setValue('run-stale'); await flushPromises()
    expect(wrapper.text()).toContain('该材料已被新版本替代')
    expect(wrapper.find('form').exists()).toBe(false)
  })
  it('空摘要或依据不能驳回', async () => {
    const { wrapper, host } = setup(); await flushPromises()
    await wrapper.get('#review-summary').setValue('')
    await wrapper.get('#review-reason').setValue('')
    await button(wrapper, '要求补充材料').trigger('click')
    expect(host.api.submit).not.toHaveBeenCalled()
    expect(wrapper.get('[role=alert]').text()).toContain('请填写核查摘要和核查意见')
  })
  it('409 后刷新最新状态并保留编辑，禁止覆盖已完成审核', async () => {
    const { wrapper, host, setView } = setup(); await flushPromises()
    await wrapper.get('#review-summary').setValue('我还未提交的修订')
    await wrapper.get('#review-reason').setValue('我的依据')
    host.api.submit.mockImplementation(async () => { setView(view('DEMO-001', 'REVIEWED')); throw { status: 409, code: 'REVIEW_CONFLICT' } })
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(wrapper.get('[role=alert]').text()).toContain('状态或版本已变化')
    expect(wrapper.get('.preserved-draft').text()).toContain('我还未提交的修订')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(host.api.submit).toHaveBeenCalledTimes(1)
  })
  it('请求结果不明确时刷新并保留草稿，不自动重发', async () => {
    const { wrapper, host } = setup(); await flushPromises()
    await wrapper.get('#review-reason').setValue('依据保留')
    host.api.submit.mockRejectedValue(new TypeError('Network down'))
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(wrapper.get('.preserved-draft').text()).toContain('依据保留')
    expect(host.api.submit).toHaveBeenCalledTimes(1)
    expect(host.api.run).toHaveBeenCalledTimes(2)
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
    host.api.context.mockResolvedValue({ ...context(), complaint: '宿主当前的新投诉' })
    await button(wrapper, '刷新').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('核查时证据快照')
    await button(wrapper, '当前数据').trigger('click')
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
    const data = view(); data.result.summary = '<img src=x onerror=alert(1)>'
    const { wrapper } = setup('AUDITOR', data); await flushPromises()
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists()).toBe(false)
  })
  it('进行中记录自动刷新，完成前不可审核', async () => {
    vi.useFakeTimers()
    const data = view('DEMO-001', 'ANALYZING'); data.run.callStatus = 'RUNNING'; data.run.taskStatus = 'RUNNING'; data.result = null
    const { wrapper, setView } = setup('REVIEWER', data); await flushPromises()
    expect(wrapper.find('form').exists()).toBe(false)
    setView(view()); await vi.advanceTimersByTimeAsync(4000); await flushPromises()
    expect(wrapper.find('form').exists()).toBe(true)
  })
  it('角色变更后重新校验页面权限并移除旧审计', async () => {
    const { wrapper, host } = setup(); await flushPromises()
    host.identity.value = { actorId: 'operator', roles: ['ROLE_OPERATOR'] }; await flushPromises()
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('system:model')
  })
})
