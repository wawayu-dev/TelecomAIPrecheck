import { h } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { createPrecheckRoute } from './modules/precheck'
export function createHostRouter(history = createWebHistory()) {
  return createRouter({ history, routes: [
    { path: '/', redirect: '/host/tickets/DEMO-001/precheck' },
    createPrecheckRoute({ path: '/host/tickets/:ticketId([A-Za-z0-9_-]{1,64})/precheck' }),
    { path: '/:pathMatch(.*)*', component: { render: () => h('main', { class: 'host-not-found' }, [h('h1', '页面不存在'), h('p', '请从左侧选择工单。')]) } },
  ] })
}
