<script setup>
import { computed, inject, nextTick, ref, watch } from 'vue'
import { precheckHostKey } from './api'
import { usePrecheck } from './usePrecheck'
import { states, findings, failures, dateTime, money } from './labels'
import EvidencePanel from './EvidencePanel.vue'
import './precheck.css'

const props = defineProps({ ticketId: { type: String, required: true } })
const emit = defineEmits(['workflow-change'])
const host = inject(precheckHostKey)
if (!host) throw new Error('Missing precheck host adapter')
const { context, history, selected, review, loading, writing, busy, moreHistory,
  error, notice, canRetry, refresh, select, loadMore, retry } = usePrecheck(() => props.ticketId, host)

const source = ref('current'), evidencePanel = ref(null)
const historyTrace = ref({}), historyLoading = ref(false)
watch(() => selected.value?.run.id, () => { source.value = selected.value?.run.currentResult === false ? 'snapshot' : 'current' })
const evidence = computed(() => source.value === 'snapshot' && selected.value?.evidenceSnapshot ? selected.value.evidenceSnapshot : context.value)
const showingSnapshot = computed(() => source.value === 'snapshot' && !!selected.value?.evidenceSnapshot)
const evidenceSections = ['bills', 'subscriptions', 'authorizations']
function fingerprint(data, key) {
  const section = data?.[key] || {}
  return JSON.stringify({ status: section.status, items: section.items || [] })
}
function changedEvidence(left, right) {
  if (!left || !right) return []
  return evidenceSections.filter(key => fingerprint(left, key) !== fingerprint(right, key))
}
const evidenceChanges = computed(() => changedEvidence(selected.value?.evidenceSnapshot, context.value))
const comparingHistory = computed(() => selected.value?.run.currentResult === false)
const comparisonAvailable = computed(() => comparingHistory.value || evidenceChanges.value.length > 0)
const evidenceVersionStatus = computed(() => {
  if (comparingHistory.value) return '正在查看历史核查所依据的证据快照'
  if (!selected.value?.evidenceSnapshot) return '当前核查尚未生成证据快照'
  if (!evidenceChanges.value.length) return '与本次证据快照一致'
  return `发现 ${evidenceChanges.value.length} 类数据变更`
})

const ticketTitle = computed(() => ({
  'DEMO-001': '视频会员扣费争议', 'DEMO-002': '未确认的业务订购', 'DEMO-003': '视频会员扣费金额存在差异',
}[props.ticketId] || selected.value?.result?.summary || '投诉核查工单'))
const billAmount = computed(() => Number(evidence.value?.bills?.items?.[0]?.amount ?? selected.value?.result?.disputedAmount ?? 0))
const orderAmount = computed(() => Number(evidence.value?.subscriptions?.items?.[0]?.monthlyPrice ?? 0))
const amountDifference = computed(() => billAmount.value && orderAmount.value ? billAmount.value - orderAmount.value : null)
const disputeFocus = computed(() => evidence.value?.authorizations?.status === 'AVAILABLE'
  ? '客户否认主动订购视频会员，需核实授权是否真实、有效并可归属于客户本人。'
  : '客户否认主动订购视频会员，当前缺少足以证明主动订购的授权证据。')
const pricingCheck = computed(() => {
  if (amountDifference.value == null) return '账单或订购数据不完整，计价关系暂无法核对。'
  if (amountDifference.value !== 0) return '账单金额与订购月费存在差异，需另行核实账期、优惠和计费规则。'
  return '账单与订购价格一致；金额核对通过不代表订购授权有效。'
})
const aiGuidance = computed(() => {
  const result = selected.value?.result
  if (!result) return null
  const hasBill = !!evidence.value?.bills?.items?.length
  const hasOrder = !!evidence.value?.subscriptions?.items?.length
  const authorizationStatus = evidence.value?.authorizations?.status
  const verifiedFinding = hasBill && hasOrder && amountDifference.value === 0
    ? `账单金额 ${money(billAmount.value)} 元，与视频会员订购月费一致，未发现计价差异。`
    : hasBill && hasOrder && amountDifference.value != null
      ? `账单金额与订购价格相差 ${money(Math.abs(amountDifference.value))} 元，差异原因尚未查明。`
      : '现有材料尚不足以完成账单与订购关系核对。'
  const evidenceGap = result.evidenceGap || (authorizationStatus === 'AVAILABLE'
    ? '系统存在授权凭据，但仅凭记录存在，尚不能确认客户主动订购。'
    : authorizationStatus === 'NOT_FOUND'
      ? '未检索到关联授权凭证，无法证明客户主动订购；记录缺失本身不能作为违规结论。'
      : '授权凭证当前不可查询，尚无法判断客户是否主动订购。')
  const suggestedAction = result.suggestedAction || (result.recommendation === 'VERIFY_BILLING'
    ? '核对计费周期、优惠规则、账单科目及其与订购流水的关联关系。'
    : '核对授权验证结果、发生时间及其与订购流水的关联关系；源系统未提供时应明确记录证据不足。')
  const title = authorizationStatus === 'AVAILABLE' ? '授权有效性待核实' : '主动订购证据不足'
  const evidenceIds = [...new Set([...(result.evidenceIds || []),
    ...(evidence.value?.bills?.items || []).map(item => item.evidenceId),
    ...(evidence.value?.subscriptions?.items || []).map(item => item.evidenceId),
    ...(evidence.value?.authorizations?.items || []).map(item => item.evidenceId)].filter(Boolean))]
  return { title, verifiedFinding, evidenceGap, suggestedAction, evidenceIds }
})
const initialConclusion = computed(() => {
  if (!aiGuidance.value) return ''
  const authorizationStatus = evidence.value?.authorizations?.status
  if (amountDifference.value === 0 && authorizationStatus === 'NOT_FOUND') return '当前未发现计价异常，主动订购授权依据不足。'
  if (amountDifference.value === 0 && authorizationStatus === 'AVAILABLE') return '当前未发现计价异常，订购授权的有效性仍待核实。'
  if (amountDifference.value != null && amountDifference.value !== 0) return '账单金额与订购价格存在差异，差异原因及订购授权均待核实。'
  return '现有材料尚不足以形成明确结论，需继续核实计价及订购授权。'
})
const handlingSuggestion = computed(() => {
  if (!aiGuidance.value) return ''
  const authorizationStatus = evidence.value?.authorizations?.status
  if (amountDifference.value === 0 && authorizationStatus === 'NOT_FOUND') {
    return `客户投诉未经确认订购视频会员并产生${money(billAmount.value)}元费用。核查发现账单与订购金额一致，但当前未查询到关联授权凭据，尚不能确认是否经过客户有效授权。建议进一步核实授权记录及与订购流水的对应关系，并依据后续核查结果按原有流程处理。`
  }
  return `${aiGuidance.value.verifiedFinding}${aiGuidance.value.evidenceGap}建议${aiGuidance.value.suggestedAction}并依据后续核查结果按原有流程处理。`
})
const suggestionText = computed(() => {
  const ids = aiGuidance.value?.evidenceIds || []
  return `初步调查结论\n${initialConclusion.value}\n\n建议处理意见\n${handlingSuggestion.value}${ids.length ? `\n\n关联证据\n${ids.join('、')}` : ''}`
})
const editingSuggestion = ref(false)
const editedSuggestion = ref('')
watch(() => selected.value?.run.id, () => {
  editingSuggestion.value = false
  editedSuggestion.value = ''
})
function beginEditingSuggestion() {
  editedSuggestion.value = suggestionText.value
  editingSuggestion.value = true
}
async function copySuggestion(text = suggestionText.value) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const helper = document.createElement('textarea')
      helper.value = text
      helper.style.position = 'fixed'
      helper.style.opacity = '0'
      document.body.appendChild(helper)
      helper.select()
      const copied = document.execCommand?.('copy')
      helper.remove()
      if (!copied) throw new Error('Clipboard unavailable')
    }
    notice.value = 'AI 处理建议已复制，可粘贴到原工单系统。'
  } catch {
    error.value = '复制失败，请进入编辑模式后手动选择文本。'
  }
}
const currentState = computed(() => {
  const run = selected.value?.run
  if (!run) return '等待工单流转'
  if (run.currentResult === false) return '历史版本'
  if (run.status === 'REVIEWED') return review.value?.decision === 'REJECTED' ? '待补证' : review.value?.decision === 'ESCALATED' ? '争议复核中' : '已完成'
  if (run.status === 'ANALYZING') return run.taskStatus === 'RETRY_WAIT' ? '等待重试' : '核查中'
  if (run.taskStatus === 'FAILED' || run.callStatus === 'FAILED') return '转人工处理'
  return '待处理'
})
watch(() => selected.value?.run.status, (status, previous) => {
  if (previous && status && status !== previous) emit('workflow-change', { ticketId: props.ticketId })
})

async function locate(id) { source.value = 'snapshot'; await nextTick(); evidencePanel.value?.focus(id) }
function refreshPage() {
  refresh()
}

async function loadHistoryTrace(event) {
  if (!event.target.open || historyLoading.value) return
  const missing = history.value.filter(item => !historyTrace.value[item.run.id])
  if (!missing.length) return
  historyLoading.value = true
  const entries = await Promise.all(missing.map(async item => {
    try {
      const [detail, savedReview] = await Promise.all([host.api.run(item.run.id), host.api.review(item.run.id)])
      return [item.run.id, { detail, review: savedReview }]
    } catch { return [item.run.id, { detail: item, review: null }] }
  }))
  historyTrace.value = { ...historyTrace.value, ...Object.fromEntries(entries) }
  historyLoading.value = false
}
function traceFor(item) { return historyTrace.value[item.run.id] || { detail: item, review: selected.value?.run.id === item.run.id ? review.value : null } }
function triggerReason(item) { return item.run.triggerType === 'MANUAL' ? '人工发起重新核查' : item.run.eventId ? '工单材料或状态变化触发' : '系统自动预审' }
function historyEvidenceChange(item, index) {
  const current = traceFor(item).detail?.evidenceSnapshot
  const previousItem = history.value[index + 1]
  const previous = previousItem ? traceFor(previousItem).detail?.evidenceSnapshot : null
  if (!current) return '读取详情后显示当时证据版本'
  if (!previous) return '首个可追溯证据版本'
  const changes = changedEvidence(current, previous)
  const names = { bills: '账单', subscriptions: '订购', authorizations: '授权' }
  return changes.length ? `${changes.map(key => names[key]).join('、')}数据发生变化` : '较上一轮未发现证据变化'
}
function historyConclusion(item) {
  const saved = traceFor(item).review
  if (saved?.decision === 'CONFIRMED') return '人工核查结论已提交'
  if (saved?.decision === 'REJECTED') return '证据不足，已要求补证'
  if (saved?.decision === 'ESCALATED') return '已转交争议复核'
  return states[item.run.status] || '等待处理'
}
</script>

<template>
  <main class="precheck-module reviewer-workspace" :aria-busy="busy">
    <header class="reviewer-title"><div><span>投诉核查 / {{ ticketId }}</span><h1>{{ ticketTitle }}</h1></div><strong class="role-state">{{ currentState }}</strong></header>
    <div v-if="error" role="alert" class="message error">{{ error }} <button v-if="!busy" class="text-button" @click="refreshPage">刷新数据</button></div>
    <div v-if="notice" role="status" class="message success">{{ notice }}</div>
    <div v-if="loading && !context" class="loading-panel" role="status"><span class="spinner"></span>正在读取工单材料…</div>
    <template v-else-if="context">
      <section class="complaint-summary"><p><span>客户反馈</span>{{ context.complaint }}</p><button class="text-button" :disabled="busy" @click="refreshPage">刷新</button></section>
      <div v-if="selected?.run.currentResult === false" class="message stale-note">该材料已被新版本替代，仅供历史追溯。</div>

      <section class="dispute-focus" aria-label="争议概览">
        <header><span>争议焦点</span><strong>{{ currentState }}</strong></header>
        <h2>{{ disputeFocus }}</h2>
        <div class="amount-comparison"><div><span>实际账单</span><b>¥{{ money(billAmount) }}</b></div><div><span>订购月费</span><b>¥{{ money(orderAmount) }}</b></div><div><span>规则核对</span><b>{{ amountDifference == null ? '待核实' : amountDifference === 0 ? '计价一致' : `差异 ¥${money(Math.abs(amountDifference))}` }}</b></div></div>
        <p>{{ pricingCheck }}</p>
        <div v-if="selected?.findings?.length" class="focus-findings"><span v-for="(finding, index) in selected.findings" :key="index">{{ findings[finding.code] || '需要人工核查' }}</span></div>
      </section>

      <div class="reviewer-main">
        <div class="reviewer-primary">
          <section class="panel evidence-panel compact-evidence">
            <div class="panel-heading"><div><h2>当前核查材料</h2></div><span class="evidence-sync" :class="{ changed: evidenceChanges.length }">{{ evidenceVersionStatus }}</span></div>
            <div class="snapshot-context"><span>本次核查 {{ selected?.run.id?.slice(0, 8) || '—' }}</span><span>快照生成于 {{ dateTime(selected?.evidenceSnapshot?.capturedAt || selected?.run.createdAt) }}</span></div>
            <details v-if="comparisonAvailable" class="evidence-compare-disclosure">
              <summary>{{ evidenceChanges.length ? '展开查看变更前后证据' : '展开历史证据对照' }}</summary>
              <div class="source-toggle"><button :class="{ chosen: source === 'current' }" @click="source = 'current'">当前数据</button><button :class="{ chosen: source === 'snapshot' }" @click="source = 'snapshot'">本次证据快照</button></div>
            </details>
            <EvidencePanel ref="evidencePanel" :context="evidence" :snapshot="showingSnapshot" />
          </section>

        </div>

        <section class="panel human-panel suggestion-panel">
          <div class="panel-heading"><div><h2>AI 辅助处理建议</h2></div><span class="ai-reference-badge">AI 生成 · 仅供参考</span></div>
          <div v-if="selected?.run.callStatus === 'FAILED'" class="processing-ai-note"><span>智能核查未完成</span><p>{{ failures[selected.run.failureCode] || '本次未生成 AI 建议' }}，请直接核对业务证据。</p><button v-if="canRetry" class="text-button" :disabled="busy" @click="retry">{{ writing ? '正在提交…' : '重新执行' }}</button></div>
          <div v-else-if="selected?.run.status === 'ANALYZING'" class="processing-ai-note"><span>智能核查处理中</span><p>系统正在准备核查建议，可先核对现有业务证据。</p></div>
          <div v-if="selected?.result && !loading" class="panel-body ai-suggestion-body">
            <template v-if="!editingSuggestion">
              <section><h3>初步调查结论</h3><p class="suggestion-conclusion">{{ initialConclusion }}</p></section>
              <section><h3>建议处理意见</h3><p class="content-text">{{ handlingSuggestion }}</p></section>
              <section v-if="aiGuidance.evidenceIds.length"><h3>关联证据</h3><div class="evidence-links"><button v-for="id in aiGuidance.evidenceIds" :key="id" class="evidence-link" @click="locate(id)">{{ id }}</button></div></section>
              <div class="suggestion-actions"><button class="primary" type="button" @click="copySuggestion()">复制 AI 处理建议</button><button class="secondary" type="button" @click="beginEditingSuggestion">引用并编辑</button></div>
            </template>
            <template v-else>
              <label for="ai-suggestion-editor">人工编辑草稿</label>
              <textarea id="ai-suggestion-editor" v-model="editedSuggestion" rows="16" maxlength="3000" aria-describedby="suggestion-editor-hint"></textarea>
              <small id="suggestion-editor-hint">可修改、清空或重新撰写；内容仅保存在当前页面，不会提交工单。</small>
              <div class="suggestion-actions"><button class="primary" type="button" @click="copySuggestion(editedSuggestion)">复制编辑后内容</button><button class="secondary" type="button" @click="editingSuggestion = false">返回 AI 建议</button></div>
            </template>
            <p class="suggestion-boundary">最终核实、业务操作及投诉办结均由原有工单系统和有权限的业务人员负责。</p>
            <p class="demo-note">交互示意：复制与编辑功能可体验，不涉及真实工单提交。</p>
          </div>
          <div v-else-if="!loading && selected?.run.status !== 'ANALYZING'" class="empty-panel compact">当前没有可用的 AI 处理建议，请以原工单材料为准。</div>
        </section>
      </div>

      <details class="history-disclosure reviewer-history" @toggle="loadHistoryTrace">
        <summary>AI 核查历史（{{ history.length }}）<small>关联展示人工核查结论；操作审计日志独立留存</small></summary>
        <div v-if="historyLoading" class="history-loading">正在读取各轮证据版本与人工结论…</div>
        <div class="history-cards">
          <article v-for="(item, index) in history" :key="item.run.id" :class="{ active: selected?.run.id === item.run.id }">
            <header><div><time>{{ dateTime(item.run.createdAt) }}</time><strong>{{ historyConclusion(item) }}</strong></div><button class="text-button" :disabled="busy" @click="select(item.run.id)">查看当时证据</button></header>
            <dl><dt>记录类型</dt><dd>AI 核查历史</dd><dt>触发原因</dt><dd>{{ triggerReason(item) }}</dd><dt>证据变化</dt><dd>{{ historyEvidenceChange(item, index) }}</dd><dt>核查结论</dt><dd>{{ historyConclusion(item) }}</dd><dt>处理人</dt><dd>{{ traceFor(item).review?.actorId || (item.run.status === 'REVIEWED' ? '读取中…' : '待人工处理') }}</dd></dl>
            <small>证据版本 {{ item.run.id.slice(0, 8) }} · {{ states[item.run.status] }}</small>
          </article>
        </div>
        <button v-if="moreHistory" class="text-button" @click="loadMore('history')">加载更早记录</button>
      </details>
    </template>
    <footer class="module-footer">本模块仅提供材料聚合与 AI 辅助建议，不负责投诉审批、退费或最终办结。</footer>
  </main>
</template>
