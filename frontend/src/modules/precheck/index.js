import PrecheckPage from './PrecheckPage.vue'
import RoleWorkspace from './RoleWorkspace.vue'
export { PrecheckPage }
export { createPrecheckApi, precheckHostKey } from './api'
export function createPrecheckRoute({ path = '/tickets/:ticketId/precheck', name = 'telecom-precheck' } = {}) {
  return { path, name, component: RoleWorkspace, props: route => ({ ticketId: String(route.params.ticketId) }) }
}
