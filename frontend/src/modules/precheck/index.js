import PrecheckPage from './PrecheckPage.vue'
export { PrecheckPage }
export { createPrecheckApi, precheckHostKey } from './api'
export function createPrecheckRoute({ path = '/tickets/:ticketId/precheck', name = 'telecom-precheck' } = {}) {
  return { path, name, component: PrecheckPage, props: route => ({ ticketId: String(route.params.ticketId) }) }
}
