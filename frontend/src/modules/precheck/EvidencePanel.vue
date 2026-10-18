<script setup>
import { watch } from 'vue'
import { dateTime, money } from './labels'
const props = defineProps({ context: Object, snapshot: Boolean })
const nodes = new Map()
watch(() => props.context, () => nodes.clear(), { flush: 'pre' })
const labels = { AVAILABLE: '已获取', NOT_FOUND: '未找到', UNAVAILABLE: '暂不可查询' }
defineExpose({ focus(id) { const el = nodes.get(id); el?.scrollIntoView?.({ behavior: 'smooth', block: 'center' }); el?.focus({ preventScroll: true }) } })
</script>
<template>
  <div v-if="context" class="evidence-content">
    <div class="complaint-block"><span class="section-label">客户原始投诉</span><p>{{ context.complaint }}</p>
      <div class="meta-line"><span>{{ context.customerRef }}</span><span>{{ context.synthetic ? '合成数据' : '宿主数据' }}</span></div>
    </div>
    <div class="evidence-group">
      <div class="section-line"><h3>账单记录</h3><span class="badge" :class="context.bills.status === 'AVAILABLE' ? 'good' : 'warning'">{{ labels[context.bills.status] || '未知状态' }}</span></div>
      <div v-for="bill in context.bills.items" :key="bill.evidenceId" :ref="el => el && nodes.set(bill.evidenceId, el)" tabindex="-1" class="evidence-item">
        <div class="section-line"><code>{{ bill.evidenceId }}</code><strong class="amount">¥ {{ money(bill.amount) }}</strong></div>
        <dl class="compact-dl"><dt>账期</dt><dd>{{ bill.billingPeriod }}</dd><dt>业务 / 订购 ID</dt><dd>{{ bill.serviceId }} / {{ bill.subscriptionId }}</dd><dt>币种</dt><dd>{{ bill.currency }}</dd></dl>
      </div>
      <p v-if="!context.bills.items.length" class="muted">当前未获得账单明细。</p>
    </div>
    <div class="evidence-group">
      <div class="section-line"><h3>订购记录</h3><span class="badge" :class="context.subscriptions.status === 'AVAILABLE' ? 'good' : 'warning'">{{ labels[context.subscriptions.status] || '未知状态' }}</span></div>
      <div v-for="order in context.subscriptions.items" :key="order.evidenceId" :ref="el => el && nodes.set(order.evidenceId, el)" tabindex="-1" class="evidence-item">
        <div class="section-line"><code>{{ order.evidenceId }}</code><strong>{{ order.serviceName }}</strong></div>
        <dl class="compact-dl"><dt>订购价格</dt><dd>¥ {{ money(order.monthlyPrice) }} / 月</dd><dt>业务 / 订购 ID</dt><dd>{{ order.serviceId }} / {{ order.subscriptionId }}</dd><dt>订购时间</dt><dd>{{ dateTime(order.orderedAt) }}</dd></dl>
      </div>
      <p v-if="!context.subscriptions.items.length" class="muted">当前未获得订购明细。</p>
    </div>
    <div class="evidence-group">
      <div class="section-line"><h3>授权凭据</h3><span class="badge" :class="context.authorizations.status === 'AVAILABLE' ? 'good' : 'warning'">{{ labels[context.authorizations.status] || '未知状态' }}</span></div>
      <div v-for="auth in context.authorizations.items" :key="auth.evidenceId" :ref="el => el && nodes.set(auth.evidenceId, el)" tabindex="-1" class="evidence-item">
        <code>{{ auth.evidenceId }}</code><p>{{ auth.description }}</p>
        <dl class="compact-dl"><dt>授权渠道</dt><dd>{{ auth.channel }}</dd><dt>关联订购</dt><dd>{{ auth.subscriptionId }}</dd><dt>授权时间</dt><dd>{{ dateTime(auth.authorizedAt) }}</dd></dl>
      </div>
      <div v-if="!context.authorizations.items.length" class="inline-note">未获得可核查的授权凭据。证据缺失不等于违规扣费，需要进一步核实。</div>
    </div>
    <div class="source-note">{{ snapshot ? '本轮证据快照' : '宿主当前上下文' }} · {{ context.sourceSystem }}<br>采集时间 {{ dateTime(context.capturedAt) }}</div>
  </div>
</template>
