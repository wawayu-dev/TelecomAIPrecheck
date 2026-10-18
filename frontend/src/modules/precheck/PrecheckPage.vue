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
  error, auditError, notice, preservedDraft, canAudit, canReview, canRetry, refresh, select, loadMore, retry, submit } = usePrecheck(() => props.ticketId, host)
const source = ref('snapshot'), evidencePanel = ref(null)
watch(() => selected.value?.run.id, () => { source.value = 'snapshot' })
const evidence = computed(() => source.value === 'snapshot' && selected.value?.evidenceSnapshot ? selected.value.evidenceSnapshot : context.value)
const showingSnapshot = computed(() => source.value === 'snapshot' && !!selected.value?.evidenceSnapshot)
const currentState = computed(() => {
  const run=selected.value?.run
  if(!run) return '等待自动预处理'
  if(run.currentResult === false) return '历史结果 · 已过期'
  if(run.status === 'REVIEWED') return '核查材料已确认'
  if(run.status === 'ANALYZING') return run.taskStatus === 'RETRY_WAIT' ? '等待自动重试' : 'AI 核查材料准备中'
  if(run.taskStatus === 'FAILED' || run.callStatus === 'FAILED') return 'AI 未完成 · 可人工处理'
  return '核查材料待确认'
})
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
    <div class="page-heading"><div><p class="eyebrow">投诉处理 / 接单即用</p><h1>AI 核查工作台 <span class="ticket-number">{{ ticketId }}</span></h1><p class="page-description">原始工单与 AI 核查材料同步呈现，专员只需核对、修正并确认。</p></div><span class="badge state-badge" :class="selected?.run.status === 'REVIEWED' ? 'good' : selected?.run.taskStatus === 'FAILED' ? 'warning' : 'info'">{{ currentState }}</span></div>
    <div class="workflow-strip" aria-label="处理流程"><span :class="{ active: context }"><b>01</b> 宿主工单流转</span><i>—</i><span :class="{ active: selected }"><b>02</b> 后台准备核查材料</span><i>—</i><span :class="{ active: review }"><b>03</b> 专员确认留痕</span><small>确认材料不等于批准退费</small></div>
    <div v-if="error" role="alert" class="message error">{{ error }} <button v-if="!busy" class="text-button" @click="refreshPage">刷新数据</button></div>
    <div v-if="notice" role="status" class="message success">{{ notice }}</div>
    <div class="workspace-toolbar"><div class="history-select"><label for="precheck-round">核查记录</label><select id="precheck-round" :value="selected?.run.id || ''" :disabled="busy || !history.length" @change="select($event.target.value)"><option value="" disabled>{{ loading ? '正在读取…' : '尚无记录' }}</option><option v-if="selected && !history.some(item => item.run.id === selected.run.id)" :value="selected.run.id">{{ dateTime(selected.run.createdAt) }} · {{ states[selected.run.status] }} · {{ selected.run.id.slice(0, 8) }}</option><option v-for="item in history" :key="item.run.id" :value="item.run.id">{{ item.run.currentResult === false ? '已过期 · ' : '' }}{{ dateTime(item.run.createdAt) }} · {{ states[item.run.status] }} · {{ item.run.id.slice(0, 8) }}</option></select><button v-if="moreHistory" class="text-button" :disabled="busy" @click="loadMore('history')">更早记录</button></div><div class="toolbar-actions"><span class="muted">AI 由宿主工单事件自动触发</span><button class="secondary" :disabled="busy" @click="refreshPage">刷新</button></div></div>
    <div v-if="loading && !context" class="loading-panel" role="status"><span class="spinner"></span>正在读取宿主工单与历史记录…</div>
    <div v-else-if="context" class="work-grid">
      <section class="panel evidence-panel"><div class="panel-heading"><div><span class="section-index">01 / EVIDENCE</span><h2>工单与业务证据</h2></div><span v-if="context.synthetic" class="badge neutral">合成数据</span></div>
        <div v-if="selected?.evidenceSnapshot" class="source-toggle" aria-label="证据来源"><button :class="{ chosen: source === 'snapshot' }" :aria-pressed="source === 'snapshot'" @click="source = 'snapshot'">本轮证据快照</button><button :class="{ chosen: source === 'current' }" :aria-pressed="source === 'current'" @click="source = 'current'">宿主当前数据</button></div>
        <p v-if="selected && !showingSnapshot" class="inline-note source-warning">正在查看宿主当前数据；核验本轮 AI 引用请切回证据快照。</p>
        <EvidencePanel ref="evidencePanel" :context="evidence" :snapshot="showingSnapshot" />
      </section>
      <div class="review-column">
        <section class="panel result-panel"><div class="panel-heading"><div><span class="section-index">02 / AI MATERIAL</span><h2>AI 核查材料</h2></div><span v-if="selected" class="badge" :class="selected.run.callStatus === 'SUCCESS' ? 'good' : selected.run.callStatus === 'FAILED' ? 'warning' : 'info'">{{ { SUCCESS: '材料已生成', FAILED: 'AI 未完成', RUNNING: '正在处理', PENDING: '等待执行' }[selected.run.callStatus] }}</span></div>
          <div v-if="loading" class="empty-panel" role="status">正在加载本轮记录…</div>
          <div v-else-if="!selected" class="empty-panel"><div class="empty-symbol">✧</div><h3>等待宿主工单事件</h3><p>原始工单不受影响，可以立即查看和人工处理；工单进入投诉队列后，AI 核查材料会自动出现在这里。</p><small>无需在本页点击按钮生成 AI 内容</small></div>
          <div v-else class="panel-body">
            <div v-if="selected.run.currentResult === false" class="inline-note stale-note" role="status">工单已有更新版本，本轮材料已过期，仅供历史追溯，不可再次确认或重试。</div>
            <div v-if="selected.run.status === 'ANALYZING'" class="inline-note" role="status">后台正在准备核查材料，页面每 4 秒轻量更新任务状态；左侧原始工单可正常查看。</div>
            <div v-if="selected.run.callStatus === 'FAILED'" class="inline-note"><div>{{ failures[selected.run.failureCode] || 'AI 核查未完成' }}。原工单仍可由人工处理，并可直接填写下方核查材料。</div><button v-if="canRetry" class="retry-button" :disabled="busy" @click="retry">{{ writing ? '正在授权…' : '授权重新执行 AI 核查' }}</button></div>
            <div v-if="selected.findings?.length" class="findings"><h3>规则核验提示</h3><div v-for="(finding, index) in selected.findings" :key="index" class="finding"><span class="finding-dot"></span><div>{{ findings[finding.code] || '需要人工核查' }}<div class="evidence-links"><button v-for="id in finding.evidenceIds" :key="id" class="evidence-link" @click="locate(id)">{{ id }} ↗</button></div></div></div></div>
            <div v-if="selected.result" class="ai-output"><div class="result-facts"><div><span>投诉类型</span><strong>{{ { VALUE_ADDED_CHARGE_DISPUTE: '增值业务扣费争议', OTHER: '其他投诉', UNCLEAR: '待进一步澄清' }[selected.result.complaintType] }}</strong></div><div><span>争议金额</span><strong>{{ selected.result.disputedAmount == null ? '未提取' : '¥ ' + money(selected.result.disputedAmount) }}</strong></div></div><h3>标准化摘要</h3><p class="content-text">{{ selected.result.summary }}</p><h3>建议处理方向</h3><p class="recommendation">{{ recommendations[selected.result.recommendation] }}</p><h3>引用证据</h3><div class="evidence-links"><button v-for="id in selected.result.evidenceIds" :key="id" class="evidence-link" @click="locate(id)">{{ id }} ↗</button><span v-if="!selected.result.evidenceIds?.length" class="muted">无证据引用</span></div></div>
            <details class="technical-details"><summary>查看本轮溯源信息</summary><dl class="compact-dl"><dt>任务状态</dt><dd>{{ selected.run.taskStatus || '人工调试触发' }} · 第 {{ selected.run.attemptCount || 0 }} 次执行</dd><dt>工单业务版本</dt><dd>{{ selected.run.ticketVersion ?? '未提供（人工触发）' }}{{ selected.run.currentResult === false ? ' · 已过期' : ' · 当前有效' }}</dd><dt>事件 ID</dt><dd>{{ selected.run.eventId || '—' }}</dd><dt>模型</dt><dd>{{ selected.run.actualModel || selected.run.requestedModel }}</dd><dt>Prompt 版本</dt><dd>{{ selected.run.promptVersion }}</dd><dt>调用耗时</dt><dd>{{ selected.run.durationMs == null ? '—' : selected.run.durationMs + ' ms' }}</dd><dt>记录 / 版本</dt><dd>{{ selected.run.id }} / v{{ selected.run.version }}</dd><dt>Trace ID</dt><dd>{{ selected.run.traceId }}</dd></dl></details>
          </div>
        </section>
        <section class="panel human-panel"><div class="panel-heading"><div><span class="section-index">03 / SPECIALIST CHECK</span><h2>投诉专员核对确认</h2></div><span v-if="review" class="badge" :class="review.decision === 'CONFIRMED' ? 'good' : 'warning'">{{ review.decision === 'CONFIRMED' ? '材料已确认' : '已退回核查' }}</span></div>
          <div v-if="review && !loading" class="panel-body"><h3>专员确认后的摘要</h3><p class="content-text">{{ review.summary }}</p><h3>处理建议</h3><p>{{ recommendations[review.recommendation] }}</p><h3>核查意见</h3><p class="content-text">{{ review.reason }}</p><div class="review-signature">{{ review.actorId }} · {{ dateTime(review.reviewedAt) }}<br>材料版本 v{{ review.version }} · 已留存审计</div></div>
          <form v-else-if="canReview && !loading" class="review-form panel-body" @submit.prevent="submit('CONFIRMED')"><p class="muted">请结合左侧证据修正 AI 材料。确认只表示核查材料已经确认，不批准退费，也不会写入宿主最终处理结论。</p><label for="review-summary">核查摘要 <span>必填 · 最多 1000 字</span></label><textarea id="review-summary" v-model="draft.summary" :disabled="busy" maxlength="1000" rows="4" required placeholder="核对投诉与业务证据，填写专员确认后的摘要"></textarea><label for="review-recommendation">处理建议</label><select id="review-recommendation" v-model="draft.recommendation" :disabled="busy"><option v-for="(label, key) in recommendations" :key="key" :value="key">{{ label }}</option></select><label for="review-reason">核查意见 <span>必填 · 最多 1000 字</span></label><textarea id="review-reason" v-model="draft.reason" :disabled="busy" maxlength="1000" rows="3" required placeholder="说明确认、退回或转专员进一步核实的依据"></textarea><div class="review-buttons"><button type="button" class="danger-outline" :disabled="busy" @click="submit('REJECTED')">退回核查材料</button><button class="primary" :disabled="busy">{{ writing ? '正在提交…' : '确认核查材料' }}</button></div></form>
          <div v-else class="empty-panel compact"><p>{{ loading ? '正在读取确认状态…' : !selected ? 'AI 材料完成或失败后，投诉专员可在此人工核查。' : selected.run.currentResult === false ? '旧版本材料仅供追溯，不可确认。' : selected.run.status === 'ANALYZING' ? 'AI 仍在处理中；原始工单可继续人工查看和处理。' : !isReviewer ? '当前身份只读，请由具有处理权限的投诉专员确认。' : '当前材料不可确认，请刷新状态。' }}</p></div>
          <details v-if="preservedDraft" class="preserved-draft" open><summary>保留的未提交编辑</summary><p>{{ preservedDraft.summary }}</p><p>{{ recommendations[preservedDraft.recommendation] }}</p><p>{{ preservedDraft.reason }}</p></details>
        </section>
      </div>
      <section class="panel audit-panel"><div class="panel-heading"><div><span class="section-index">TRACEABILITY</span><h2>业务操作时间线</h2></div><span class="muted">{{ canAudit ? '按本轮操作顺序展示' : '需要审核或审计权限' }}</span></div>
        <div v-if="!canAudit" class="empty-panel compact">审计时间线仅向审核员与审计员开放。</div>
        <div v-else-if="auditError" role="alert" class="message error">{{ auditError }} <button class="text-button" :disabled="busy" @click="refreshPage">重新读取</button></div>
        <ol v-else-if="audit.length" class="timeline"><li v-for="item in audit" :key="item.event.id"><div class="timeline-dot"></div><div class="timeline-main"><div class="section-line"><strong>{{ actions[item.event.action] || item.event.action }}</strong><time>{{ dateTime(item.event.occurredAt) }}</time></div><p>{{ item.event.actorId }} <span>·</span> {{ item.event.fromStatus ? states[item.event.fromStatus] + ' → ' : '' }}{{ states[item.event.toStatus] }} <span>·</span> v{{ item.event.runVersion }} <span>·</span> {{ { COMMITTED: '已提交', ACCEPTED: '已接收', RUNNING: '执行中', RETRY_WAIT: '等待重试', QUEUED: '已重新入队', SUCCESS: '成功', FAILED: '失败转人工', IMPORTED: '已导入' }[item.event.outcome] || item.event.outcome }}</p><div v-if="item.reviewChange" class="review-change"><div><span>AI 原始材料</span><p>{{ item.reviewChange.aiResult?.summary || 'AI 未生成有效摘要' }}</p><small>{{ recommendations[item.reviewChange.aiResult?.recommendation] || '无有效建议' }}</small></div><b>→</b><div><span>专员确认材料</span><p>{{ item.reviewChange.humanResult.summary }}</p><small>{{ recommendations[item.reviewChange.humanResult.recommendation] }} · {{ item.reviewChange.humanResult.reason }}</small></div></div><details><summary>操作追踪信息</summary><dl class="compact-dl"><dt>事件 / 工单版本</dt><dd>{{ item.event.eventId || '人工触发' }} / {{ item.event.ticketVersion ?? '—' }}</dd><dt>Trace ID</dt><dd>{{ item.event.traceId }}</dd><dt>证据快照</dt><dd>{{ item.event.snapshotRunId }}</dd><dt>模型 / Prompt</dt><dd>{{ item.event.model }} / {{ item.event.promptVersion }}</dd><dt>耗时 / 错误</dt><dd>{{ item.event.durationMs == null ? '—' : item.event.durationMs + ' ms' }} / {{ item.event.errorType || '—' }}</dd></dl></details></div></li></ol>
        <div v-else class="empty-panel compact">{{ loading ? '正在读取操作记录…' : '本轮暂无可展示的审计事件。' }}</div><button v-if="moreAudit" class="text-button audit-more" :disabled="busy" @click="loadMore('audit')">继续加载事件</button>
      </section>
    </div>
    <footer class="module-footer">AI 仅辅助理解、整理与建议。确认核查材料不等于批准退费；未确认结果不会自动写入宿主最终业务结论。</footer>
  </main>
</template>
