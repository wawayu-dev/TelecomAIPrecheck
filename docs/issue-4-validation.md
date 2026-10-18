# Issue #4 验收记录

最终本地验证日期：2026-10-18。Java 17、Spring Boot 3.5.9、MySQL 8.4、Vue 3.5.44、Vue Router 4.6.4。所有业务数据均为合成样本。

## 自动化结果

| 验证 | 实际结果 |
| --- | --- |
| Maven verify，RUN_MYSQL_TESTS=true | 70 项：69 通过，0 失败，0 错误，1 跳过 |
| Vitest 页面/宿主/请求测试 | 23 项全部通过 |
| Vite 生产构建 | 通过 |
| Playwright Chromium | 2 条真实浏览器端到端流程通过 |

后端跳过项为显式开关控制的真实讯飞调用。本轮不重复调用付费模型。新增后端测试验证退出接口必须带 CSRF、退出后会话失效，以及预审/审核/审计时间与实际时刻一致。

前端用例覆盖：路由传入工单、宿主身份复用及切换、角色入口限制、预审防重复点击、人工修订与版本提交、模型失败转人工驳回、空字段拦截、409 刷新与草稿保留、网络失败不自动重发、工单切换忽略旧响应、快照/当前数据区分、接口失败重试、审计局部失败、模型文本按纯文本渲染和进行中状态轮询。

## 真实浏览器验收

测试连接独立 MySQL 测试 schema `issue4_ui_test`（端口 13306），后端端口 18081，Vite 代理端口 5174。使用后端真实 Basic 认证与 CSRF，不通过前端伪造角色，也不 mock 审核或数据库接口。

1. 桌面 1440×1050：访问 DEMO-003 宿主路由；operator 发起预审；展示金额不一致和 MODEL_NOT_CONFIGURED 转人工；切换 reviewer 后路由保持；修改摘要/建议并确认；切换 auditor 后只读查看人工结果与三条审计事件。未出现浏览器脚本异常。
2. 窄屏 390×844：DEMO-002 显示缺少授权；发起预审后切换 reviewer，填写依据并驳回；查看三条审计。验证文档宽度不超过视口，没有页面整体横向溢出。
3. 两条流程均断言预审时间接近实际时刻，且与证据快照采集时间相差不超过 5 秒，防止 8 小时时区偏差回归。

[桌面实际截图](evidence/issue-4/desktop-audit.png) · [窄屏实际截图](evidence/issue-4/mobile-review.png)

截图来自真实测试运行，并明确展示模型未配置的状态。AI 成功输出的展示与引用定位由前端合成 fixture 用例验证；实际讯飞成功联调证据保留在 Issue #2 报告，不将本轮浏览器失败转人工测试描述为新的真实模型成功。

## 复现

后端使用隔离的 MySQL 数据库与 mysql profile；配置 DB_URL、DB_USERNAME、DB_PASSWORD 和三个 DEMO_*_PASSWORD。DB_URL 使用 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`。启动后端到 18081，不配置 XFYUN_API_KEY/XFYUN_MODEL。

```sh
cd frontend
npm ci
npm test
npm run build
npx playwright install chromium
# 测试账号密码需与测试后端一致，通过环境注入，不写入仓库。
BACKEND_URL=http://127.0.0.1:18081 npm run test:e2e
```

浏览器测试会新增并审核合成工单，请使用独立测试库。DEMO-002/003 不应存在其他遗留的待审核轮次；成功运行可重复执行，生成新的轮次。截图与报告输出到忽略目录 test-results / playwright-report。不录制网络 trace，避免保存认证凭据。

## CI 与交付边界

GitHub Actions 保留 backend/frontend 两项任务，新增 browser 任务：下载本次已测试的后端构建，使用独立 MySQL 服务启动后端，安装 Chromium 并执行两条端到端流程；保存浏览器报告和页面截图。最终远端结果以提交对应的 Actions 记录为准。

页面与宿主接入已实现；完整演示环境部署、镜像发布、系统化 AI 效果评估和 30 分钟演示材料仍属于 Issue #5。模块没有真实退费、完整权限平台或多租户数据权限。
