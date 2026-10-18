// 宿主提供统一 request；模块不持有密码、不决定认证方式。
export function createPrecheckApi(request) {
  const id = encodeURIComponent
  const base = '/api/ai-prechecks'
  return {
    context: (ticket, signal) => request(`/api/tickets/${id(ticket)}/context`, { signal }),
    history: (ticket, offset = 0, signal) => request(`${base}/tickets/${id(ticket)}?limit=20&offset=${offset}`, { signal }),
    run: (run, signal) => request(`${base}/${id(run)}`, { signal }),
    review: (run, signal) => request(`${base}/${id(run)}/review`, { signal }),
    audit: (run, after = 0, signal) => request(`${base}/${id(run)}/audit?limit=50&after=${after}`, { signal }),
    start: ticket => request(`${base}/tickets/${id(ticket)}`, { method: 'POST', body: {} }),
    submit: (run, body) => request(`${base}/${id(run)}/review`, { method: 'POST', body }),
  }
}
export const precheckHostKey = Symbol('TelecomPrecheckHost')
export const messages = {
  UNAUTHENTICATED: '登录已失效，请在宿主系统重新登录。',
  FORBIDDEN: '当前身份无操作权限，或会话校验已失效。请刷新登录后重试。',
  TICKET_NOT_FOUND: '宿主系统中未找到这个工单。',
  PRECHECK_NOT_FOUND: '未找到该轮预审，请刷新历史记录。',
  CONTEXT_UNAVAILABLE: '宿主工单数据暂时不可用，请稍后重试。',
  INVALID_REQUEST: '请求内容不符合要求，请检查填写内容。',
  REVIEW_CONFLICT: '这条记录的状态或版本已变化，已尝试刷新最新结果。',
}
export function errorText(error) {
  return (messages[error?.code] || '请求未完成，请检查服务连接后重试。') + (error?.traceId ? ` 追踪编号：${error.traceId}` : '')
}
