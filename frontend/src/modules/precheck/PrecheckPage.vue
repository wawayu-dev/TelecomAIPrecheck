<script setup>
import { computed, inject, nextTick, ref, watch } from 'vue'
import { precheckHostKey } from './api'
import { usePrecheck } from './usePrecheck'
import { states, recommendations, findings, failures, dateTime, money } from './labels'
import EvidencePanel from './EvidencePanel.vue'
import './precheck.css'
const props = defineProps({ ticketId: { type: String, required: true } })
const host = inject(precheckHostKey)
if (!host) throw new Error('Missing precheck host adapter')
const { context, history, selected, review, draft, loading, writing, busy, moreHistory,
  error, notice, preservedDraft, canReview, canRetry, refresh, select, loadMore, retry, submit } = usePrecheck(() => props.ticketId, host)
const source = ref('snapshot'), evidencePanel = ref(null)
watch(() => selected.value?.run.id, () => { source.value = 'snapshot' })
const evidence = computed(() => source.value === 'snapshot' && selected.value?.evidenceSnapshot ? selected.value.evidenceSnapshot : context.value)
const showingSnapshot = computed(() => source.value === 'snapshot' && !!selected.value?.evidenceSnapshot)
const ticketTitle = computed(() => ({
  'DEMO-001': '视频会员扣费争议',
  'DEMO-002': '未确认的业务订购',
  'DEMO-003': '视频会员扣费金额存在差异',
}[props.ticketId] || selected.value?.result?.summary || '投诉核查工单'))
const billAmount = computed(() => Number(evidence.value?.bills?.items?.[0]?.amount ?? selected.value?.result?.disputedAmount ?? 0))
const orderAmount = computed(() => Number(evidence.value?.subscriptions?.items?.[0]?.monthlyPrice ?? 0))
const amountDifference = computed(() => billAmount.value && orderAmount.value ? billAmount.value - orderAmount.value : null)
const aiState = computed(() => {
  const run = selected.value?.run
  if (!run) return '等待执行'
  return { SUCCESS: '已生成', FAILED: '未完成', RUNNING: '处理中', PENDING: '等待执行' }[run.callStatus] || '待处理'
})
const currentState = computed(() => {
  const run=selected.value?.run
  if(!run) return '等待工单流转'
  if(run.currentResult === false) return '历史版本'
  if(run.status === 'REVIEWED') return '已完成'
  if(run.status === 'ANALYZING') return run.taskStatus === 'RETRY_WAIT' ? '等待重试' : '核查中'
  if(run.taskStatus === 'FAILED' || run.callStatus === 'FAILED') return '转人工处理'
  return '待处理'
})
async function locate(id) { source.value = 'snapshot'; await nextTick(); evidencePanel.value?.focus(id) }
function refreshPage() {
  if (canReview.value && (draft.reason || draft.summary !== (selected.value?.result?.summary || ''))) preservedDraft.value = { ...draft }
  refresh()
}
</script>

<template>
  <main class="precheck-module reviewer-workspace" :aria-busy="busy">
    <header class="reviewer-title"><div><span>投诉核查 / {{ ticketId }}</span><h1>{{ ticketTitle }}</h1></div><strong class="role-state">{{ currentState }}</strong></header>
    <div v-if="error" role="alert" class="message error">{{ error }} <button v-if="!busy" class="text-button" @click="refreshPage">刷新数据</button></div>
    <div v-if="notice" role="status" class="message success">{{ notice }}</div>
    <div v-if="loading && !context" class="loading-panel" role="status"><span class="spinner"></span>正在读取工单材料…</div>
    <template v-else-if="context">
      <section class="complaint-summary">
        <p><span>客户反馈</span>{{ context.complaint }}</p>
        <button class="text-button" :disabled="busy" @click="refreshPage">刷新</button>
      </section>
      <div v-if="selected?.run.currentResult === false" class="message stale-note">该材料已被新版本替代，仅供历史追溯。</div>

      <section class="dispute-focus" aria-label="争议概览">
        <header><span>当前争议</span><strong>{{ currentState }}</strong></header>
        <div class="amount-comparison"><div><span>实际账单</span><b>¥{{ money(billAmount) }}</b></div><div><span>订购月费</span><b>¥{{ money(orderAmount) }}</b></div><div><span>金额差异</span><b>{{ amountDifference == null ? '待核实' : '¥' + money(Math.abs(amountDifference)) }}</b></div></div>
        <p>{{ amountDifference ? '账单金额与订购月费存在差异，差异原因尚未明确，需要核实计费依据。' : '当前未形成确定的金额异常结论，请结合账单、订购和授权凭据核实。' }}</p>
        <div v-if="selected?.findings?.length" class="focus-findings"><span v-for="(finding, index) in selected.findings" :key="index">{{ findings[finding.code] || '需要人工核查' }}</span></div>
      </section>

      <div class="reviewer-main">
        <div class="reviewer-primary">
          <section class="panel evidence-panel compact-evidence">
            <div class="panel-heading"><div><h2>核查材料</h2></div></div>
            <div v-if="selected?.evidenceSnapshot" class="source-toggle"><button :class="{ chosen: source === 'snapshot' }" @click="source = 'snapshot'">核查时快照</button><button :class="{ chosen: source === 'current' }" @click="source = 'current'">当前数据</button></div>
            <EvidencePanel ref="evidencePanel" :context="evidence" :snapshot="showingSnapshot" />
          </section>

          <section v-if="selected?.result" class="ai-assistance result-panel">
            <div class="ai-assistance-title"><h2>AI 辅助意见</h2><span>{{ aiState }}</span></div>
            <div class="ai-output compact-ai-output"><p class="content-text">{{ selected.result.summary }}</p><p class="recommendation">{{ recommendations[selected.result.recommendation] }}</p><div v-if="selected.result.evidenceIds?.length" class="evidence-links"><button v-for="id in selected.result.evidenceIds" :key="id" class="evidence-link" @click="locate(id)">{{ id }}</button></div></div>
          </section>
        </div>

        <section class="panel human-panel">
          <div class="panel-heading"><div><h2>人工核查</h2></div><span v-if="review" class="badge" :class="review.decision === 'CONFIRMED' ? 'good' : 'warning'">{{ review.decision === 'CONFIRMED' ? '已确认' : '待补充材料' }}</span></div>
          <div v-if="selected?.run.callStatus === 'FAILED'" class="processing-ai-note"><span>智能核查未完成</span><p>{{ failures[selected.run.failureCode] || '本次未生成 AI 建议' }}，请直接核对业务证据。</p><button v-if="canRetry" class="text-button" :disabled="busy" @click="retry">{{ writing ? '正在提交…' : '重新执行' }}</button></div>
          <div v-else-if="selected?.run.status === 'ANALYZING'" class="processing-ai-note"><span>智能核查处理中</span><p>系统正在准备核查建议，可先核对现有业务证据。</p></div>
          <div v-if="review && !loading" class="panel-body"><h3>核查摘要</h3><p class="content-text">{{ review.summary }}</p><h3>处理建议</h3><p>{{ recommendations[review.recommendation] }}</p><h3>核查意见</h3><p class="content-text">{{ review.reason }}</p><div class="review-signature">{{ review.actorId }} · {{ dateTime(review.reviewedAt) }}</div></div>
          <form v-else-if="canReview && !loading" class="review-form panel-body" @submit.prevent="submit('CONFIRMED')"><label for="review-summary">核查摘要 <span>必填</span></label><textarea id="review-summary" v-model="draft.summary" :disabled="busy" maxlength="1000" rows="3" required></textarea><label for="review-recommendation">处理建议</label><select id="review-recommendation" v-model="draft.recommendation" :disabled="busy"><option v-for="(label, key) in recommendations" :key="key" :value="key">{{ label }}</option></select><label for="review-reason">核查意见 <span>必填</span></label><textarea id="review-reason" v-model="draft.reason" :disabled="busy" maxlength="1000" rows="2" required></textarea><div class="review-buttons"><button class="primary" :disabled="busy">{{ writing ? '正在提交…' : '确认核查材料' }}</button><button type="button" class="secondary" :disabled="busy" @click="submit('REJECTED')">要求补充材料</button></div></form>
          <div v-else class="empty-panel compact">{{ !selected ? '等待系统核查结果。' : selected.run.status === 'ANALYZING' ? '系统仍在处理中。' : '当前材料不可编辑。' }}</div>
          <details v-if="preservedDraft" class="preserved-draft" open><summary>保留的未提交编辑</summary><p>{{ preservedDraft.summary }}</p><p>{{ preservedDraft.reason }}</p></details>
        </section>
      </div>

      <details class="history-disclosure reviewer-history">
        <summary>历史核查记录（{{ history.length }}）</summary>
        <div class="history-select"><select id="precheck-round" :value="selected?.run.id || ''" :disabled="busy || !history.length" @change="select($event.target.value)"><option v-for="item in history" :key="item.run.id" :value="item.run.id">{{ dateTime(item.run.createdAt) }} · {{ states[item.run.status] }} · {{ item.run.id.slice(0, 8) }}</option></select><button v-if="moreHistory" class="text-button" @click="loadMore('history')">加载更早记录</button></div>
      </details>
    </template>
    <footer class="module-footer">系统建议仅作为核查辅助，最终处理意见由投诉处理专员确认。</footer>
  </main>
</template>
