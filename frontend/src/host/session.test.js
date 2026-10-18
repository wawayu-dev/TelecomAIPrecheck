import { expect, it, vi } from 'vitest'
import { createDemoHost } from './session'
import { errorText } from '../modules/precheck/api'
const session = { actorId: 'operator', roles: ['ROLE_OPERATOR'], csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'csrf-test' }
const response = (data, status = 200) => ({ ok: status < 400, status, text: async () => data == null ? '' : JSON.stringify(data) })
it('一键演示登录读取服务器角色，后续写请求携带 CSRF 会话', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(response(session)).mockResolvedValueOnce(response({ run: {} }, 201))
  const host = createDemoHost(fetch); await host.login('operator')
  expect(host.identity.value).toEqual({ actorId: 'operator', roles: ['ROLE_OPERATOR'] })
  expect(fetch.mock.calls[0][0]).toBe('/api/demo-session')
  expect(fetch.mock.calls[0][1]).toMatchObject({ method: 'POST', body: '{"account":"operator"}', credentials: 'same-origin' })
  await host.api.start('A/B')
  expect(fetch.mock.calls[1][0]).toBe('/api/ai-prechecks/tickets/A%2FB')
  expect(fetch.mock.calls[1][1]).toMatchObject({ method: 'POST', body: '{}', credentials: 'same-origin', headers: { 'X-CSRF-TOKEN': 'csrf-test' } })
})
it('失败登录清除旧身份，不能保留之前的权限', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(response(session)).mockResolvedValueOnce(response({ code: 'UNAUTHENTICATED' }, 401))
  const host = createDemoHost(fetch); await host.login('operator')
  await expect(host.login('reviewer')).rejects.toMatchObject({ status: 401 })
  expect(host.identity.value).toBeNull()
})
it('退出调用服务端注销，清除内存身份及认证', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(response(session)).mockResolvedValueOnce(response(null, 204)).mockResolvedValueOnce(response([]))
  const host = createDemoHost(fetch); await host.login('operator'); await host.logout()
  expect(fetch.mock.calls[1][0]).toBe('/api/session/logout'); expect(host.identity.value).toBeNull()
  await host.api.history('DEMO-001')
  expect(fetch.mock.calls[2][1].headers['X-CSRF-TOKEN']).toBeUndefined()
})
it('未审核的空响应解析为 null，非 JSON 代理错误不泄露原文', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(response(null)).mockResolvedValueOnce({ ok: false, status: 502, text: async () => '<html>secret</html>' })
  const host = createDemoHost(fetch)
  expect(await host.api.review('run')).toBeNull()
  try { await host.api.context('ID') } catch (error) { expect(errorText(error)).not.toContain('secret'); expect(errorText(error)).toContain('请求未完成') }
})
it('身份响应不完整时拒绝进入模块', async () => {
  const host = createDemoHost(vi.fn().mockResolvedValue(response({ actorId: 'operator' })))
  await expect(host.login('operator')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  expect(host.identity.value).toBeNull()
})
it('401 失效后移除页面身份，错误只显示白名单消息', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(response(session)).mockResolvedValueOnce(response({ code: 'UNAUTHENTICATED', message: 'secret', traceId: 'trace-1' }, 401))
  const host = createDemoHost(fetch); await host.login('operator')
  try { await host.api.context('ID') } catch (error) { expect(errorText(error)).toContain('trace-1'); expect(errorText(error)).not.toContain('secret') }
  expect(host.identity.value).toBeNull()
})
