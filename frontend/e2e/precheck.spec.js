import { test, expect } from '@playwright/test'

async function login(page, actor) {
  await page.getByLabel('岗位身份').selectOption(actor)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.locator('.host-identity strong')).toHaveText(actor)
}

test('桌面：DEMO-002 可阅读、复制并编辑 AI 辅助处理建议', async ({ page }, info) => {
  await page.setViewportSize({ width: 1440, height: 1050 })
  const errors = []; page.on('pageerror', error => errors.push(error.message))
  await page.goto('/host/tickets/DEMO-002/precheck')
  await login(page, 'reviewer')
  await expect(page).toHaveURL(/DEMO-002\/precheck$/)
  await expect(page.locator('.suggestion-panel')).toContainText('AI 生成 · 仅供参考')
  await expect(page.locator('.suggestion-panel')).toContainText('当前未发现计价异常，主动订购授权依据不足。')
  await expect(page.locator('.suggestion-panel')).toContainText('尚不能确认是否经过客户有效授权')
  await expect(page.locator('.suggestion-panel')).toContainText('ORDER-002')
  await expect(page.locator('.structured-review')).toHaveCount(0)
  await page.getByRole('button', { name: '复制 AI 处理建议' }).click()
  await expect(page.getByRole('status')).toContainText('AI 处理建议已复制')
  await page.getByRole('button', { name: '引用并编辑' }).click()
  await expect(page.locator('#ai-suggestion-editor')).toHaveValue(/初步调查结论/)
  await page.locator('#ai-suggestion-editor').fill('人工修改后的工单备注')
  await page.getByRole('button', { name: '复制编辑后内容' }).click()
  await expect(page.getByRole('status')).toContainText('AI 处理建议已复制')
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: info.outputPath('ai-suggestion-edit.png'), fullPage: true })
  expect(errors).toEqual([])
})

test('窄屏：AI 辅助建议布局不横向溢出', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/host/tickets/DEMO-002/precheck')
  await login(page, 'reviewer')
  await expect(page.locator('.compact-evidence')).toContainText('授权凭据')
  await expect(page.locator('.suggestion-panel')).toContainText('建议处理意见')
  await page.getByRole('button', { name: '引用并编辑' }).click()
  await expect(page.locator('#ai-suggestion-editor')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: info.outputPath('mobile-ai-suggestion.png'), fullPage: true })
})
