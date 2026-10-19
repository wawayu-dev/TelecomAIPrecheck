import { test, expect } from '@playwright/test'

async function login(page, actor) {
  await page.getByLabel('岗位身份').selectOption(actor)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.locator('.host-identity strong')).toHaveText(actor)
}

async function openReviewableTicket(page, ticketId = 'DEMO-002') {
  await page.goto(`/host/tickets/${ticketId}/precheck`)
  await login(page, 'operator')
  const ticketState = () => page.evaluate(async id => {
    const response = await fetch('/api/mock-host/tickets', {
      headers: { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
      credentials: 'same-origin',
    })
    const tickets = await response.json()
    return tickets.find(ticket => ticket.id === id)
  }, ticketId)
  await expect.poll(async () => (await ticketState())?.id).toBe(ticketId)
  const submit = page.getByRole('button', { name: '提交投诉工单' })
  if (!(await ticketState()).submitted) {
    await expect(submit).toBeVisible()
    const accepted = page.waitForResponse(response => response.request().method() === 'POST'
      && response.url().includes(`/api/mock-host/tickets/${ticketId}/complaint-queue`))
    await submit.click()
    const payload = await (await accepted).json()
    expect(payload.transitioned).toBe(true)
    expect(payload.precheckAccepted).toBe(true)
  }
  await expect.poll(async () => (await ticketState())?.reviewerBucket).toBe('PENDING')
  await page.getByRole('button', { name: '切换身份' }).click()
  await login(page, 'reviewer')
  await expect(page).toHaveURL(new RegExp(`${ticketId}/precheck$`))
  await expect(page.locator('.compact-evidence')).toBeVisible()
}

test('桌面：DEMO-002 模型未配置时安全转人工处理', async ({ page }, info) => {
  await page.setViewportSize({ width: 1440, height: 1050 })
  const errors = []; page.on('pageerror', error => errors.push(error.message))
  await openReviewableTicket(page)
  await expect(page.locator('.suggestion-panel')).toContainText('AI 生成 · 仅供参考')
  await expect(page.locator('.suggestion-panel')).toContainText('智能核查未完成')
  await expect(page.locator('.suggestion-panel')).toContainText('模型未配置')
  await expect(page.locator('.suggestion-panel')).toContainText('当前没有可用的 AI 处理建议')
  await expect(page.locator('.structured-review')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '引用并编辑' })).toHaveCount(0)
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: info.outputPath('manual-fallback.png'), fullPage: true })
  expect(errors).toEqual([])
})

test('窄屏：证据与人工兜底布局不横向溢出', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await openReviewableTicket(page)
  await expect(page.locator('.compact-evidence')).toContainText('授权凭据')
  await expect(page.locator('.suggestion-panel')).toContainText('智能核查未完成')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: info.outputPath('mobile-manual-fallback.png'), fullPage: true })
})
