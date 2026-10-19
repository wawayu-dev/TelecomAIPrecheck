import { flushPromises, mount } from '@vue/test-utils'
import { shallowRef } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RoleWorkspace from './RoleWorkspace.vue'
import { precheckHostKey } from './api'
import { context, event, view } from './fixtures'

const wrappers = []
function setup(role) {
  const data = view()
  const host = { identity: shallowRef({ actorId: role.toLowerCase(), roles: [`ROLE_${role}`] }), api: {
    context: vi.fn(async id => context(id)),
    tickets: vi.fn(async () => [{ id: 'DEMO-001', title: '视频会员扣费争议', note: '账单与授权材料齐全', priority: '普通', workflowStatus: 'DRAFT', statusLabel: '登记中', reviewerBucket: null, submitted: false }]),
    updateTicket: vi.fn(async (id, body) => ({ id, ...body })),
    transition: vi.fn(async () => ({ precheckAccepted: true })),
    history: vi.fn(async () => [data]), current: vi.fn(async () => data), run: vi.fn(async () => data),
    review: vi.fn(async () => role === 'AUDITOR' ? ({ decision: 'CONFIRMED', summary: '专员确认摘要', reason: '证据一致', recommendation: 'VERIFY_AUTHORIZATION', actorId: 'reviewer', reviewedAt: '2026-10-17T12:00:00Z' }) : null),
    timeline: vi.fn(async () => [{ event }]), retry: vi.fn(), submit: vi.fn(),
  } }
  const wrapper = mount(RoleWorkspace, { props: { ticketId: 'DEMO-001' }, global: { provide: { [precheckHostKey]: host } } })
  wrappers.push(wrapper)
  return { wrapper, host }
}
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('按岗位隔离的工作台', () => {
  it('客服只登记并提交投诉，不读取核查、证据结果或审计', async () => {
    const { wrapper, host } = setup('OPERATOR'); await flushPromises()
    expect(wrapper.text()).toContain('新建投诉')
    expect(wrapper.text()).toContain('提交投诉工单')
    expect(wrapper.text()).not.toContain('核查建议')
    expect(host.api.history).not.toHaveBeenCalled()
    expect(host.api.current).not.toHaveBeenCalled()
    expect(host.api.timeline).not.toHaveBeenCalled()
    await wrapper.get('.business-form').trigger('submit'); await flushPromises()
    expect(host.api.transition).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('已提交 · 核查中')
  })

  it('投诉专员直接查看 AI 辅助建议，不进入重复审核流程', async () => {
    const { wrapper, host } = setup('REVIEWER'); await flushPromises()
    expect(wrapper.text()).toContain('AI 辅助处理建议')
    expect(wrapper.text()).toContain('当前核查材料')
    expect(wrapper.find('.review-form').exists()).toBe(false)
    expect(wrapper.text()).toContain('复制 AI 处理建议')
    expect(host.api.current).toHaveBeenCalledTimes(1)
    expect(host.api.submit).not.toHaveBeenCalled()
    expect(host.api.timeline).not.toHaveBeenCalled()
  })

  it('质检使用独立只读审计页，按需展开技术日志', async () => {
    const { wrapper, host } = setup('AUDITOR'); await flushPromises()
    expect(wrapper.text()).toContain('工单审计概览')
    expect(wrapper.text()).toContain('审计轨迹')
    expect(wrapper.text()).toContain('查看技术日志')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.find('.review-form').exists()).toBe(false)
    expect(host.api.timeline).toHaveBeenCalledTimes(1)
  })
})
