// 测试用合成样本；运行中的页面不导入此文件。
export function context(ticketId = 'DEMO-001') {
  const suffix = ticketId.slice(-3)
  return { ticketId, schemaVersion: '1.0', synthetic: true, customerRef: 'SYNTHETIC', complaint: `合成投诉 ${ticketId}`, sourceSystem: 'test-host', capturedAt: '2026-10-17T10:00:00Z',
    bills: { status: 'AVAILABLE', items: [{ evidenceId: 'BILL-' + suffix, amount: '30.00', currency: 'CNY', billingPeriod: '2026-10', serviceId: 'VIDEO', subscriptionId: 'SUB-' + suffix }] },
    subscriptions: { status: 'AVAILABLE', items: [{ evidenceId: 'ORDER-' + suffix, monthlyPrice: '20.00', currency: 'CNY', serviceName: '视频会员', serviceId: 'VIDEO', subscriptionId: 'SUB-' + suffix, orderedAt: '2026-09-01T02:00:00Z' }] },
    authorizations: { status: 'NOT_FOUND', items: [] },
  }
}
export function view(ticketId = 'DEMO-001', status = 'PENDING_REVIEW', id = 'run-1') {
  return { run: { id, ticketId, status, callStatus: 'SUCCESS', version: 1, promptVersion: 'charge-dispute-v1', requestedModel: 'test-model', actualModel: 'test-model', createdAt: '2026-10-17T10:00:00Z', durationMs: 100, traceId: 'trace-test' },
    result: { complaintType: 'VALUE_ADDED_CHARGE_DISPUTE', disputedAmount: 30, summary: '合成 AI 摘要', recommendation: 'VERIFY_AUTHORIZATION', evidenceIds: ['BILL-' + ticketId.slice(-3)] },
    findings: [{ code: 'AMOUNT_MISMATCH', evidenceIds: ['BILL-' + ticketId.slice(-3), 'ORDER-' + ticketId.slice(-3)] }], evidenceSnapshot: context(ticketId),
  }
}
export const event = { id: 1, action: 'PRECHECK_SUCCESS', actorId: 'system:model', fromStatus: 'ANALYZING', toStatus: 'PENDING_REVIEW', outcome: 'SUCCESS', runVersion: 1, traceId: 'trace-test', occurredAt: '2026-10-17T10:00:00Z', snapshotRunId: 'run-1', model: 'test-model', promptVersion: 'charge-dispute-v1' }
