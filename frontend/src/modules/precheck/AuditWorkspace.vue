<script setup>
import { computed, inject } from 'vue'
import { precheckHostKey } from './api'
import { usePrecheck } from './usePrecheck'
import { states, recommendations, actions, dateTime } from './labels'
import './precheck.css'
const props = defineProps({ ticketId: { type: String, required: true } })
const host = inject(precheckHostKey)
const { context, history, selected, review, audit, loading, busy, moreAudit, error, auditError, refresh, select, loadMore } = usePrecheck(() => props.ticketId, host)
const outcome = computed(() => review.value?.decision === 'CONFIRMED' ? '核查材料已确认' : review.value?.decision === 'REJECTED' ? '已要求补充材料' : selected.value?.run.status === 'PENDING_REVIEW' ? '待投诉专员处理' : selected.value?.run.status === 'ANALYZING' ? '系统核查中' : '暂无处理结果')
</script>

<template>
  <main class="role-workspace audit-workspace" :aria-busy="busy">
    <header class="role-page-heading"><div><span>质量审计</span><h1>投诉工单审计</h1></div><strong class="role-state readonly">只读</strong></header>
    <div v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="refresh">重新读取</button></div>
    <div v-if="loading && !context" class="loading-panel"><span class="spinner"></span>正在读取工单审计材料…</div>
    <template v-else-if="context">
      <section class="audit-summary-grid">
        <article class="audit-summary-card"><span>处理结果</span><strong>{{ outcome }}</strong><p>{{ review?.summary || selected?.result?.summary || '尚未形成核查摘要' }}</p></article>
        <article class="audit-summary-card"><span>处理责任人</span><strong>{{ review?.actorId || '尚未处理' }}</strong><p>{{ review ? dateTime(review.reviewedAt) : '等待投诉处理专员完成核查' }}</p></article>
        <article class="audit-summary-card"><span>证据依据</span><strong>{{ selected?.result?.evidenceIds?.length || 0 }} 项引用</strong><p>{{ selected?.result?.evidenceIds?.join('、') || '当前无证据引用' }}</p></article>
      </section>

      <section class="panel audit-result-panel">
        <div class="panel-heading"><div><span class="section-index">审计结论</span><h2>处理结果与依据</h2></div><span class="muted">{{ ticketId }}</span></div>
        <div class="audit-result-body">
          <dl><dt>客户诉求</dt><dd>{{ context.complaint }}</dd><dt>核查意见</dt><dd>{{ review?.reason || '尚未提交人工核查意见' }}</dd><dt>处理建议</dt><dd>{{ recommendations[review?.recommendation || selected?.result?.recommendation] || '—' }}</dd></dl>
          <details class="audit-evidence-details"><summary>查看证据依据</summary><div class="audit-evidence-list"><span v-for="id in selected?.result?.evidenceIds || []" :key="id">{{ id }}</span><p v-if="!selected?.result?.evidenceIds?.length">暂无引用证据。</p></div></details>
        </div>
      </section>

      <section class="panel audit-log-panel">
        <div class="panel-heading"><div><span class="section-index">操作记录</span><h2>处理时间线</h2></div><span class="muted">{{ audit.length }} 条记录</span></div>
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
