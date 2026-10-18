<script setup>
import { computed, inject, reactive, ref, watch } from 'vue'
import { precheckHostKey, errorText } from './api'
import './precheck.css'
const props = defineProps({ ticketId: { type: String, required: true } })
const host = inject(precheckHostKey)
const context = ref(null), loading = ref(false), submitting = ref(false), error = ref(''), notice = ref('')
const form = reactive({ category: '增值业务扣费争议', channel: '10000号热线', priority: '普通', complaint: '' })
const submitted = reactive({})
const existingStatus = computed(() => ({ 'DEMO-002': '处理中', 'DEMO-003': '已完成' }[props.ticketId] || ''))
const readonly = computed(() => !!existingStatus.value)
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
    <header class="operator-heading"><div><span>{{ readonly ? `投诉详情 / ${ticketId}` : '投诉受理' }}</span><h1>{{ readonly ? '客户投诉记录' : '新建投诉' }}</h1><p>{{ readonly ? '查看已提交投诉及当前处理进度' : '填写客户投诉信息后提交' }}</p></div><strong class="role-state">{{ submitted[ticketId] || existingStatus || '登记中' }}</strong></header>
    <div v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></div>
    <section v-if="submitted[ticketId]" class="operator-result" role="status"><i>✓</i><div><h2>投诉工单已提交</h2><p>{{ ticketId }} 已进入投诉处理队列，当前状态：{{ submitted[ticketId] }}。</p></div></section>
    <section v-else-if="readonly" class="operator-readonly"><dl><div><dt>客户标识</dt><dd>{{ context?.customerRef || '—' }}</dd></div><div><dt>投诉类型</dt><dd>{{ form.category }}</dd></div><div class="wide"><dt>客户诉求</dt><dd>{{ form.complaint }}</dd></div><div><dt>处理状态</dt><dd><strong>{{ existingStatus }}</strong></dd></div><div><dt>受理渠道</dt><dd>{{ form.channel }}</dd></div></dl></section>
    <form v-else class="business-form focused-form" @submit.prevent="submitTicket">
      <label class="operator-customer">客户标识<input :value="context?.customerRef || '正在读取…'" disabled><small>由宿主工单系统自动带入</small></label>
      <label>投诉类型<select v-model="form.category" :disabled="loading || submitting"><option>增值业务扣费争议</option><option>业务订购争议</option><option>计费金额争议</option></select></label>
      <label class="complaint-field">客户诉求<textarea v-model="form.complaint" :disabled="loading || submitting" rows="7" maxlength="1000" required></textarea><small>{{ form.complaint.length }} / 1000</small></label>
      <details class="operator-more"><summary>更多受理信息</summary><div><label>受理渠道<select v-model="form.channel" :disabled="loading || submitting"><option>10000号热线</option><option>线上客服</option><option>营业厅</option></select></label><label>紧急程度<select v-model="form.priority" :disabled="loading || submitting"><option>普通</option><option>关注</option><option>紧急</option></select></label></div></details>
      <div class="form-actions"><button class="primary" :disabled="loading || submitting">{{ submitting ? '正在提交…' : '提交投诉工单' }}</button></div>
    </form>
  </main>
</template>
