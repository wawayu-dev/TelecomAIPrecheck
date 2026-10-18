export const states = { ANALYZING: '预审进行中', PENDING_REVIEW: '待人工复核', REVIEWED: '已完成复核' }
export const recommendations = { VERIFY_AUTHORIZATION: '补充核实订购授权', VERIFY_BILLING: '核对账单与计费依据', MANUAL_REVIEW: '转人工进一步核实' }
export const findings = {
  BILL_MISSING: '未找到关联账单', BILL_UNAVAILABLE: '账单暂不可查询', ORDER_MISSING: '未找到订购记录',
  ORDER_UNAVAILABLE: '订购记录暂不可查询', AUTHORIZATION_MISSING: '缺少授权凭据', AUTHORIZATION_UNAVAILABLE: '授权凭据暂不可查询',
  ORDER_NOT_LINKED: '账单未匹配到订购记录', AUTHORIZATION_NOT_LINKED: '授权凭据关联不完整', SERVICE_MISMATCH: '业务标识不一致', AMOUNT_MISMATCH: '账单与订购金额不一致',
}
export const failures = { MODEL_NOT_CONFIGURED: '模型未配置', MODEL_TIMEOUT: '模型响应超时', MODEL_ERROR: '模型服务异常', INVALID_OUTPUT: '模型输出格式不合法', INVALID_EVIDENCE_REFERENCE: '模型引用了不存在的证据', INVALID_CONTEXT: '业务上下文校验失败', INTERRUPTED: '预审进程中断' }
export const actions = { PRECHECK_STARTED: '发起 AI 预审', PRECHECK_SUCCESS: 'AI 预审完成', PRECHECK_FAILED: '预审失败，转人工', PRECHECK_INTERRUPTED: '中断恢复，转人工', REVIEW_CONFIRMED: '人工确认建议', REVIEW_REJECTED: '人工驳回预审', BASELINE_IMPORT: '历史数据基线导入' }
export function dateTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
export function money(value) { return value == null ? '未提取' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
