import { mount, flushPromises } from '@vue/test-utils'
import { shallowRef } from 'vue'
import { createMemoryHistory } from 'vue-router'
import { it, expect, vi, afterEach } from 'vitest'
import App from './App.vue'
import { createHostRouter } from './router'
import { precheckHostKey } from './modules/precheck'
import { context, view } from './modules/precheck/fixtures'
const wrappers = []
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))
const draftTickets = [
  { id: 'DEMO-001', title: '视频会员扣费争议', note: '账单与授权材料齐全', priority: '普通', workflowStatus: 'DRAFT', statusLabel: '登记中', reviewerBucket: null, submitted: false },
  { id: 'DEMO-002', title: '未确认的业务订购', note: '缺少授权凭据', priority: '关注', workflowStatus: 'DRAFT', statusLabel: '登记中', reviewerBucket: null, submitted: false },
  { id: 'DEMO-003', title: '月费金额不一致', note: '账单与订购价格存在差异', priority: '普通', workflowStatus: 'DRAFT', statusLabel: '登记中', reviewerBucket: null, submitted: false },
]
it('宿主只登录一次，通过路由传入工单；身份切换不改变工单路由', async () => {
  const host = { identity: shallowRef(null), api: { tickets: vi.fn(async () => draftTickets), context: vi.fn(async id => context(id)), history: vi.fn(async () => []), current: vi.fn(async () => null), transition: vi.fn() } }
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
  expect(host.api.context).toHaveBeenLastCalledWith('DEMO-002')
  expect(wrapper.find('.business-nav').exists()).toBe(true)
  expect(wrapper.find('.sidebar-filter').exists()).toBe(false)
  expect(wrapper.text()).toContain('新建投诉')
  expect(wrapper.get('.complaint-field textarea').element.value).toBe('合成投诉 DEMO-002')
  await router.push('/host/tickets/DEMO-003/precheck'); await flushPromises()
  expect(wrapper.get('.complaint-field textarea').element.value).toBe('合成投诉 DEMO-003'); expect(wrapper.text()).not.toContain('BILL-002')
  await wrapper.findAll('.host-identity button').find(button => button.text() === '切换身份').trigger('click'); await flushPromises()
  expect(router.currentRoute.value.params.ticketId).toBe('DEMO-003')
  expect(wrapper.find('.precheck-module').exists()).toBe(false)
  expect(wrapper.find('#host-password').exists()).toBe(false)
  await wrapper.get('#host-account').setValue('reviewer'); await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(wrapper.text()).toContain('投诉处理专员'); expect(wrapper.text()).toContain('当前分类暂无可处理工单')
})
it('未知路由不静默切换到别的工单', async () => {
  const router = createHostRouter(createMemoryHistory()); await router.push('/invalid'); await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router], provide: { [precheckHostKey]: { identity: shallowRef({ actorId: 'auditor', roles: ['ROLE_AUDITOR'] }) } } } }); wrappers.push(wrapper)
  expect(wrapper.text()).toContain('页面不存在')
})
it('模拟宿主流转只提交业务事件并刷新工作台，不调用人工预审接口', async () => {
  let submitted = false
  const host={ identity:shallowRef({actorId:'operator',roles:['ROLE_OPERATOR']}), api:{
    tickets:vi.fn(async () => submitted
      ? [{...draftTickets[0],workflowStatus:'SUBMITTED',statusLabel:'核查中',reviewerBucket:'PROCESSING',submitted:true},...draftTickets.slice(1)]
      : draftTickets),
    context:vi.fn(async id=>context(id)),history:vi.fn(async()=>[]),current:vi.fn(async()=>null),
    updateTicket:vi.fn(async(id,body)=>({id,...body})),
    transition:vi.fn(async(ticket,body)=>{ submitted=true; return {ticketId:ticket,ticketVersion:body.ticketVersion,transitioned:true,precheckAccepted:true} })
  },logout:vi.fn() }
  const router=createHostRouter(createMemoryHistory()); await router.push('/host/tickets/DEMO-001/precheck'); await router.isReady()
  const wrapper=mount(App,{global:{plugins:[router],provide:{[precheckHostKey]:host}}}); wrappers.push(wrapper); await flushPromises()
  await wrapper.get('.business-form').trigger('submit'); await flushPromises()
  expect(host.api.transition).toHaveBeenCalledTimes(1)
  expect(host.api.transition.mock.calls[0][0]).toBe('DEMO-001')
  expect(wrapper.text()).toContain('投诉工单已提交')
  expect(wrapper.text()).toContain('已提交 · 核查中')
  expect(host.api.context).toHaveBeenCalledTimes(1)
})

it('投诉专员页签按真实工作流状态筛选，并显示各分类数量', async () => {
  const rows = [
    { ...draftTickets[0], workflowStatus: 'SUBMITTED', statusLabel: '待核查', reviewerBucket: 'PENDING', submitted: true },
    { ...draftTickets[1], workflowStatus: 'SUBMITTED', statusLabel: '核查中', reviewerBucket: 'PROCESSING', submitted: true },
    { ...draftTickets[2], workflowStatus: 'COMPLETED', statusLabel: '已完成', reviewerBucket: 'COMPLETED', submitted: true },
  ]
  const detail = view()
  const host = { identity: shallowRef({ actorId: 'reviewer', roles: ['ROLE_REVIEWER'] }), api: {
    tickets: vi.fn(async () => rows), context: vi.fn(async id => context(id)), history: vi.fn(async () => [detail]),
    current: vi.fn(async () => detail), run: vi.fn(async () => detail), review: vi.fn(async () => null),
    submit: vi.fn(), retry: vi.fn(),
  }, logout: vi.fn() }
  const router = createHostRouter(createMemoryHistory()); await router.push('/host/tickets/DEMO-001/precheck'); await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router], provide: { [precheckHostKey]: host } } }); wrappers.push(wrapper); await flushPromises()
  expect(wrapper.findAll('.business-nav button').map(button => button.text())).toEqual(['核查待办1', '处理中1', '已完成1'])
  expect(wrapper.find('.ticket-nav strong').text()).toBe('视频会员扣费争议')
  await wrapper.findAll('.business-nav button')[1].trigger('click'); await flushPromises()
  expect(wrapper.find('.ticket-nav strong').text()).toBe('未确认的业务订购')
  await wrapper.findAll('.business-nav button')[2].trigger('click'); await flushPromises()
  expect(wrapper.find('.ticket-nav strong').text()).toBe('月费金额不一致')
})

it('空列表可以手工登记新投诉并提交到数据库工作流', async () => {
  let submitted = false
  const createdRow = { id: 'TKT-NEW001', title: '新登记扣费争议', note: '客户反馈不认可视频会员扣费', priority: '关注',
    workflowStatus: 'SUBMITTED', statusLabel: '核查中', reviewerBucket: 'PROCESSING', submitted: true,
    category: '增值业务扣费争议', channel: '10000号热线' }
  const host = { identity: shallowRef({ actorId: 'operator', roles: ['ROLE_OPERATOR'] }), api: {
    tickets: vi.fn(async () => submitted ? [createdRow] : []),
    createTicket: vi.fn(async body => ({ id: 'TKT-NEW001', ...body })),
    transition: vi.fn(async () => { submitted = true; return { transitioned: true, precheckAccepted: true } }),
    context: vi.fn(async id => ({ ...context(id), customerRef: 'CUSTOMER-NEW', complaint: '客户反馈不认可视频会员扣费' })),
  }, logout: vi.fn() }
  const router = createHostRouter(createMemoryHistory()); await router.push('/host/tickets/DEMO-001/precheck'); await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router], provide: { [precheckHostKey]: host } } }); wrappers.push(wrapper); await flushPromises()
  await wrapper.get('.host-empty-state button').trigger('click'); await flushPromises()
  await wrapper.get('input[placeholder="例如：视频会员扣费争议"]').setValue('新登记扣费争议')
  await wrapper.get('input[placeholder="请输入客户标识"]').setValue('CUSTOMER-NEW')
  await wrapper.get('.complaint-field textarea').setValue('客户反馈不认可视频会员扣费')
  await wrapper.findAll('.operator-more select')[1].setValue('关注')
  await wrapper.get('.business-form').trigger('submit'); await flushPromises()
  expect(host.api.createTicket).toHaveBeenCalledWith(expect.objectContaining({ title: '新登记扣费争议', customerRef: 'CUSTOMER-NEW', priority: '关注' }))
  expect(host.api.transition).toHaveBeenCalledWith('TKT-NEW001', expect.objectContaining({ eventId: expect.stringMatching(/^WEB-/) }))
  expect(router.currentRoute.value.params.ticketId).toBe('TKT-NEW001')
  expect(wrapper.text()).toContain('客户投诉记录')
  expect(wrapper.text()).toContain('核查中')
})
