<script setup>
import { computed, inject, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { precheckHostKey } from './modules/precheck'
import { errorText } from './modules/precheck/api'
const host = inject(precheckHostKey)
const route = useRoute()
const identity = host.identity
const account = ref('operator'), password = ref(''), signing = ref(false), loginError = ref('')
const transitioning = ref(false), transitionNotice = ref(''), transitionRevision = ref(0)
const roles = { ROLE_OPERATOR: '客服受理人员', ROLE_REVIEWER: '投诉处理专员', ROLE_AUDITOR: '质检审核人员（只读）' }
const currentTicket = computed(() => typeof route.params.ticketId === 'string' ? route.params.ticketId : '')
const canTransition = computed(() => identity.value?.roles.includes('ROLE_OPERATOR') && currentTicket.value)
const tickets = [{ id: 'DEMO-001', label: '视频会员扣费争议', note: '账单与授权材料齐全' }, { id: 'DEMO-002', label: '未确认的业务订购', note: '缺少授权凭据' }, { id: 'DEMO-003', label: '月费金额不一致', note: '账单与订购价格存在差异' }]
async function login() {
  if (signing.value) return
  signing.value = true; loginError.value = ''; transitionNotice.value = ''
  try { await host.login(account.value, password.value) }
  catch (error) { loginError.value = error.status === 401 ? '账号或密码不正确，请核对宿主演示账号。' : errorText(error) }
  finally { password.value = ''; signing.value = false }
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
  <div class="demo-host">
    <aside class="host-sidebar"><a class="brand" href="/"><span class="brand-symbol"><i></i><i></i><i></i><i></i></span><div>Telecom<span>AI PRECHECK</span></div></a><div class="workspace-label">模拟宿主 · 工单工作台</div><div class="sidebar-section-title">扣费争议工单 <span>03</span></div><nav aria-label="宿主工单列表"><RouterLink v-for="ticket in tickets" :key="ticket.id" :to="`/host/tickets/${ticket.id}/precheck`" class="ticket-nav"><span class="nav-icon">▤</span><div><small>{{ ticket.id }}</small><strong>{{ ticket.label }}</strong><p>{{ ticket.note }}</p></div></RouterLink></nav><div class="sidebar-bottom"><span class="demo-dot"></span>合成业务场景<span>DEMO ENVIRONMENT</span><p>AI 提供建议<br>业务人员保留决定权</p></div></aside>
    <div class="host-main"><header class="host-topbar"><div class="host-breadcrumb">客户服务 <span>/</span> 投诉处理 <span>/</span> <strong>AI 核查工作台</strong></div><div class="host-identity" v-if="identity"><button v-if="canTransition" class="host-transition" :disabled="transitioning" @click="transitionTicket">{{ transitioning ? '正在流转…' : '模拟流转至投诉队列' }}</button><span class="avatar">{{ identity.actorId.slice(0, 1).toUpperCase() }}</span><div><strong>{{ identity.actorId }}</strong><small>{{ identity.roles.map(role => roles[role] || role).join(' / ') }}</small></div><button :disabled="signing" @click="logout">切换身份</button></div><span v-else class="host-topbar-note">复用宿主身份 · 独立业务模块</span></header>
      <div v-if="transitionNotice && identity" class="host-transition-notice" role="status">{{ transitionNotice }}</div>
      <RouterView v-if="identity" :key="`${route.fullPath}:${transitionRevision}`" />
      <main v-else class="host-login"><div class="login-intro"><p>TELECOM AI PRECHECK</p><h1>接单时，核查材料<br>已经在路上。</h1><div>工单流转后后台自动核验证据并生成 AI 材料。<br>投诉处理专员打开工单即可核对、修正和确认。</div><ol><li><b>01</b>客服受理并流转投诉工单</li><li><b>02</b>后台准备结构化核查材料</li><li><b>03</b>投诉专员确认并完整留痕</li></ol><small>当前为合成数据演示，不连接运营商生产系统。</small></div><form class="host-login-card" @submit.prevent="login"><span class="login-label">HOST SESSION</span><h2>登录模拟宿主</h2><p>登录后，AI 模块沿用宿主身份与权限。</p><label for="host-account">演示身份</label><select id="host-account" v-model="account" :disabled="signing"><option value="operator">客服受理人员 · operator</option><option value="reviewer">投诉处理专员 · reviewer</option><option value="auditor">质检审核人员（只读）· auditor</option></select><label for="host-password">账户密码</label><input id="host-password" v-model="password" type="password" required autocomplete="current-password" :disabled="signing" placeholder="输入当前演示账户的密码"><div v-if="loginError" role="alert" class="login-error">{{ loginError }}</div><button :disabled="signing" class="login-submit">{{ signing ? '正在验证身份…' : '登录并进入工单 →' }}</button><div class="login-footnote">客服受理人员模拟工单创建与流转，投诉处理专员核查并确认材料，质检审核人员仅负责只读审计，不构成串行审批。密码由演示环境管理员提供。</div></form></main>
      <footer class="host-footer">Telecom AI Precheck <span>轻量嵌入 · 人工决策 · 过程可追溯</span></footer>
    </div>
  </div>
</template>
