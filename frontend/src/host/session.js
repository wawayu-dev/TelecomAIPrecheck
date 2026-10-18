import { readonly, shallowRef } from 'vue'
import { createPrecheckApi } from '../modules/precheck/api'

export class ApiError extends Error {
  constructor(status, code, traceId) { super(code); Object.assign(this, { status, code, traceId }) }
}
// 仅模拟宿主：凭据只在内存保留；真实宿主替换 request 与 identity 即可。
export function createDemoHost(fetcher = (...args) => fetch(...args)) {
  const identity = shallowRef(null)
  let authorization = '', csrf = null, revision = 0
  async function request(path, options = {}) {
    const headers = { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
    if (authorization) headers.Authorization = authorization
    if (options.method === 'POST') {
      headers['Content-Type'] = 'application/json'
      if (csrf) headers[csrf.csrfHeader] = csrf.csrfToken
    }
    const generation = revision
    const response = await fetcher(path, {
      ...options, body: options.body === undefined ? undefined : JSON.stringify(options.body),
      headers, credentials: 'same-origin',
    })
    let data = null
    const text = await response.text()
    if (text) { try { data = JSON.parse(text) } catch { /* 代理 HTML 不作为业务信息展示 */ } }
    if (!response.ok) {
      if (response.status === 401 && generation === revision) clear()
      throw new ApiError(response.status, data?.code || 'REQUEST_FAILED', data?.traceId)
    }
    if (text && data === null) throw new ApiError(response.status, 'INVALID_RESPONSE')
    return data
  }
  function clear() { revision++; identity.value = null; authorization = ''; csrf = null }
  async function login(username, password) {
    clear()
    // UTF-8 编码，以支持宿主密码中的非 ASCII 字符。
    authorization = 'Basic ' + btoa(Array.from(new TextEncoder().encode(`${username}:${password}`), b => String.fromCharCode(b)).join(''))
    const attempt = revision
    try {
      const session = await request('/api/session')
      if (attempt !== revision) return
      if (!session?.actorId || !Array.isArray(session.roles) || !session.csrfHeader || !session.csrfToken) throw new ApiError(0, 'INVALID_RESPONSE')
      csrf = { csrfHeader: session.csrfHeader, csrfToken: session.csrfToken }
      identity.value = { actorId: session.actorId, roles: session.roles }
    } catch (error) { if (attempt === revision) clear(); throw error }
  }
  async function logout() {
    try { await request('/api/session/logout', { method: 'POST', body: {} }) }
    finally { clear() }
  }
  return { identity: readonly(identity), api: createPrecheckApi(request), login, logout }
}
