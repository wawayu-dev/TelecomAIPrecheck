<script setup>
import { computed, inject, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { precheckHostKey } from './modules/precheck'
import { errorText } from './modules/precheck/api'
const host = inject(precheckHostKey)
const route = useRoute()
const router = useRouter()
const identity = host.identity
const account = ref('operator'), signing = ref(false), loginError = ref('')
const tickets = ref([]), ticketLoading = ref(false), ticketError = ref(''), activeNav = ref(0)
const roles = { ROLE_OPERATOR: '客服受理人员', ROLE_REVIEWER: '投诉处理专员', ROLE_AUDITOR: '质检审核人员（只读）' }
const currentTicket = computed(() => typeof route.params.ticketId === 'string' ? route.params.ticketId : '')
const currentRole = computed(() => identity.value?.roles[0] || '')
const roleUi = computed(() => ({
  ROLE_OPERATOR: { nav: ['投诉登记', '我的工单'], headings: ['待登记投诉', '我的投诉工单'], crumb: '投诉登记' },
  ROLE_REVIEWER: { nav: ['核查待办', '处理中', '已完成'], headings: ['核查待办', '处理中', '已完成'], crumb: '投诉核查' },
  ROLE_AUDITOR: { nav: ['审计工作台'], headings: ['审计工单'], crumb: '质量审计' },
}[currentRole.value] || { nav: [], headings: [], crumb: '' }))
const visibleTickets = computed(() => {
  if (currentRole.value === 'ROLE_OPERATOR') return tickets.value.filter(ticket => activeNav.value === 0 ? !ticket.submitted : ticket.submitted)
  if (currentRole.value === 'ROLE_REVIEWER') {
    const bucket = ['PENDING', 'PROCESSING', 'COMPLETED'][activeNav.value]
    return tickets.value.filter(ticket => ticket.reviewerBucket === bucket)
  }
  return tickets.value
})
const sidebarHeading = computed(() => roleUi.value.headings[activeNav.value] || roleUi.value.headings[0] || '')
const hasVisibleCurrent = computed(() => currentRole.value === 'ROLE_AUDITOR'
  || (currentRole.value === 'ROLE_OPERATOR' && currentTicket.value === 'new')
  || visibleTickets.value.some(ticket => ticket.id === currentTicket.value))
async function startNewTicket() {
  activeNav.value = 0
  await router.push('/host/tickets/new/precheck')
}
async function loadTickets(nextNav = null, focusTicketId = null) {
  if (!identity.value || !host.api?.tickets) { tickets.value = []; return }
  ticketLoading.value = true; ticketError.value = ''
  try {
    const rows = await host.api.tickets()
    let resolvedNav = nextNav
    if (resolvedNav === null && focusTicketId && currentRole.value === 'ROLE_REVIEWER') {
      const bucket = rows.find(ticket => ticket.id === focusTicketId)?.reviewerBucket
      const bucketIndex = ['PENDING', 'PROCESSING', 'COMPLETED'].indexOf(bucket)
      if (bucketIndex >= 0) resolvedNav = bucketIndex
    }
    if (resolvedNav !== null) activeNav.value = resolvedNav
    tickets.value = rows
  }
  catch (error) { tickets.value = []; ticketError.value = errorText(error) }
  finally { ticketLoading.value = false }
}
async function chooseNav(index) {
  activeNav.value = index
  const first = visibleTickets.value[0]
  if (first && !visibleTickets.value.some(ticket => ticket.id === currentTicket.value))
    await router.push(`/host/tickets/${first.id}/precheck`)
}
async function workflowChanged(change) {
  const nextNav = currentRole.value === 'ROLE_OPERATOR' ? 1
    : currentRole.value === 'ROLE_REVIEWER' && change?.decision === 'CONFIRMED' ? 2
      : currentRole.value === 'ROLE_REVIEWER' && change?.decision === 'REJECTED' ? 1 : null
  await loadTickets(nextNav, change?.ticketId)
  if (change?.ticketId && change.ticketId !== currentTicket.value)
    await router.replace(`/host/tickets/${change.ticketId}/precheck`)
}
watch(() => [identity.value?.actorId, currentRole.value], () => {
  activeNav.value = 0
  if (identity.value) loadTickets(); else tickets.value = []
}, { immediate: true })
async function login() {
  if (signing.value) return
  signing.value = true; loginError.value = ''
  try { await host.login(account.value) }
  catch (error) { loginError.value = error.status === 401 ? '演示身份登录未启用，请检查后端配置。' : errorText(error) }
  finally { signing.value = false }
}
async function logout() {
  if (signing.value) return
  signing.value = true; loginError.value = ''
  try { await host.logout() }
  catch { loginError.value = '本地身份已清除，服务端退出未确认。请重新登录。' }
  finally { signing.value = false }
}
</script>

<template>
  <div class="demo-host" :class="{ 'is-authenticated': identity }">
    <header class="system-header">
      <a class="system-brand" href="/" aria-label="客户服务运营平台首页">
        <span class="carrier-mark" aria-hidden="true"><i></i><i></i><i></i></span>
        <span><strong>客户服务运营平台</strong><small>Customer Service Operations</small></span>
      </a>
      <div class="system-header-right">
        <span class="system-status"><i></i>运行正常</span>
        <div v-if="identity" class="header-user host-identity"><span class="avatar">{{ identity.actorId.slice(0, 1).toUpperCase() }}</span><span><strong>{{ identity.actorId }}</strong><small>{{ identity.roles.map(role => roles[role] || role).join(' / ') }}</small></span><button :disabled="signing" @click="logout">切换身份</button></div>
        <span v-else class="service-line">服务热线 10000</span>
      </div>
    </header>

    <template v-if="identity">
      <nav class="business-nav role-business-nav" :class="{ 'role-full': currentRole === 'ROLE_AUDITOR' }" aria-label="岗位业务导航"><button v-for="(item, index) in roleUi.nav" :key="item" :class="{ active: index === activeNav }" :aria-current="index === activeNav ? 'page' : undefined" @click="chooseNav(index)">{{ item }}<span v-if="currentRole === 'ROLE_REVIEWER'">{{ tickets.filter(ticket => ticket.reviewerBucket === ['PENDING', 'PROCESSING', 'COMPLETED'][index]).length }}</span></button></nav>
      <aside v-if="currentRole !== 'ROLE_AUDITOR'" class="host-sidebar">
        <div class="sidebar-heading"><strong>{{ sidebarHeading }}</strong><span>{{ visibleTickets.length }}</span></div>
        <nav class="ticket-list" aria-label="宿主工单列表">
          <RouterLink v-for="ticket in visibleTickets" :key="ticket.id" :to="`/host/tickets/${ticket.id}/precheck`" class="ticket-nav">
            <div class="ticket-nav-top"><span class="ticket-type">{{ ticket.statusLabel }}</span><time>今日</time></div>
            <strong>{{ ticket.title }}</strong><p>{{ ticket.note }}</p>
            <div class="ticket-nav-bottom"><code>{{ ticket.id }}</code><span :class="{ attention: ticket.priority === '关注' }">{{ ticket.priority }}</span></div>
          </RouterLink>
          <p v-if="ticketLoading" class="ticket-list-state">正在加载工单…</p>
          <p v-else-if="ticketError" class="ticket-list-state error">{{ ticketError }} <button @click="loadTickets">重试</button></p>
          <p v-else-if="!visibleTickets.length" class="ticket-list-state">当前分类暂无工单</p>
        </nav>
      </aside>

      <div class="host-main" :class="{ 'full-width': currentRole === 'ROLE_AUDITOR' }">
        <header class="host-topbar">
          <div class="host-breadcrumb"><strong>{{ roleUi.crumb }}</strong><em>{{ currentTicket }}</em></div>
          <div class="host-actions"><button v-if="currentRole === 'ROLE_OPERATOR'" class="new-ticket-action" @click="startNewTicket">新建投诉</button><button class="plain-action" @click="$router.back()">返回</button></div>
        </header>
        <RouterView v-if="hasVisibleCurrent" v-slot="{ Component }"><component :is="Component" :key="route.fullPath" @workflow-change="workflowChanged" /></RouterView>
        <main v-else-if="!ticketLoading" class="host-empty-state"><h1>{{ sidebarHeading }}</h1><p>当前分类暂无可处理工单。</p><button v-if="currentRole === 'ROLE_OPERATOR'" @click="startNewTicket">新建投诉登记</button></main>
      </div>
    </template>

    <main v-else class="host-login">
      <section class="login-context">
        <div class="context-heading"><span>投诉处理中心</span><h1>智能核查辅助模块</h1><p>统一查询客户投诉、账单、订购与授权材料</p></div>
        <dl class="context-list">
          <div><dt>01</dt><dd><strong>材料自动归集</strong><span>工单流转后自动准备结构化核查材料</span></dd></div>
          <div><dt>02</dt><dd><strong>业务人员确认</strong><span>核查建议须由投诉处理专员核对确认</span></dd></div>
          <div><dt>03</dt><dd><strong>操作全程留痕</strong><span>保留证据快照、人工意见与操作记录</span></dd></div>
        </dl>
        <div class="context-boundary"><strong>使用边界</strong><p>本模块仅辅助材料整理与核查，不执行退费、订购变更等生产操作。</p></div>
      </section>
      <section class="login-area">
        <form class="host-login-card" @submit.prevent="login">
          <div class="login-card-heading"><span class="login-lock" aria-hidden="true">◈</span><div><h2>演示身份登录</h2><p>选择岗位后可直接进入对应工作台</p></div></div>
          <label for="host-account">岗位身份</label>
          <select id="host-account" v-model="account" :disabled="signing"><option value="operator">客服受理人员 · operator</option><option value="reviewer">投诉处理专员 · reviewer</option><option value="auditor">质检审核人员（只读）· auditor</option></select>
          <div v-if="loginError" role="alert" class="login-error">{{ loginError }}</div>
          <button :disabled="signing" class="login-submit">{{ signing ? '正在登录…' : '登录' }}</button>
          <div class="login-footnote"><strong>演示环境说明</strong> 客服受理人员负责创建与流转工单，投诉处理专员负责核查确认，质检审核人员仅负责只读审计，不构成串行审批。</div>
        </form>
        <p class="login-security">仅用于合成数据演示　·　生产环境应接入统一身份认证</p>
      </section>
    </main>
    <footer v-if="!identity" class="public-footer">© 2026 客户服务运营平台 <span>内部演示环境　|　合成数据　|　技术支持</span></footer>
  </div>
</template>
