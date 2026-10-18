export const states = { ANALYZING: '预审进行中', PENDING_REVIEW: '待人工复核', REVIEWED: '已完成复核' }
export const recommendations = { VERIFY_AUTHORIZATION: '补充核实订购授权', VERIFY_BILLING: '核对账单与计费依据', MANUAL_REVIEW: '转投诉专员或主管进一步核实' }
export const findings = {
  BILL_MISSING: '未找到关联账单', BILL_UNAVAILABLE: '账单暂不可查询', ORDER_MISSING: '未找到订购记录',
  ORDER_UNAVAILABLE: '订购记录暂不可查询', AUTHORIZATION_MISSING: '缺少授权凭据', AUTHORIZATION_UNAVAILABLE: '授权凭据暂不可查询',
  ORDER_NOT_LINKED: '账单未匹配到订购记录', AUTHORIZATION_NOT_LINKED: '授权凭据关联不完整', SERVICE_MISMATCH: '业务标识不一致', AMOUNT_MISMATCH: '账单与订购金额不一致',
}
export const failures = { MODEL_NOT_CONFIGURED: '模型未配置', MODEL_TIMEOUT: '模型响应超时', MODEL_ERROR: '模型服务异常', INVALID_OUTPUT: '模型输出格式不合法', INVALID_EVIDENCE_REFERENCE: '模型引用了不存在的证据', INVALID_CONTEXT: '业务上下文校验失败', CONTEXT_UNAVAILABLE: '宿主业务证据暂不可用', TICKET_NOT_FOUND: '宿主工单不存在', INTERRUPTED: '预审进程中断' }
export const actions = { PRECHECK_QUEUED: '宿主事件已触发自动预处理', PRECHECK_TASK_STARTED: 'AI 核查任务开始执行', PRECHECK_RETRY_SCHEDULED: '临时失败，等待自动重试', PRECHECK_RETRY_AUTHORIZED: '人工授权重新执行', PRECHECK_TASK_RECOVERED: '中断任务已恢复', PRECHECK_STARTED: '人工调试触发预审', PRECHECK_SUCCESS: 'AI 核查材料已生成', PRECHECK_FAILED: 'AI 未完成，转人工处理', PRECHECK_INTERRUPTED: '中断恢复，转人工', REVIEW_CONFIRMED: '投诉专员确认核查材料', REVIEW_REJECTED: '投诉专员退回核查材料', BASELINE_IMPORT: '历史数据基线导入' }
export function dateTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
export function money(value) { return value == null ? '未提取' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
