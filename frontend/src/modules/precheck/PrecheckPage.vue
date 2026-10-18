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
    <header class="role-page-heading reviewer-heading"><div><span>核查待办</span><h1>{{ ticketId }} · {{ context?.complaint || '投诉工单' }}</h1></div><strong class="role-state">{{ currentState }}</strong></header>
    <div v-if="error" role="alert" class="message error">{{ error }} <button v-if="!busy" class="text-button" @click="refreshPage">刷新数据</button></div>
    <div v-if="notice" role="status" class="message success">{{ notice }}</div>
    <div v-if="loading && !context" class="loading-panel" role="status"><span class="spinner"></span>正在读取工单材料…</div>
    <template v-else-if="context">
      <section class="reviewer-overview">
        <div><span>客户诉求</span><p>{{ context.complaint }}</p></div>
        <div><span>系统状态</span><strong>{{ currentState }}</strong><small>核查任务由工单流转自动触发</small></div>
        <button class="secondary" :disabled="busy" @click="refreshPage">刷新</button>
      </section>

      <div class="reviewer-grid">
        <section class="panel result-panel">
          <div class="panel-heading"><div><span class="section-index">系统辅助</span><h2>核查建议</h2></div><span v-if="selected" class="badge" :class="selected.run.callStatus === 'SUCCESS' ? 'good' : selected.run.callStatus === 'FAILED' ? 'warning' : 'info'">{{ { SUCCESS: '已生成', FAILED: '未完成', RUNNING: '处理中', PENDING: '等待执行' }[selected.run.callStatus] }}</span></div>
          <div v-if="loading" class="empty-panel compact">正在更新核查结果…</div>
          <div v-else-if="!selected" class="empty-panel"><h3>等待工单进入处理队列</h3><p>工单流转后，核查结果会自动出现在这里。</p></div>
          <div v-else class="panel-body">
            <div v-if="selected.run.currentResult === false" class="inline-note stale-note">该材料已被新版本替代，仅供查看。</div>
            <div v-if="selected.run.status === 'ANALYZING'" class="inline-note">系统正在准备核查材料，原工单仍可继续人工处理。</div>
            <div v-if="selected.run.callStatus === 'FAILED'" class="inline-note"><div>{{ failures[selected.run.failureCode] || '系统核查未完成' }}，可直接进行人工核查。</div><button v-if="canRetry" class="retry-button" :disabled="busy" @click="retry">{{ writing ? '正在提交…' : '重新执行系统核查' }}</button></div>
            <div v-if="selected.result" class="ai-output">
              <div class="result-facts"><div><span>投诉类型</span><strong>{{ { VALUE_ADDED_CHARGE_DISPUTE: '增值业务扣费争议', OTHER: '其他投诉', UNCLEAR: '待进一步澄清' }[selected.result.complaintType] }}</strong></div><div><span>争议金额</span><strong>{{ selected.result.disputedAmount == null ? '未提取' : '¥ ' + money(selected.result.disputedAmount) }}</strong></div></div>
              <h3>投诉摘要</h3><p class="content-text">{{ selected.result.summary }}</p>
              <h3>建议处理方向</h3><p class="recommendation">{{ recommendations[selected.result.recommendation] }}</p>
            </div>
            <div v-if="selected.findings?.length" class="key-findings"><h3>需要关注</h3><div v-for="(finding, index) in selected.findings" :key="index"><span>{{ findings[finding.code] || '需要人工核查' }}</span><button v-for="id in finding.evidenceIds" :key="id" class="evidence-link" @click="locate(id)">{{ id }}</button></div></div>
          </div>
        </section>

        <section class="panel evidence-panel compact-evidence">
          <div class="panel-heading"><div><span class="section-index">关键证据</span><h2>业务凭据</h2></div><span v-if="context.synthetic" class="badge neutral">演示数据</span></div>
          <div v-if="selected?.evidenceSnapshot" class="source-toggle"><button :class="{ chosen: source === 'snapshot' }" @click="source = 'snapshot'">核查时快照</button><button :class="{ chosen: source === 'current' }" @click="source = 'current'">当前数据</button></div>
          <EvidencePanel ref="evidencePanel" :context="evidence" :snapshot="showingSnapshot" />
        </section>

        <section class="panel human-panel">
          <div class="panel-heading"><div><span class="section-index">人工处理</span><h2>核查结论</h2></div><span v-if="review" class="badge" :class="review.decision === 'CONFIRMED' ? 'good' : 'warning'">{{ review.decision === 'CONFIRMED' ? '已确认' : '待补充材料' }}</span></div>
          <div v-if="review && !loading" class="panel-body"><h3>核查摘要</h3><p class="content-text">{{ review.summary }}</p><h3>处理建议</h3><p>{{ recommendations[review.recommendation] }}</p><h3>核查意见</h3><p class="content-text">{{ review.reason }}</p><div class="review-signature">{{ review.actorId }} · {{ dateTime(review.reviewedAt) }}</div></div>
          <form v-else-if="canReview && !loading" class="review-form panel-body" @submit.prevent="submit('CONFIRMED')"><label for="review-summary">核查摘要 <span>必填</span></label><textarea id="review-summary" v-model="draft.summary" :disabled="busy" maxlength="1000" rows="4" required></textarea><label for="review-recommendation">处理建议</label><select id="review-recommendation" v-model="draft.recommendation" :disabled="busy"><option v-for="(label, key) in recommendations" :key="key" :value="key">{{ label }}</option></select><label for="review-reason">核查意见 <span>必填</span></label><textarea id="review-reason" v-model="draft.reason" :disabled="busy" maxlength="1000" rows="3" required></textarea><div class="review-buttons"><button type="button" class="danger-outline" :disabled="busy" @click="submit('REJECTED')">要求补充材料</button><button class="primary" :disabled="busy">{{ writing ? '正在提交…' : '确认核查材料' }}</button></div></form>
          <div v-else class="empty-panel compact">{{ !selected ? '等待系统核查结果。' : selected.run.status === 'ANALYZING' ? '系统仍在处理中。' : '当前材料不可编辑。' }}</div>
          <details v-if="preservedDraft" class="preserved-draft" open><summary>保留的未提交编辑</summary><p>{{ preservedDraft.summary }}</p><p>{{ preservedDraft.reason }}</p></details>
        </section>
      </div>

      <details class="history-disclosure">
        <summary>查看历史核查记录（{{ history.length }}）</summary>
        <div class="history-select"><select id="precheck-round" :value="selected?.run.id || ''" :disabled="busy || !history.length" @change="select($event.target.value)"><option v-for="item in history" :key="item.run.id" :value="item.run.id">{{ dateTime(item.run.createdAt) }} · {{ states[item.run.status] }} · {{ item.run.id.slice(0, 8) }}</option></select><button v-if="moreHistory" class="text-button" @click="loadMore('history')">加载更早记录</button></div>
      </details>
    </template>
    <footer class="module-footer">系统建议仅作为核查辅助，最终处理意见由投诉处理专员确认。</footer>
  </main>
</template>
