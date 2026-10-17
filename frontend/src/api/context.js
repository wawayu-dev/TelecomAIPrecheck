export async function getTicketContext(ticketId) {
  const response = await fetch(`/api/tickets/${encodeURIComponent(ticketId)}/context`)
  if (!response.ok) {
    let code = 'REQUEST_FAILED'
    try { code = (await response.json()).code || code } catch { /* 非 JSON 错误使用通用提示 */ }
    const messages = {
      TICKET_NOT_FOUND: '未找到工单',
      CONTEXT_UNAVAILABLE: '宿主数据暂不可用，请稍后重试',
      INVALID_REQUEST: '工单编号格式不合法',
    }
    throw new Error(messages[code] || '读取失败，请确认后端已启动')
  }
  return response.json()
}
