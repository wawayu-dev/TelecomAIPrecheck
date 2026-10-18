<script setup>
import { inject, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { precheckHostKey } from './modules/precheck'
import { errorText } from './modules/precheck/api'
const host = inject(precheckHostKey)
const identity = host.identity
const account = ref('operator'), password = ref(''), signing = ref(false), loginError = ref('')
const roles = { ROLE_OPERATOR: '工单操作员', ROLE_REVIEWER: '业务审核员', ROLE_AUDITOR: '审计员' }
const tickets = [{ id: 'DEMO-001', label: '视频会员扣费争议', note: '账单与授权材料齐全' }, { id: 'DEMO-002', label: '未确认的业务订购', note: '缺少授权凭据' }, { id: 'DEMO-003', label: '月费金额不一致', note: '账单与订购价格存在差异' }]
async function login() {
  if (signing.value) return
  signing.value = true; loginError.value = ''
  try { await host.login(account.value, password.value) }
  catch (error) { loginError.value = error.status === 401 ? '账号或密码不正确，请核对宿主演示账号。' : errorText(error) }
  finally { password.value = ''; signing.value = false }
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
  <div class="demo-host">
    <aside class="host-sidebar"><a class="brand" href="/"><span class="brand-symbol"><i></i><i></i><i></i><i></i></span><div>Telecom<span>AI PRECHECK</span></div></a><div class="workspace-label">模拟宿主 · 工单工作台</div><div class="sidebar-section-title">扣费争议工单 <span>03</span></div><nav aria-label="宿主工单列表"><RouterLink v-for="ticket in tickets" :key="ticket.id" :to="`/host/tickets/${ticket.id}/precheck`" class="ticket-nav"><span class="nav-icon">▤</span><div><small>{{ ticket.id }}</small><strong>{{ ticket.label }}</strong><p>{{ ticket.note }}</p></div></RouterLink></nav><div class="sidebar-bottom"><span class="demo-dot"></span>合成业务场景<span>DEMO ENVIRONMENT</span><p>AI 提供建议<br>业务人员保留决定权</p></div></aside>
    <div class="host-main"><header class="host-topbar"><div class="host-breadcrumb">客户服务 <span>/</span> 工单处理 <span>/</span> <strong>AI 预审</strong></div><div class="host-identity" v-if="identity"><span class="avatar">{{ identity.actorId.slice(0, 1).toUpperCase() }}</span><div><strong>{{ identity.actorId }}</strong><small>{{ identity.roles.map(role => roles[role] || role).join(' / ') }}</small></div><button :disabled="signing" @click="logout">切换身份</button></div><span v-else class="host-topbar-note">复用宿主身份 · 独立业务模块</span></header>
      <RouterView v-if="identity" />
      <main v-else class="host-login"><div class="login-intro"><p>TELECOM AI PRECHECK</p><h1>每一条处理建议，<br>都有证据可循。</h1><div>在现有工单中完成证据核验、AI 辅助预审与人工复核。<br>已有业务上下文自动传入，全流程可追溯。</div><ol><li><b>01</b>读取工单与关联证据</li><li><b>02</b>生成结构化预审建议</li><li><b>03</b>人工复核并记录操作</li></ol><small>当前为合成数据演示，不连接运营商生产系统。</small></div><form class="host-login-card" @submit.prevent="login"><span class="login-label">HOST SESSION</span><h2>登录模拟宿主</h2><p>登录后，预审模块复用此身份与权限。</p><label for="host-account">演示身份</label><select id="host-account" v-model="account" :disabled="signing"><option value="operator">工单操作员 · operator</option><option value="reviewer">业务审核员 · reviewer</option><option value="auditor">审计员 · auditor</option></select><label for="host-password">账户密码</label><input id="host-password" v-model="password" type="password" required autocomplete="current-password" :disabled="signing" placeholder="输入当前演示账户的密码"><div v-if="loginError" role="alert" class="login-error">{{ loginError }}</div><button :disabled="signing" class="login-submit">{{ signing ? '正在验证身份…' : '登录并进入工单 →' }}</button><div class="login-footnote">操作员发起预审，审核员复核建议，审计员查看记录。密码由演示环境管理员提供。</div></form></main>
      <footer class="host-footer">Telecom AI Precheck <span>轻量嵌入 · 人工决策 · 过程可追溯</span></footer>
    </div>
  </div>
</template>
