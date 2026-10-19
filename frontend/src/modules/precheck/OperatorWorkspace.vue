<script setup>
import { computed, inject, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { precheckHostKey, errorText } from './api'
import './precheck.css'
const props = defineProps({ ticketId: { type: String, required: true } })
const emit = defineEmits(['workflow-change'])
const host = inject(precheckHostKey)
const router = useRouter()
const context = ref(null), workflow = ref(null), loading = ref(false), submitting = ref(false), error = ref(''), notice = ref('')
const form = reactive({ title: '', customerRef: '', category: '增值业务扣费争议', channel: '10000号热线', priority: '普通', complaint: '' })
const submitted = ref('')
const creating = computed(() => props.ticketId === 'new')
const existingStatus = computed(() => workflow.value?.statusLabel || '')
const readonly = computed(() => workflow.value?.submitted === true)
async function load() {
  loading.value = true; error.value = ''; notice.value = ''; submitted.value = ''
  Object.assign(form, { title: '', customerRef: '', category: '增值业务扣费争议', channel: '10000号热线', priority: '普通', complaint: '' })
  if (creating.value) { context.value = null; workflow.value = null; loading.value = false; return }
  try {
    const [ticket, rows] = await Promise.all([host.api.context(props.ticketId), host.api.tickets()])
    context.value = ticket; workflow.value = rows.find(row => row.id === props.ticketId) || null
    Object.assign(form, { title: workflow.value?.title || '', customerRef: context.value.customerRef,
      complaint: context.value.complaint, category: workflow.value?.category || '增值业务扣费争议',
      channel: workflow.value?.channel || '10000号热线', priority: workflow.value?.priority || '普通' })
  }
  catch (cause) { context.value = null; workflow.value = null; error.value = errorText(cause) }
  finally { loading.value = false }
}
async function submitTicket() {
  if (submitting.value || !form.title.trim() || !form.customerRef.trim() || !form.complaint.trim()) return
  submitting.value = true; error.value = ''; notice.value = ''
  let targetId = props.ticketId
  try {
    const body = { title: form.title.trim(), customerRef: form.customerRef.trim(), category: form.category,
      complaint: form.complaint.trim(), channel: form.channel, priority: form.priority }
    if (creating.value) targetId = (await host.api.createTicket(body)).id
    else await host.api.updateTicket(targetId, body)
    const eventId = `WEB-${globalThis.crypto?.randomUUID?.() || Date.now()}`
    const result = await host.api.transition(targetId, { eventId, ticketVersion: Date.now() * 1000 })
    const rows = await host.api.tickets()
    workflow.value = rows.find(row => row.id === targetId) || null
    submitted.value = result.precheckAccepted ? '已提交 · 核查中' : '已提交 · 待人工处理'
    notice.value = '投诉工单已提交至处理队列。'
    emit('workflow-change', { ticketId: targetId, workflowStatus: workflow.value?.workflowStatus })
  } catch (cause) {
    if (creating.value && targetId !== 'new') await router?.replace?.(`/host/tickets/${targetId}/precheck`)
    error.value = errorText(cause)
  }
  finally { submitting.value = false }
}
watch(() => props.ticketId, load, { immediate: true })
</script>

<template>
  <main class="role-workspace operator-workspace">
    <header class="operator-heading"><div><span>{{ readonly ? `投诉详情 / ${ticketId}` : '投诉受理' }}</span><h1>{{ readonly ? '客户投诉记录' : '新建投诉' }}</h1><p>{{ readonly ? '查看已提交投诉及当前处理进度' : '填写客户投诉信息后登记并提交' }}</p></div><strong class="role-state">{{ submitted || existingStatus || '登记中' }}</strong></header>
    <div v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></div>
    <section v-if="submitted" class="operator-result operator-status-panel" role="status"><i>✓</i><div><h2>投诉工单已提交</h2><p>{{ ticketId }} 已进入投诉处理队列，当前状态：{{ submitted }}。</p></div></section>
    <section v-else-if="readonly" class="operator-readonly"><dl><div><dt>客户标识</dt><dd>{{ context?.customerRef || '—' }}</dd></div><div><dt>投诉类型</dt><dd>{{ form.category }}</dd></div><div class="wide"><dt>客户诉求</dt><dd>{{ form.complaint }}</dd></div><div><dt>处理状态</dt><dd><strong>{{ existingStatus }}</strong></dd></div><div><dt>受理渠道</dt><dd>{{ form.channel }}</dd></div></dl></section>
    <form v-else class="business-form focused-form" @submit.prevent="submitTicket">
      <label>工单标题<input v-model="form.title" :disabled="loading || submitting" maxlength="128" required placeholder="例如：视频会员扣费争议"></label>
      <label class="operator-customer">客户标识<input v-model="form.customerRef" :disabled="loading || submitting" maxlength="64" required placeholder="请输入客户标识"><small>仅保存业务引用，不填写证件号码</small></label>
      <label>投诉类型<select v-model="form.category" :disabled="loading || submitting"><option>增值业务扣费争议</option><option>业务订购争议</option><option>计费金额争议</option></select></label>
      <label class="complaint-field">客户诉求<textarea v-model="form.complaint" :disabled="loading || submitting" rows="7" maxlength="1000" required></textarea><small>{{ form.complaint.length }} / 1000</small></label>
      <details class="operator-more"><summary>更多受理信息</summary><div><label>受理渠道<select v-model="form.channel" :disabled="loading || submitting"><option>10000号热线</option><option>线上客服</option><option>营业厅</option></select></label><label>紧急程度<select v-model="form.priority" :disabled="loading || submitting"><option>普通</option><option>关注</option><option>紧急</option></select></label></div></details>
      <div class="form-actions"><button class="primary" :disabled="loading || submitting">{{ submitting ? '正在登记并提交…' : '登记并提交投诉工单' }}</button></div>
    </form>
  </main>
</template>
