<script setup>
import { computed, inject } from 'vue'
import { precheckHostKey } from './api'
import { usePrecheck } from './usePrecheck'
import { states, recommendations, actions, dateTime } from './labels'
import './precheck.css'
const props = defineProps({ ticketId: { type: String, required: true } })
const host = inject(precheckHostKey)
const { context, history, selected, review, audit, loading, busy, moreAudit, error, auditError, refresh, select, loadMore } = usePrecheck(() => props.ticketId, host)
const outcome = computed(() => review.value?.decision === 'CONFIRMED' ? '人工核查已完成' : review.value?.decision === 'REJECTED' ? '已退回补充材料' : selected.value?.run.status === 'PENDING_REVIEW' ? '待投诉专员处理' : selected.value?.run.status === 'ANALYZING' ? '系统核查中' : '暂无处理结果')
const citedEvidence = computed(() => selected.value?.result?.evidenceIds || [])
const mentionedEvidence = computed(() => [...new Set((review.value?.reason?.match(/(?:BILL|ORDER|AUTH)-[A-Za-z0-9_-]+/g) || []))])
const uncitedEvidence = computed(() => mentionedEvidence.value.filter(id => !citedEvidence.value.includes(id)))
const risks = computed(() => {
  const items = []
  if (uncitedEvidence.value.length) items.push({ level: 'high', title: '处理意见缺少结构化证据引用', text: `意见中提到 ${uncitedEvidence.value.join('、')}，但未建立对应引用关系。` })
  if (!citedEvidence.value.length) items.push({ level: 'high', title: '未记录结构化证据依据', text: '当前处理结果没有可追溯的证据引用，需要重点复核。' })
  if (selected.value?.run.callStatus === 'FAILED') items.push({ level: 'medium', title: '智能核查未完成', text: '本工单已转入人工处理，应结合业务规则判断是否影响流程合规。' })
  return items
})
</script>

<template>
  <main class="role-workspace audit-workspace" :aria-busy="busy">
    <header class="audit-heading"><div><span>质量审计 / {{ ticketId }}</span><h1>工单审计概览</h1></div><strong class="role-state readonly">只读</strong></header>
    <div v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="refresh">重新读取</button></div>
    <div v-if="loading && !context" class="loading-panel"><span class="spinner"></span>正在读取工单审计材料…</div>
    <template v-else-if="context">
      <section class="audit-hero">
        <div><span>处理结果</span><h2>{{ outcome }}</h2><p>{{ review?.actorId || '尚未处理' }}<template v-if="review"> · {{ dateTime(review.reviewedAt) }}</template></p></div>
        <p>{{ review?.summary || selected?.result?.summary || '尚未形成核查摘要' }}</p>
      </section>

      <section class="audit-risks" aria-label="审计关注事项">
        <header><h2>审计关注事项</h2><span>{{ risks.length ? `${risks.length} 项需关注` : '未发现明显风险' }}</span></header>
        <article v-for="risk in risks" :key="risk.title" :class="risk.level"><i>!</i><div><strong>{{ risk.title }}</strong><p>{{ risk.text }}</p></div></article>
        <p v-if="!risks.length" class="audit-clear">当前处理结果、责任人与证据引用关系完整。</p>
      </section>

      <section class="panel audit-result-panel">
        <div class="panel-heading"><div><h2>处理结果</h2></div><span class="muted">最终人工意见</span></div>
        <div class="audit-result-body">
          <dl><dt>客户诉求</dt><dd>{{ context.complaint }}</dd><dt>核查意见</dt><dd>{{ review?.reason || '尚未提交人工核查意见' }}</dd><dt>处理建议</dt><dd>{{ recommendations[review?.recommendation || selected?.result?.recommendation] || '—' }}</dd></dl>
          <details class="audit-evidence-details"><summary>证据与依据（{{ citedEvidence.length }} 项正式引用）</summary><div class="audit-evidence-list"><span v-for="id in citedEvidence" :key="id">{{ id }}</span><p v-if="!citedEvidence.length">未建立结构化证据引用。</p><p v-if="uncitedEvidence.length">处理意见中另提到：{{ uncitedEvidence.join('、') }}</p></div></details>
        </div>
      </section>

      <section class="panel audit-log-panel">
        <div class="panel-heading"><div><h2>审计轨迹</h2></div><span class="muted">{{ audit.length }} 条业务事件</span></div>
        <div v-if="auditError" class="message error" role="alert">{{ auditError }}</div>
        <ol v-else-if="audit.length" class="audit-business-timeline"><li v-for="item in audit" :key="item.event.id"><i></i><div><div><strong>{{ actions[item.event.action] || item.event.action }}</strong><time>{{ dateTime(item.event.occurredAt) }}</time></div><p>{{ item.event.actorId }} · {{ states[item.event.toStatus] }} · {{ item.event.outcome }}</p><details><summary>查看技术日志</summary><dl><dt>Trace ID</dt><dd>{{ item.event.traceId }}</dd><dt>事件 ID</dt><dd>{{ item.event.eventId || '—' }}</dd><dt>模型 / Prompt</dt><dd>{{ item.event.model }} / {{ item.event.promptVersion }}</dd><dt>耗时 / 错误</dt><dd>{{ item.event.durationMs == null ? '—' : item.event.durationMs + ' ms' }} / {{ item.event.errorType || '—' }}</dd></dl></details></div></li></ol>
        <div v-else class="empty-panel compact">暂无操作记录。</div>
        <button v-if="moreAudit" class="text-button audit-more" @click="loadMore('audit')">加载更多记录</button>
      </section>

      <details class="history-disclosure auditor-history">
        <summary>查看历史核查版本（{{ history.length }}）</summary>
        <div><button v-for="item in history" :key="item.run.id" :class="{ active: item.run.id === selected?.run.id }" @click="select(item.run.id)">{{ dateTime(item.run.createdAt) }} · {{ states[item.run.status] }}</button></div>
      </details>
    </template>
  </main>
</template>
