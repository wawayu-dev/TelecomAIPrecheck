<script setup>
import { inject, reactive, ref, watch } from 'vue'
import { precheckHostKey, errorText } from './api'
import './precheck.css'
const props = defineProps({ ticketId: { type: String, required: true } })
const host = inject(precheckHostKey)
const context = ref(null), loading = ref(false), submitting = ref(false), error = ref(''), notice = ref('')
const form = reactive({ category: '增值业务扣费争议', channel: '10000号热线', priority: '普通', complaint: '' })
const submitted = reactive({})
async function load() {
  loading.value = true; error.value = ''; notice.value = ''
  try { context.value = await host.api.context(props.ticketId); form.complaint = context.value.complaint }
  catch (cause) { context.value = null; error.value = errorText(cause) }
  finally { loading.value = false }
}
async function submitTicket() {
  if (submitting.value || !form.complaint.trim()) return
  submitting.value = true; error.value = ''; notice.value = ''
  try {
    const eventId = `WEB-${globalThis.crypto?.randomUUID?.() || Date.now()}`
    const result = await host.api.transition(props.ticketId, { eventId, ticketVersion: Date.now() * 1000 })
    submitted[props.ticketId] = result.precheckAccepted ? '已提交 · 核查中' : '已提交 · 待人工处理'
    notice.value = '投诉工单已提交至处理队列。'
  } catch (cause) { error.value = errorText(cause) }
  finally { submitting.value = false }
}
watch(() => props.ticketId, load, { immediate: true })
</script>

<template>
  <main class="role-workspace operator-workspace">
    <header class="role-page-heading"><div><span>投诉受理</span><h1>登记投诉工单</h1></div><strong class="role-state">{{ submitted[ticketId] || '待提交' }}</strong></header>
    <div v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></div>
    <div v-if="notice" class="message success" role="status">{{ notice }}</div>
    <div class="operator-layout">
      <form class="business-form" @submit.prevent="submitTicket">
        <div class="business-section-title"><h2>投诉信息</h2><span>{{ ticketId }}</span></div>
        <div class="form-grid">
          <label>投诉类型<select v-model="form.category" :disabled="loading || submitting"><option>增值业务扣费争议</option><option>业务订购争议</option><option>计费金额争议</option></select></label>
          <label>受理渠道<select v-model="form.channel" :disabled="loading || submitting"><option>10000号热线</option><option>线上客服</option><option>营业厅</option></select></label>
          <label>紧急程度<select v-model="form.priority" :disabled="loading || submitting"><option>普通</option><option>关注</option><option>紧急</option></select></label>
          <label>客户标识<input :value="context?.customerRef || '正在读取…'" disabled></label>
        </div>
        <label class="complaint-field">客户诉求<textarea v-model="form.complaint" :disabled="loading || submitting" rows="6" maxlength="1000" required></textarea><small>{{ form.complaint.length }} / 1000</small></label>
        <div class="form-actions"><button type="button" class="secondary" :disabled="loading || submitting" @click="load">重置</button><button class="primary" :disabled="loading || submitting || !!submitted[ticketId]">{{ submitting ? '正在提交…' : submitted[ticketId] ? '工单已提交' : '提交投诉工单' }}</button></div>
      </form>
      <aside class="operator-status-panel"><div class="business-section-title"><h2>当前工单</h2></div><dl><dt>工单编号</dt><dd>{{ ticketId }}</dd><dt>客户标识</dt><dd>{{ context?.customerRef || '—' }}</dd><dt>当前状态</dt><dd><b>{{ submitted[ticketId] || '待提交' }}</b></dd><dt>受理人</dt><dd>{{ host.identity.value?.actorId }}</dd></dl><p>提交后由系统自动进入投诉处理队列。</p></aside>
    </div>
  </main>
</template>
