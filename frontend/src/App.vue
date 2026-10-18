<script setup>
import { computed, inject, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { precheckHostKey } from './modules/precheck'
import { errorText } from './modules/precheck/api'
const host = inject(precheckHostKey)
const route = useRoute()
const identity = host.identity
const account = ref('operator'), signing = ref(false), loginError = ref('')
const transitioning = ref(false), transitionNotice = ref(''), transitionRevision = ref(0)
const roles = { ROLE_OPERATOR: '客服受理人员', ROLE_REVIEWER: '投诉处理专员', ROLE_AUDITOR: '质检审核人员（只读）' }
const currentTicket = computed(() => typeof route.params.ticketId === 'string' ? route.params.ticketId : '')
const canTransition = computed(() => identity.value?.roles.includes('ROLE_OPERATOR') && currentTicket.value)
const tickets = [{ id: 'DEMO-001', label: '视频会员扣费争议', note: '账单与授权材料齐全', level: '普通' }, { id: 'DEMO-002', label: '未确认的业务订购', note: '缺少授权凭据', level: '关注' }, { id: 'DEMO-003', label: '月费金额不一致', note: '账单与订购价格存在差异', level: '普通' }]
async function login() {
  if (signing.value) return
  signing.value = true; loginError.value = ''; transitionNotice.value = ''
  try { await host.login(account.value) }
  catch (error) { loginError.value = error.status === 401 ? '演示身份登录未启用，请检查后端配置。' : errorText(error) }
  finally { signing.value = false }
}
async function logout() {
  if (signing.value) return
  signing.value = true; loginError.value = ''
  try { await host.logout() }
  catch { loginError.value = '本地身份已清除，服务端退出未确认。请重新登录。' }
  finally { transitionNotice.value = ''; signing.value = false }
}
async function transitionTicket() {
  if(transitioning.value || !canTransition.value) return
  transitioning.value=true; transitionNotice.value=''
  try {
    const eventId=`WEB-${globalThis.crypto?.randomUUID?.() || Date.now()}`
    const result=await host.api.transition(currentTicket.value,{ eventId,ticketVersion:Date.now()*1000 })
    transitionNotice.value=result.precheckAccepted ? '工单已进入投诉队列，AI 核查任务已在后台接收。' : '工单已正常流转；AI 任务接入失败，可继续人工处理。'
    transitionRevision.value++
  } catch(error) { transitionNotice.value=errorText(error) }
  finally { transitioning.value=false }
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
        <span class="system-status"><i></i>系统运行正常</span>
        <span>业务支持</span><span>帮助中心</span>
        <div v-if="identity" class="header-user host-identity"><span class="avatar">{{ identity.actorId.slice(0, 1).toUpperCase() }}</span><span><strong>{{ identity.actorId }}</strong><small>{{ identity.roles.map(role => roles[role] || role).join(' / ') }}</small></span><button :disabled="signing" @click="logout">切换身份</button></div>
        <span v-else class="service-line">服务热线 10000</span>
      </div>
    </header>

    <template v-if="identity">
      <nav class="business-nav" aria-label="主业务导航"><a>首页</a><a>客户视图</a><a class="active">投诉处理</a><a>业务查询</a><a>知识库</a><a>统计报表</a></nav>
      <aside class="host-sidebar">
        <div class="sidebar-heading"><strong>投诉工单</strong><span>待办 3</span></div>
        <div class="sidebar-filter"><button class="active">我的待办</button><button>全部工单</button></div>
        <nav class="ticket-list" aria-label="宿主工单列表">
          <RouterLink v-for="ticket in tickets" :key="ticket.id" :to="`/host/tickets/${ticket.id}/precheck`" class="ticket-nav">
            <div class="ticket-nav-top"><span class="ticket-type">费用争议</span><time>今日</time></div>
            <strong>{{ ticket.label }}</strong><p>{{ ticket.note }}</p>
            <div class="ticket-nav-bottom"><code>{{ ticket.id }}</code><span :class="{ attention: ticket.level === '关注' }">{{ ticket.level }}</span></div>
          </RouterLink>
        </nav>
        <div class="sidebar-notice"><strong>数据说明</strong><p>当前环境使用合成业务数据，不连接生产客户及账务系统。</p></div>
      </aside>

      <div class="host-main">
        <header class="host-topbar">
          <div class="host-breadcrumb"><span>客户服务</span><i>/</i><span>投诉处理</span><i>/</i><strong>工单核查</strong><em>{{ currentTicket }}</em></div>
          <div class="host-actions"><button class="plain-action" @click="$router.back()">返回</button><button v-if="canTransition" class="host-transition" :disabled="transitioning" @click="transitionTicket">{{ transitioning ? '正在流转…' : '流转至投诉队列' }}</button></div>
        </header>
        <div v-if="transitionNotice" class="host-transition-notice" role="status"><strong>业务提示</strong>{{ transitionNotice }}</div>
        <RouterView :key="`${route.fullPath}:${transitionRevision}`" />
        <footer class="host-footer"><span>客户服务运营平台 · 智能核查辅助模块</span><span>数据分级：内部使用　|　操作全程留痕</span></footer>
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
