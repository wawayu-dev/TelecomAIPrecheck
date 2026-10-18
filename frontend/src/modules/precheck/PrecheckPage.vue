<script setup>
import { computed, inject, nextTick, ref, watch } from 'vue'
import { precheckHostKey } from './api'
import { usePrecheck } from './usePrecheck'
import { states, recommendations, findings, failures, actions, dateTime, money } from './labels'
import EvidencePanel from './EvidencePanel.vue'
import './precheck.css'
const props = defineProps({ ticketId: { type: String, required: true } })
const host = inject(precheckHostKey)
if (!host) throw new Error('Missing precheck host adapter')
const { context, history, selected, review, audit, draft, loading, writing, busy, moreHistory, moreAudit,
  error, auditError, notice, preservedDraft, canAudit, canReview, canStart, refresh, select, loadMore, start, submit } = usePrecheck(() => props.ticketId, host)
const source = ref('snapshot'), evidencePanel = ref(null)
watch(() => selected.value?.run.id, () => { source.value = 'snapshot' })
const evidence = computed(() => source.value === 'snapshot' && selected.value?.evidenceSnapshot ? selected.value.evidenceSnapshot : context.value)
const showingSnapshot = computed(() => source.value === 'snapshot' && !!selected.value?.evidenceSnapshot)
const currentState = computed(() => selected.value ? states[selected.value.run.status] : '尚未预审')
const hasOperator = computed(() => host.identity.value?.roles.includes('ROLE_OPERATOR'))
const isReviewer = computed(() => host.identity.value?.roles.includes('ROLE_REVIEWER'))
async function locate(id) { source.value = 'snapshot'; await nextTick(); evidencePanel.value?.focus(id) }
function refreshPage() {
  // 刷新保留用户草稿供核对，避免静默丢失人工编辑。
  if (canReview.value && (draft.reason || draft.summary !== (selected.value?.result?.summary || ''))) preservedDraft.value = { ...draft }
  refresh()
}
</script>
<template>
  <main class="precheck-module" :aria-busy="busy">
    <div class="page-heading"><div><p class="eyebrow">工单处理 / AI 辅助预审</p><h1>增值业务扣费争议 <span class="ticket-number">{{ ticketId }}</span></h1><p class="page-description">核对业务证据，整理预审建议，由人工完成最终复核。</p></div><span class="badge state-badge" :class="selected?.run.status === 'REVIEWED' ? 'good' : 'info'">{{ currentState }}</span></div>
    <div class="workflow-strip" aria-label="处理流程"><span :class="{ active: context }"><b>01</b> 读取工单证据</span><i>—</i><span :class="{ active: selected }"><b>02</b> AI 辅助预审</span><i>—</i><span :class="{ active: review }"><b>03</b> 人工复核留痕</span><small>建议不直接触发退费</small></div>
    <div v-if="error" role="alert" class="message error">{{ error }} <button v-if="!busy" class="text-button" @click="refreshPage">刷新数据</button></div>
    <div v-if="notice" role="status" class="message success">{{ notice }}</div>
    <div class="workspace-toolbar"><div class="history-select"><label for="precheck-round">预审记录</label><select id="precheck-round" :value="selected?.run.id || ''" :disabled="busy || !history.length" @change="select($event.target.value)"><option value="" disabled>{{ loading ? '正在读取…' : '尚无记录' }}</option><option v-if="selected && !history.some(item => item.run.id === selected.run.id)" :value="selected.run.id">{{ dateTime(selected.run.createdAt) }} · {{ states[selected.run.status] }} · {{ selected.run.id.slice(0, 8) }}</option><option v-for="item in history" :key="item.run.id" :value="item.run.id">{{ dateTime(item.run.createdAt) }} · {{ states[item.run.status] }} · {{ item.run.id.slice(0, 8) }}</option></select><button v-if="moreHistory" class="text-button" :disabled="busy" @click="loadMore('history')">更早记录</button></div><div class="toolbar-actions"><button class="secondary" :disabled="busy" @click="refreshPage">刷新</button><button v-if="hasOperator" class="primary" :disabled="busy || !canStart" @click="start">{{ writing ? '正在预审…' : history.length ? '发起新一轮预审' : '开始 AI 预审' }}</button></div></div>
    <p v-if="hasOperator && context && !canStart && !loading" class="muted toolbar-hint">已有进行中或待复核记录，请先完成当前轮次的人工处理。</p>
    <div v-if="loading && !context" class="loading-panel" role="status"><span class="spinner"></span>正在读取宿主工单与历史记录…</div>
    <div v-else-if="context" class="work-grid">
      <section class="panel evidence-panel"><div class="panel-heading"><div><span class="section-index">01 / EVIDENCE</span><h2>工单与业务证据</h2></div><span v-if="context.synthetic" class="badge neutral">合成数据</span></div>
        <div v-if="selected?.evidenceSnapshot" class="source-toggle" aria-label="证据来源"><button :class="{ chosen: source === 'snapshot' }" :aria-pressed="source === 'snapshot'" @click="source = 'snapshot'">本轮证据快照</button><button :class="{ chosen: source === 'current' }" :aria-pressed="source === 'current'" @click="source = 'current'">宿主当前数据</button></div>
        <p v-if="selected && !showingSnapshot" class="inline-note source-warning">正在查看宿主当前数据；核验本轮 AI 引用请切回证据快照。</p>
        <EvidencePanel ref="evidencePanel" :context="evidence" :snapshot="showingSnapshot" />
      </section>
      <div class="review-column">
        <section class="panel result-panel"><div class="panel-heading"><div><span class="section-index">02 / PRECHECK</span><h2>AI 预审结果</h2></div><span v-if="selected" class="badge" :class="selected.run.callStatus === 'SUCCESS' ? 'good' : 'warning'">{{ { SUCCESS: '预审成功', FAILED: '已转人工', RUNNING: '处理中' }[selected.run.callStatus] }}</span></div>
          <div v-if="loading" class="empty-panel" role="status">正在加载本轮记录…</div>
          <div v-else-if="!selected" class="empty-panel"><div class="empty-symbol">✧</div><h3>让证据成为处理依据</h3><p>发起预审后，这里将展示结构化摘要、证据引用和处理建议。</p><small>{{ hasOperator ? '请点击上方「开始 AI 预审」' : '等待操作员发起预审' }}</small></div>
          <div v-else class="panel-body">
            <div v-if="selected.run.status === 'ANALYZING'" class="inline-note" role="status">预审正在处理，页面每 4 秒更新状态。离开页面不会取消已提交的操作。</div>
            <div v-if="selected.run.callStatus === 'FAILED'" class="inline-note">{{ failures[selected.run.failureCode] || '预审未完成' }}。工单与证据已保留，可以继续人工复核。</div>
            <div v-if="selected.findings?.length" class="findings"><h3>规则核验提示</h3><div v-for="(finding, index) in selected.findings" :key="index" class="finding"><span class="finding-dot"></span><div>{{ findings[finding.code] || '需要人工核查' }}<div class="evidence-links"><button v-for="id in finding.evidenceIds" :key="id" class="evidence-link" @click="locate(id)">{{ id }} ↗</button></div></div></div></div>
            <div v-if="selected.result" class="ai-output"><div class="result-facts"><div><span>投诉类型</span><strong>{{ { VALUE_ADDED_CHARGE_DISPUTE: '增值业务扣费争议', OTHER: '其他投诉', UNCLEAR: '待进一步澄清' }[selected.result.complaintType] }}</strong></div><div><span>争议金额</span><strong>{{ selected.result.disputedAmount == null ? '未提取' : '¥ ' + money(selected.result.disputedAmount) }}</strong></div></div><h3>标准化摘要</h3><p class="content-text">{{ selected.result.summary }}</p><h3>建议处理方向</h3><p class="recommendation">{{ recommendations[selected.result.recommendation] }}</p><h3>引用证据</h3><div class="evidence-links"><button v-for="id in selected.result.evidenceIds" :key="id" class="evidence-link" @click="locate(id)">{{ id }} ↗</button><span v-if="!selected.result.evidenceIds?.length" class="muted">无证据引用</span></div></div>
            <details class="technical-details"><summary>查看本轮溯源信息</summary><dl class="compact-dl"><dt>模型</dt><dd>{{ selected.run.actualModel || selected.run.requestedModel }}</dd><dt>Prompt 版本</dt><dd>{{ selected.run.promptVersion }}</dd><dt>调用耗时</dt><dd>{{ selected.run.durationMs == null ? '—' : selected.run.durationMs + ' ms' }}</dd><dt>记录 / 版本</dt><dd>{{ selected.run.id }} / v{{ selected.run.version }}</dd><dt>Trace ID</dt><dd>{{ selected.run.traceId }}</dd></dl></details>
          </div>
        </section>
        <section class="panel human-panel"><div class="panel-heading"><div><span class="section-index">03 / HUMAN REVIEW</span><h2>人工复核</h2></div><span v-if="review" class="badge" :class="review.decision === 'CONFIRMED' ? 'good' : 'warning'">{{ review.decision === 'CONFIRMED' ? '已确认' : '已驳回' }}</span></div>
          <div v-if="review && !loading" class="panel-body"><h3>人工确认后的摘要</h3><p class="content-text">{{ review.summary }}</p><h3>处理建议</h3><p>{{ recommendations[review.recommendation] }}</p><h3>复核依据</h3><p class="content-text">{{ review.reason }}</p><div class="review-signature">{{ review.actorId }} · {{ dateTime(review.reviewedAt) }}<br>审核版本 v{{ review.version }} · 已留存审计</div></div>
          <form v-else-if="canReview && !loading" class="review-form panel-body" @submit.prevent="submit('CONFIRMED')"><p class="muted">请结合左侧证据修订内容。确认仅保存处理建议，不执行退费。</p><label for="review-summary">人工摘要 <span>必填 · 最多 1000 字</span></label><textarea id="review-summary" v-model="draft.summary" :disabled="busy" maxlength="1000" rows="4" required placeholder="核对投诉与业务证据，填写人工摘要"></textarea><label for="review-recommendation">处理建议</label><select id="review-recommendation" v-model="draft.recommendation" :disabled="busy"><option v-for="(label, key) in recommendations" :key="key" :value="key">{{ label }}</option></select><label for="review-reason">复核依据 <span>必填 · 最多 1000 字</span></label><textarea id="review-reason" v-model="draft.reason" :disabled="busy" maxlength="1000" rows="3" required placeholder="说明确认或驳回的依据"></textarea><div class="review-buttons"><button type="button" class="danger-outline" :disabled="busy" @click="submit('REJECTED')">驳回预审</button><button class="primary" :disabled="busy">{{ writing ? '正在提交…' : '确认处理建议' }}</button></div></form>
          <div v-else class="empty-panel compact"><p>{{ loading ? '正在读取复核状态…' : !selected ? '预审完成或转人工后，可在此复核。' : selected.run.status === 'ANALYZING' ? '预审仍在处理中，暂不可审核。' : !isReviewer ? '当前身份只读，请由审核员完成复核。' : '当前记录不可审核，请刷新状态。' }}</p></div>
          <details v-if="preservedDraft" class="preserved-draft" open><summary>保留的未提交编辑</summary><p>{{ preservedDraft.summary }}</p><p>{{ recommendations[preservedDraft.recommendation] }}</p><p>{{ preservedDraft.reason }}</p></details>
        </section>
      </div>
      <section class="panel audit-panel"><div class="panel-heading"><div><span class="section-index">TRACEABILITY</span><h2>业务操作时间线</h2></div><span class="muted">{{ canAudit ? '按本轮操作顺序展示' : '需要审核或审计权限' }}</span></div>
        <div v-if="!canAudit" class="empty-panel compact">审计时间线仅向审核员与审计员开放。</div>
        <div v-else-if="auditError" role="alert" class="message error">{{ auditError }} <button class="text-button" :disabled="busy" @click="refreshPage">重新读取</button></div>
        <ol v-else-if="audit.length" class="timeline"><li v-for="event in audit" :key="event.id"><div class="timeline-dot"></div><div class="timeline-main"><div class="section-line"><strong>{{ actions[event.action] || event.action }}</strong><time>{{ dateTime(event.occurredAt) }}</time></div><p>{{ event.actorId }} <span>·</span> {{ event.fromStatus ? states[event.fromStatus] + ' → ' : '' }}{{ states[event.toStatus] }} <span>·</span> v{{ event.runVersion }} <span>·</span> {{ { COMMITTED: '已提交', SUCCESS: '成功', FAILED: '失败转人工', IMPORTED: '已导入' }[event.outcome] || event.outcome }}</p><details><summary>操作追踪信息</summary><dl class="compact-dl"><dt>Trace ID</dt><dd>{{ event.traceId }}</dd><dt>证据快照</dt><dd>{{ event.snapshotRunId }}</dd><dt>模型 / Prompt</dt><dd>{{ event.model }} / {{ event.promptVersion }}</dd></dl></details></div></li></ol>
        <div v-else class="empty-panel compact">{{ loading ? '正在读取操作记录…' : '本轮暂无可展示的审计事件。' }}</div><button v-if="moreAudit" class="text-button audit-more" :disabled="busy" @click="loadMore('audit')">继续加载事件</button>
      </section>
    </div>
    <footer class="module-footer">AI 仅辅助理解、整理与建议。授权凭据缺失不直接构成违规认定，最终处理由业务人员负责。</footer>
  </main>
</template>
