<script setup>
import { watch } from 'vue'
import { dateTime, money } from './labels'
const props = defineProps({ context: Object, snapshot: Boolean })
const nodes = new Map()
watch(() => props.context, () => nodes.clear(), { flush: 'pre' })
const labels = { AVAILABLE: '已获取', NOT_FOUND: '未找到', UNAVAILABLE: '暂不可查询' }
defineExpose({ focus(id) { const el = nodes.get(id); const details = el?.closest('details'); if (details) details.open = true; el?.scrollIntoView?.({ behavior: 'smooth', block: 'center' }); el?.focus({ preventScroll: true }) } })
</script>

<template>
  <div v-if="context" class="evidence-content progressive-evidence">
    <details class="evidence-disclosure">
      <summary><span><strong>账单记录</strong><small>{{ context.bills.items.length ? `共 ${context.bills.items.length} 条` : '无记录' }}</small></span><b v-if="context.bills.items[0]">¥ {{ money(context.bills.items[0].amount) }}</b><em :class="context.bills.status === 'AVAILABLE' ? 'available' : 'missing'">{{ labels[context.bills.status] }}</em></summary>
      <div class="evidence-detail-body"><div v-for="bill in context.bills.items" :key="bill.evidenceId" :ref="el => el && nodes.set(bill.evidenceId, el)" tabindex="-1" class="evidence-item"><div class="section-line"><code>{{ bill.evidenceId }}</code><strong class="amount">¥ {{ money(bill.amount) }}</strong></div><dl class="compact-dl"><dt>账期</dt><dd>{{ bill.billingPeriod }}</dd><dt>业务 / 订购 ID</dt><dd>{{ bill.serviceId }} / {{ bill.subscriptionId }}</dd><dt>币种</dt><dd>{{ bill.currency }}</dd></dl></div><p v-if="!context.bills.items.length">当前未获得账单明细。</p></div>
    </details>
    <details class="evidence-disclosure">
      <summary><span><strong>订购记录</strong><small>{{ context.subscriptions.items[0]?.serviceName || '无记录' }}</small></span><b v-if="context.subscriptions.items[0]">¥ {{ money(context.subscriptions.items[0].monthlyPrice) }} / 月</b><em :class="context.subscriptions.status === 'AVAILABLE' ? 'available' : 'missing'">{{ labels[context.subscriptions.status] }}</em></summary>
      <div class="evidence-detail-body"><div v-for="order in context.subscriptions.items" :key="order.evidenceId" :ref="el => el && nodes.set(order.evidenceId, el)" tabindex="-1" class="evidence-item"><div class="section-line"><code>{{ order.evidenceId }}</code><strong>{{ order.serviceName }}</strong></div><dl class="compact-dl"><dt>订购价格</dt><dd>¥ {{ money(order.monthlyPrice) }} / 月</dd><dt>业务 / 订购 ID</dt><dd>{{ order.serviceId }} / {{ order.subscriptionId }}</dd><dt>订购时间</dt><dd>{{ dateTime(order.orderedAt) }}</dd></dl></div><p v-if="!context.subscriptions.items.length">当前未获得订购明细。</p></div>
    </details>
    <details class="evidence-disclosure">
      <summary><span><strong>授权凭据</strong><small>{{ context.authorizations.items.length ? context.authorizations.items[0].channel : '需要进一步核实' }}</small></span><b>{{ context.authorizations.items.length ? `${context.authorizations.items.length} 条` : '无凭据' }}</b><em :class="context.authorizations.status === 'AVAILABLE' ? 'available' : 'missing'">{{ labels[context.authorizations.status] }}</em></summary>
      <div class="evidence-detail-body"><div v-for="auth in context.authorizations.items" :key="auth.evidenceId" :ref="el => el && nodes.set(auth.evidenceId, el)" tabindex="-1" class="evidence-item"><code>{{ auth.evidenceId }}</code><p>{{ auth.description }}</p><dl class="compact-dl"><dt>授权渠道</dt><dd>{{ auth.channel }}</dd><dt>关联订购</dt><dd>{{ auth.subscriptionId }}</dd><dt>授权时间</dt><dd>{{ dateTime(auth.authorizedAt) }}</dd></dl></div><p v-if="!context.authorizations.items.length" class="evidence-missing-note">未获得可核查的授权凭据，需进一步核实。</p></div>
    </details>
    <div class="source-note">{{ snapshot ? '核查时证据快照' : '当前业务数据' }} · {{ dateTime(context.capturedAt) }}</div>
  </div>
</template>
