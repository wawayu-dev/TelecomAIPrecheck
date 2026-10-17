<script setup>
import { ref } from 'vue'
import { getTicketContext } from './api/context'

const ticketId = ref('DEMO-001')
const context = ref(null)
const loading = ref(false)
const error = ref('')
async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  context.value = null
  try { context.value = await getTicketContext(ticketId.value) }
  catch (cause) { error.value = cause instanceof TypeError ? '网络连接失败，请确认后端已启动' : cause.message }
  finally { loading.value = false }
}
const statusLabels = { AVAILABLE: '已获取', NOT_FOUND: '未找到', UNAVAILABLE: '暂不可查询' }
</script>

<template>
  <main>
    <header><p class="eyebrow">TELECOM AI PRECHECK · ISSUE #1</p><h1>工单上下文联调</h1>
      <p>项目基础工程 · 合成数据演示</p></header>
    <section>
      <h2>读取宿主工单</h2>
      <p>通过统一适配接口读取投诉与关联证据。AI 预审及人工审核将在后续阶段实现。</p>
      <form @submit.prevent="load">
        <label for="ticket">模拟场景</label>
        <select id="ticket" v-model="ticketId" :disabled="loading" @change="context = null; error = ''">
          <option value="DEMO-001">DEMO-001 · 证据齐全</option>
          <option value="DEMO-002">DEMO-002 · 缺少授权凭据</option>
          <option value="DEMO-003">DEMO-003 · 账单与订购金额不一致</option>
        </select>
        <button :disabled="loading">{{ loading ? '正在读取…' : '读取上下文' }}</button>
      </form>
      <p v-if="error" role="alert">{{ error }}</p>
      <div v-if="context" aria-live="polite">
        <h3>{{ context.ticketId }}</h3><p>{{ context.complaint }}</p>
        <p class="notice">所有数据均为合成。证据未找到不代表违规扣费。</p>
        <dl>
          <dt>账单</dt><dd v-for="bill in context.bills.items" :key="bill.evidenceId">{{ bill.evidenceId }} · {{ bill.billingPeriod }} · {{ bill.amount }} {{ bill.currency }}</dd>
          <dt>订购记录</dt><dd v-for="order in context.subscriptions.items" :key="order.evidenceId">{{ order.evidenceId }} · {{ order.serviceName }} · {{ order.monthlyPrice }} {{ order.currency }}/月</dd>
          <dt>授权凭据</dt><dd>{{ statusLabels[context.authorizations.status] }}</dd>
          <dd v-for="auth in context.authorizations.items" :key="auth.evidenceId">{{ auth.evidenceId }} · {{ auth.description }}</dd>
        </dl>
      </div>
    </section>
    <footer>只读联调页面 · 当前不生成 AI 结论、不执行审核或退费</footer>
  </main>
</template>
