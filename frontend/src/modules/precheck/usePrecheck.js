import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { errorText } from './api'

export function usePrecheck(ticketId, host) {
  const context = ref(null), history = ref([]), selected = ref(null), review = ref(null), audit = ref([])
  const loading = ref(false), writing = ref(false), paging = ref(false), moreHistory = ref(false), moreAudit = ref(false)
  const error = ref(''), auditError = ref(''), notice = ref(''), preservedDraft = ref(null)
  const draft = reactive({
    evidenceConclusion: 'INSUFFICIENT', nextAction: 'REQUEST_EVIDENCE',
    summary: '', recommendation: 'VERIFY_AUTHORIZATION', reason: '',
  })
  const roles = computed(() => host.identity.value?.roles || [])
  const canAudit = computed(() => roles.value.includes('ROLE_AUDITOR'))
  const canReview = computed(() => roles.value.includes('ROLE_REVIEWER') && selected.value?.run.currentResult !== false && selected.value?.run.status === 'PENDING_REVIEW' && !review.value)
  const canRetry = computed(() => roles.value.includes('ROLE_REVIEWER')
    && selected.value?.run.currentResult !== false && selected.value?.run.taskStatus === 'FAILED'
    && selected.value?.run.status === 'PENDING_REVIEW')
  const canStart = computed(() => roles.value.includes('ROLE_OPERATOR') && !!context.value && !history.value.some(v => ['ANALYZING', 'PENDING_REVIEW'].includes(v.run.status)))
  let scope = 0, read = 0, controller, poll
  const busy = computed(() => loading.value || writing.value || paging.value)
  function cancelReads() { read++; controller?.abort(); clearTimeout(poll) }
  function clearDetail() { selected.value = null; review.value = null; audit.value = []; auditError.value = ''; moreAudit.value = false }
  function populateDraft() {
    const savedConclusion = review.value?.evidenceConclusion
    const savedAction = review.value?.nextAction
    const authorizationAvailable = selected.value?.evidenceSnapshot?.authorizations?.status === 'AVAILABLE'
    draft.evidenceConclusion = savedConclusion || (selected.value?.result?.evidenceGap || !authorizationAvailable
      || selected.value?.run.callStatus === 'FAILED' ? 'INSUFFICIENT' : 'SUFFICIENT')
    draft.nextAction = savedAction || (review.value?.decision === 'CONFIRMED' ? 'SUBMIT_CONCLUSION'
      : review.value?.decision === 'ESCALATED' ? 'ESCALATE' : 'REQUEST_EVIDENCE')
    draft.summary = review.value?.summary || selected.value?.result?.summary || ''
    draft.recommendation = review.value?.recommendation || selected.value?.result?.recommendation || 'MANUAL_REVIEW'
    const gap = selected.value?.result?.evidenceGap
    const action = selected.value?.result?.suggestedAction
    draft.reason = review.value?.reason || [gap && `关键疑点：${gap}`, action && `建议操作：${action}`].filter(Boolean).join('\n')
  }
  async function detail(id, token, signal) {
    const [run, saved] = await Promise.all([host.api.run(id, signal), host.api.review(id, signal)])
    if (token !== read) return
    selected.value = run; review.value = saved; populateDraft()
    if (canAudit.value) {
      try {
        const events = await host.api.timeline(id, 0, signal)
        if (token !== read) return
        audit.value = events; moreAudit.value = events.length === 50
      } catch (cause) { if (token === read) auditError.value = errorText(cause) }
    }
    if (token === read && run.run.status === 'ANALYZING') poll = setTimeout(() => pollRun(id, token), 4000)
  }
  async function pollRun(id, token) {
    try {
      const run = await host.api.run(id)
      if (token !== read || selected.value?.run.id !== id) return
      selected.value = run
      if (run.run.status === 'ANALYZING') poll = setTimeout(() => pollRun(id, token), 4000)
      else await refresh(id)
    } catch {
      if (token === read && selected.value?.run.id === id) poll = setTimeout(() => pollRun(id, token), 4000)
    }
  }
  async function refresh(preferredId = selected.value?.run.id) {
    cancelReads(); const token = read
    controller = new AbortController(); const signal = controller.signal
    loading.value = true; error.value = ''; clearDetail()
    try {
      const [ticket, rows, current] = await Promise.all([host.api.context(ticketId(), signal),
        host.api.history(ticketId(), 0, signal), host.api.current(ticketId(), signal)])
      if (token !== read) return
      context.value = ticket; history.value = rows; moreHistory.value = rows.length === 20
      const id = preferredId || current?.run.id
      if (id) await detail(id, token, signal)
    } catch (cause) { if (token === read) { context.value = null; history.value = []; clearDetail(); error.value = errorText(cause) } }
    finally { if (token === read) loading.value = false }
  }
  async function select(id) {
    if (busy.value) return
    cancelReads(); const token = read; controller = new AbortController()
    loading.value = true; error.value = ''; notice.value = ''; preservedDraft.value = null; clearDetail()
    try { await detail(id, token, controller.signal) }
    catch (cause) { if (token === read) error.value = errorText(cause) }
    finally { if (token === read) loading.value = false }
  }
  async function loadMore(kind) {
    if (busy.value) return
    const token = read; paging.value = true
    try {
      if (kind === 'history') {
        const rows = await host.api.history(ticketId(), history.value.length, controller?.signal)
        if (token !== read) return
        history.value = [...new Map([...history.value, ...rows].map(row => [row.run.id, row])).values()]
        moreHistory.value = rows.length === 20
      } else {
        const rows = await host.api.timeline(selected.value.run.id, audit.value.at(-1)?.event.id || 0, controller?.signal)
        if (token !== read) return
        audit.value.push(...rows); moreAudit.value = rows.length === 50
      }
    } catch (cause) { if (token === read) error.value = errorText(cause) }
    finally { if (token === read) paging.value = false }
  }
  async function start() {
    if (busy.value || !canStart.value) return
    const currentScope = scope; writing.value = true; error.value = ''; notice.value = ''; preservedDraft.value = null
    try {
      const created = await host.api.start(ticketId())
      if (currentScope !== scope) return
      await refresh(created.run.id)
      if (currentScope === scope) notice.value = '本轮预审已保存。处理意见须由审核人员复核。'
    } catch (cause) {
      if (currentScope !== scope) return
      await refresh()
      if (currentScope === scope) error.value = `${errorText(cause)} 提交结果可能尚未返回，请先核对历史记录；系统不会自动重发。`
    } finally { if (currentScope === scope) writing.value = false }
  }
  async function submit(decision) {
    if (busy.value || !canReview.value) return false
    if (!draft.summary.trim() || !draft.reason.trim() || draft.summary.length > 1000 || draft.reason.length > 1000) {
      error.value = '请填写核查依据与处理说明，且不超过 1000 字。'; return false
    }
    const currentScope = scope, id = selected.value.run.id
    const compatible = (draft.evidenceConclusion === 'SUFFICIENT' && draft.nextAction === 'SUBMIT_CONCLUSION' && decision === 'CONFIRMED')
      || (draft.evidenceConclusion !== 'SUFFICIENT' && draft.nextAction === 'REQUEST_EVIDENCE' && decision === 'REJECTED')
      || (draft.nextAction === 'ESCALATE' && decision === 'ESCALATED')
    if (!compatible) {
      error.value = '证据核查结论与下一步处理不一致，请调整后再提交。'; return false
    }
    const body = { expectedVersion: selected.value.run.version, decision, ...draft }
    writing.value = true; error.value = ''; notice.value = ''; preservedDraft.value = null
    try {
      await host.api.submit(id, body)
      if (currentScope !== scope) return
      await refresh(id)
      if (currentScope === scope) notice.value = decision === 'CONFIRMED' ? '核查结论已提交，工单已转入完成状态。'
        : decision === 'ESCALATED' ? '争议已转交复核，核查决定及依据已留痕。'
          : '补证要求已提交，工单已转入待补证状态。'
      return currentScope === scope
    } catch (cause) {
      if (currentScope !== scope) return
      await refresh(id)
      if (currentScope === scope) {
        preservedDraft.value = body
        error.value = `${errorText(cause)} 未提交的编辑内容保留在下方，请核对最新状态，不会自动重提。`
      }
      return false
    } finally { if (currentScope === scope) writing.value = false }
  }
  async function retry() {
    if (busy.value || !canRetry.value) return
    const currentScope=scope, id=selected.value.run.id, expectedVersion=selected.value.run.version
    writing.value=true; error.value=''; notice.value=''; preservedDraft.value=null
    try {
      await host.api.retry(id,expectedVersion)
      if(currentScope!==scope) return
      await refresh(id)
      if(currentScope===scope) notice.value='已授权重新执行 AI 核查；原工单仍可继续人工处理。'
    } catch(cause) {
      if(currentScope!==scope) return
      await refresh()
      if(currentScope===scope) error.value=errorText(cause)
    } finally { if(currentScope===scope) writing.value=false }
  }
  watch(() => [ticketId(), host.identity.value?.actorId, roles.value.join(',')], () => {
    scope++; cancelReads(); context.value = null; history.value = []; clearDetail()
    writing.value = false; paging.value = false; loading.value = false; error.value = ''; notice.value = ''; preservedDraft.value = null
    if (host.identity.value) refresh(undefined)
  }, { immediate: true })
  onBeforeUnmount(() => { scope++; cancelReads() })
  return { context, history, selected, review, audit, draft, loading, writing, paging, busy, moreHistory, moreAudit,
    error, auditError, notice, preservedDraft, canAudit, canReview, canRetry, canStart, refresh, select, loadMore, start, retry, submit }
}
