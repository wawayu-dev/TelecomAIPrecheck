# Issue #4：预审页面与宿主集成

## 当前功能

预审模块提供一个业务页面：自动读取宿主工单与证据、发起预审、查看结构化摘要与规则提示、定位证据引用、修订并确认/驳回人工建议，以及查看本轮操作时间线。支持历史轮次、证据快照与宿主当前数据切换、加载和失败状态、重复点击防护、版本冲突刷新及草稿保留。

模拟宿主位于 `frontend/src/App.vue` 与 `frontend/src/host/`，路由为 `/host/tickets/:ticketId/precheck`。左侧三条工单均为合成场景；选中工单后自动读取业务上下文，不需要重新输入投诉、账单或授权记录。页面默认以本轮不可变快照展示历史证据，切换到当前宿主数据时有明确提示。

身份只在模拟宿主登录一次。operator 对应客服受理人员，负责工单创建与流转；reviewer 对应投诉处理专员，负责修订、确认或退回核查材料；auditor 对应质检审核人员，仅只读查询与审计，不是串行审批节点。前端按后端返回的身份控制操作入口，真正的权限、状态及并发检查仍由后端执行。客服受理人员不能查看审计，投诉处理专员和质检审核人员可以。

## 本机启动

```sh
# 仓库根目录：保留 MySQL 命名卷
scripts/demo.sh up
scripts/demo.sh backend
# 另一个终端
cd frontend
npm ci
npm run dev
```

打开 http://127.0.0.1:5173/host/tickets/DEMO-001/precheck 。模拟宿主允许从 operator、reviewer、auditor 三个白名单岗位中选择并一键建立本机会话，不向浏览器下发或硬编码密码。该入口默认关闭，仅由 `scripts/demo.sh backend` 为本地合成数据演示显式开启；其他部署必须保持关闭并改用宿主统一认证。不要把密码、数据库凭据或模型 Key 放进 VITE_* 变量。

完整页面需要 mysql profile；默认 mock profile 只提供只读上下文 API，不提供本页面需要的身份和预审接口。无模型配置时，仍可实际验证失败转人工、人工审核和审计；不能将此描述成真实模型调用成功。配置 XFYUN_API_KEY 和 XFYUN_MODEL 后，新一轮预审才调用真实模型。

登录会调用 `POST /api/demo-session`，由后端白名单角色建立演示 Session；切换身份会调用 `POST /api/session/logout`（CSRF 保护），清除服务端会话及本机内存身份，工单路由保持不变。刷新浏览器需要重新选择身份，不写入 localStorage/sessionStorage。

## 嵌入现有 Vue 3 + Vue Router 4 宿主

业务模块位于 `frontend/src/modules/precheck/`，不依赖模拟宿主的登录表单、工单列表、路由前缀或密码配置。模块组件接收 ticketId，并通过 Vue provide/inject 获取宿主提供的 identity 与 API。

```js
import { readonly, computed } from 'vue'
import { createPrecheckRoute, createPrecheckApi, precheckHostKey } from './modules/precheck'

// existingSession / hostRequest / app / router 均由宿主提供。
// hostRequest 返回已解析的 JSON（空响应返回 null）；负责认证、CSRF、Cookie 和错误转换。
const host = {
  identity: readonly(computed(() => existingSession.user ? {
    actorId: existingSession.user.opaqueId,
    roles: existingSession.user.authorities,
  } : null)),
  api: createPrecheckApi(hostRequest),
}
app.provide(precheckHostKey, host)
router.addRoute(createPrecheckRoute({
  path: '/work-orders/:ticketId/precheck',
  name: 'host-telecom-precheck',
}))
```

也可直接使用 `<PrecheckPage :ticket-id="selectedTicketId" />`，不必让模块拥有整个应用的 Router。宿主负责未登录页面及会话刷新；模块不自行注册账号、管理角色或读取密码。模块 CSS 以 `.precheck-module` 为边界，模拟宿主的 `style.css` 不需要引入真实宿主。

### 请求适配器契约

`hostRequest(path, options)` 接收相对 API 路径和 `{ method?, body?, signal? }`。GET 默认无 body；POST 的 body 为对象，由宿主序列化。返回 Promise，结果为 JSON 对象/数组或 null。非 2xx 必须抛出含 `status`、`code`、可选 `traceId` 的错误；网络错误可直接抛出。模块只显示白名单错误消息，不输出任意服务端原文。

读取接口传入 AbortSignal，宿主应转交 fetch/HTTP 客户端。组件还会检查请求代次，因此旧工单的迟到响应不会覆盖新工单。写请求不自动重发；网络失败时先刷新状态，再提示核查，避免把响应丢失当成未提交。审核 409 后刷新最新版本，保留编辑副本，不能自动覆盖他人结果。

identity 为响应式 Ref，值为 null 或 `{ actorId, roles }`；角色为 ROLE_OPERATOR / ROLE_REVIEWER / ROLE_AUDITOR。禁止让前端自报角色替代服务端认证。后端仍须接入宿主 SecurityContext，详见 [Issue #3 权限契约](issue-3-review.md)。

### API 与服务端接入

沿用 TicketContextProvider 提供业务上下文，不查询或更新宿主具体表。读取/预审/审核/审计 API 见 [Issue #2](issue-2-precheck.md) 和 [Issue #3](issue-3-review.md)。模拟宿主另使用：

- `GET /api/session`：身份、角色、CSRF token/header。
- `POST /api/demo-session`：仅演示环境开放，按白名单岗位建立 Session，不接受任意角色。
- `POST /api/session/logout`：退出会话，成功 204。

真实宿主可用既有登录及退出接口替代上述模拟宿主接口，并向模块注入已有 request。跨系统部署时认证、CSRF、路径代理和工单数据权限需由宿主适配，不承诺无修改接入所有系统。使用 history 路由时，Web 服务器需把前端页面路径回退至 index.html，API 路径单独代理到 Spring Boot。

### 时间显示

数据库连接统一使用 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`，与 SQL 的 UTC_TIMESTAMP 保持一致；前端根据浏览器本地时区显示。已修复旧连接配置把 UTC 数据按 Asia/Shanghai 解读而产生的 8 小时偏差。自定义 DB_URL 也应使用以上设置。此变更修正读取解释，不改写既有证据或审核记录。

## 验证与边界

- `npm test`：组件、请求适配器、宿主路由与身份复用测试。
- `npm run build`：生产构建。
- `npm run test:e2e`：真实 Chromium + 后端 + MySQL 的桌面确认和窄屏驳回流程；详见 [验收记录](issue-4-validation.md)。

模块只提供预审所需的业务页，不是完整客服平台。人工确认仅确认处理建议，没有退费执行入口。前端阻止同页面重复点击；跨标签页/跨客户端的审核竞争由后端乐观锁裁决。预审创建没有全局幂等键，异常响应时不会自动重试，需先查历史。多租户、逐工单数据权限、生产单点登录、完整异常访问日志仍由真实宿主接入方案补足。

实现参考：[Vue Router 动态路由](https://router.vuejs.org/guide/essentials/dynamic-matching.html)、[Vue provide/inject](https://vuejs.org/guide/components/provide-inject.html)、[MySQL Connector/J 时间处理](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)。
