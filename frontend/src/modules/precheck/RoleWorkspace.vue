<script setup>
import { computed, inject } from 'vue'
import { precheckHostKey } from './api'
import OperatorWorkspace from './OperatorWorkspace.vue'
import PrecheckPage from './PrecheckPage.vue'
import AuditWorkspace from './AuditWorkspace.vue'
const props = defineProps({ ticketId: { type: String, required: true } })
const emit = defineEmits(['workflow-change'])
const host = inject(precheckHostKey)
const role = computed(() => host.identity.value?.roles[0] || '')
</script>

<template>
  <OperatorWorkspace v-if="role === 'ROLE_OPERATOR'" :ticket-id="props.ticketId" @workflow-change="emit('workflow-change', $event)" />
  <PrecheckPage v-else-if="role === 'ROLE_REVIEWER'" :ticket-id="props.ticketId" @workflow-change="emit('workflow-change', $event)" />
  <AuditWorkspace v-else-if="role === 'ROLE_AUDITOR'" :ticket-id="props.ticketId" />
  <main v-else class="role-empty"><h1>当前身份无可用工作台</h1><p>请切换为已配置的业务岗位。</p></main>
</template>
