import { afterEach, it, expect, vi } from 'vitest'
import { getTicketContext } from './context'
afterEach(() => vi.unstubAllGlobals())
it('编码宿主工单 ID 并读取响应', async () => {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ticketId: 'A/B' }) })
  vi.stubGlobal('fetch', fetchMock)
  expect(await getTicketContext('A/B')).toEqual({ ticketId: 'A/B' })
  expect(fetchMock).toHaveBeenCalledWith('/api/tickets/A%2FB/context')
})
it('后端错误使用白名单提示，不展示任意服务端原文', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => ({ code: 'TICKET_NOT_FOUND', message: 'secret' }) }))
  await expect(getTicketContext('X')).rejects.toThrow('未找到工单')
})
it('代理返回非 JSON 时仍提供可理解的错误', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => { throw new Error('HTML') } }))
  await expect(getTicketContext('X')).rejects.toThrow('读取失败，请确认后端已启动')
})
