# API 接口文档

## 1. 通用约定

- Compose 访问入口：`http://127.0.0.1:8088`
- 数据格式：`application/json; charset=UTF-8`
- 时间格式：ISO-8601 UTC，例如 `2026-10-18T12:00:00Z`
- 工单 ID、事件 ID：`[A-Za-z0-9_-]{1,64}`
- 预审 ID：小写 UUID
- 所有业务请求均返回或关联服务端 Trace ID。

除健康检查和启用后的演示登录接口外，接口都需要认证。浏览器写请求还需携带 `/api/session` 返回的 CSRF Header 和 Token。内部宿主事件接口使用 `HOST` 服务身份并豁免 CSRF。

## 2. 角色说明

| 标记 | 角色 |
| --- | --- |
| O | `OPERATOR` 客服受理人员 |
| R | `REVIEWER` 投诉处理专员 |
| A | `AUDITOR` 质检审核人员 |
| H | `HOST` 宿主系统服务身份 |

## 3. 接口总览

| Method | Path | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/actuator/health` | 公开 | 健康检查 |
| POST | `/api/demo-session` | 演示环境公开 | 选择白名单岗位并建立 Session |
| GET | `/api/session` | O/R/A | 当前身份和 CSRF 信息 |
| POST | `/api/session/logout` | O/R/A | 注销 Session |
| GET | `/api/tickets/{ticketId}/context` | O/R/A | 读取宿主工单及证据 |
| GET | `/api/mock-host/tickets` | O/R/A | 查询演示宿主工单及统一工作流状态 |
| POST | `/api/mock-host/tickets/{ticketId}/complaint-queue` | O | 模拟宿主流转并投递事件 |
| POST | `/api/internal/ticket-events/complaint-queue` | H | 正式宿主事件入口 |
| POST | `/api/ai-prechecks/tickets/{ticketId}` | O | 手工同步预审，仅用于调试 |
| GET | `/api/ai-prechecks/tickets/{ticketId}/current` | R/A | 当前有效核查结果 |
| GET | `/api/ai-prechecks/tickets/{ticketId}` | R/A | 核查历史 |
| GET | `/api/ai-prechecks/{id}` | R/A | 单轮核查详情 |
| POST | `/api/ai-prechecks/{id}/retry` | R | 授权失败任务重新执行 |
| POST | `/api/ai-prechecks/{id}/review` | R | 提交人工核查结果 |
| GET | `/api/ai-prechecks/{id}/review` | R/A | 查询人工核查结果 |
| GET | `/api/ai-prechecks/{id}/audit` | R/A | 查询原始审计事件 |
| GET | `/api/ai-prechecks/{id}/timeline` | R/A | 查询包含修改对比的统一时间线 |

## 4. 会话接口

### 4.1 演示身份登录

`POST /api/demo-session`

仅当 `DEMO_ONE_CLICK_LOGIN=true` 时启用。

```json
{"account":"reviewer"}
```

`account` 只能是 `operator`、`reviewer` 或 `auditor`。

响应：

```json
{
  "actorId":"reviewer",
  "roles":["ROLE_REVIEWER"],
  "csrfHeader":"X-CSRF-TOKEN",
  "csrfToken":"..."
}
```

### 4.2 当前会话

`GET /api/session` 返回格式与登录响应相同。后续 POST 请求必须使用返回的 Header 名称和值。

### 4.3 注销

`POST /api/session/logout`，成功返回 `204 No Content`。

## 5. 工单和宿主事件

### 5.1 查询工单上下文

`GET /api/tickets/DEMO-002/context`

```json
{
  "schemaVersion":"1.0",
  "ticketId":"DEMO-002",
  "customerRef":"SYNTHETIC-CUSTOMER-002",
  "complaint":"这个月扣了30元视频会员费，我不记得主动办理过，请帮我核实。",
  "synthetic":true,
  "sourceSystem":"mock-host",
  "capturedAt":"2026-10-18T12:00:00Z",
  "bills":{"status":"AVAILABLE","items":[{"evidenceId":"BILL-002","amount":"30.00","currency":"CNY"}]},
  "subscriptions":{"status":"AVAILABLE","items":[{"evidenceId":"ORDER-002","monthlyPrice":"30.00","currency":"CNY"}]},
  "authorizations":{"status":"NOT_FOUND","items":[]}
}
```

### 5.2 正式宿主事件

`POST /api/internal/ticket-events/complaint-queue`

```json
{"eventId":"EV-20261018-001","ticketId":"DEMO-001","ticketVersion":1}
```

首次接收返回 `202 Accepted`；同一来源、工单和版本重复投递返回 `200 OK` 及原 `runId`。

```json
{"runId":"uuid","taskStatus":"QUEUED","created":true,"accepted":true}
```

同一 `eventId` 用于其他工单版本返回 `409 EVENT_CONFLICT`。

### 5.3 模拟宿主流转

`POST /api/mock-host/tickets/{ticketId}/complaint-queue`

```json
{"eventId":"WEB-001","ticketVersion":1}
```

```json
{
  "ticketId":"DEMO-001",
  "ticketVersion":1,
  "transitioned":true,
  "precheckAccepted":true,
  "precheckRunId":"uuid"
}
```

`transitioned=true` 表示宿主流转已经完成；AI 任务投递失败时只把 `precheckAccepted` 设为 `false`，不会回滚宿主流转。

### 5.4 演示宿主工单列表

`GET /api/mock-host/tickets`

客服和投诉专员工作台共同使用该接口。`workflowStatus` 表示宿主状态，`reviewerBucket` 为
`PENDING`、`PROCESSING`、`COMPLETED` 之一；未提交工单为 `null`。列表状态只依据最新核查轮次，
不会被较早的历史轮次覆盖。

## 6. 核查结果

### 6.1 当前有效结果

`GET /api/ai-prechecks/tickets/{ticketId}/current`

没有结果时返回 `204 No Content`。存在结果时：

```json
{
  "run": {
    "id":"uuid",
    "ticketId":"DEMO-001",
    "status":"PENDING_REVIEW",
    "callStatus":"SUCCESS",
    "taskStatus":"COMPLETED",
    "version":1,
    "currentResult":true,
    "promptVersion":"charge-dispute-v2",
    "requestedModel":"spark-x2.5",
    "actualModel":"spark-x2.5",
    "traceId":"uuid"
  },
  "result": {
    "complaintType":"VALUE_ADDED_CHARGE_DISPUTE",
    "disputedAmount":30.00,
    "currency":"CNY",
    "summary":"账单金额与订购价格一致，未发现价格异常。",
    "evidenceGap":"存在授权记录，但尚未确认授权过程能否证明客户主动订购。",
    "suggestedAction":"核实授权时间、验证方式和操作主体，并关联订购流水。",
    "evidenceIds":["BILL-001","ORDER-001","AUTH-001"],
    "recommendation":"VERIFY_AUTHORIZATION"
  },
  "findings":[],
  "evidenceSnapshot":{}
}
```

`callStatus`：`PENDING`、`RUNNING`、`SUCCESS`、`FAILED`。
`taskStatus`：`QUEUED`、`RUNNING`、`RETRY_WAIT`、`COMPLETED`、`FAILED`。
`recommendation`：`VERIFY_AUTHORIZATION`、`VERIFY_BILLING`、`MANUAL_REVIEW`。
`summary`、`evidenceGap`、`suggestedAction` 分别表示“核查发现”、“关键疑点”和“建议操作”；`recommendation` 仅用于工作流分类。

### 6.2 历史列表

`GET /api/ai-prechecks/tickets/{ticketId}?limit=20&offset=0`

- `limit`：1～100，默认 20。
- `offset`：0～100000，默认 0。
- 历史列表不重复返回完整证据快照；需要时查询单轮详情。

### 6.3 单轮详情

`GET /api/ai-prechecks/{id}` 返回与当前结果相同的 View 结构。

### 6.4 人工授权重试

`POST /api/ai-prechecks/{id}/retry`

```json
{"expectedVersion":1}
```

只有当前工单版本、事件触发、任务已经失败且版本匹配时允许重试。成功返回 `202 Accepted`；冲突返回 `409 PRECHECK_RETRY_CONFLICT`。

## 7. 人工核查与审计

### 7.1 提交人工结果

`POST /api/ai-prechecks/{id}/review`

```json
{
  "expectedVersion":1,
  "decision":"CONFIRMED",
  "summary":"已核对账单与订购记录，授权仍需进一步确认。",
  "recommendation":"VERIFY_AUTHORIZATION",
  "reason":"当前材料不足以认定违规扣费，不执行退费。"
}
```

- `decision`：`CONFIRMED` 或 `REJECTED`。
- `summary`、`reason`：必填，最长 1000 字符。
- 状态或版本变化返回 `409 REVIEW_CONFLICT`。

### 7.2 查询人工结果

`GET /api/ai-prechecks/{id}/review`。尚未审核时响应体为空。

### 7.3 审计与时间线

`GET /api/ai-prechecks/{id}/audit?after=0&limit=50` 直接返回审计事件。
`GET /api/ai-prechecks/{id}/timeline?after=0&limit=50` 返回审计事件，并在人工操作节点附带原 AI 结果和人工结果对比。

- `after` 为上一页最后一条审计 ID，默认 0。
- `limit` 为 1～100，默认 50。

## 8. 错误响应

统一格式：

```json
{"code":"REVIEW_CONFLICT","message":"状态或版本已变化，请刷新后重试","traceId":"uuid"}
```

| HTTP | code | 说明 |
| ---: | --- | --- |
| 400 | `INVALID_REQUEST` | 参数、字段或 JSON 不合法 |
| 401 | `UNAUTHENTICATED` | 未登录或会话失效 |
| 403 | `FORBIDDEN` | 角色无权限或 CSRF 校验失败 |
| 404 | `TICKET_NOT_FOUND` | 工单不存在 |
| 404 | `PRECHECK_NOT_FOUND` | 核查记录不存在 |
| 404 | `RESOURCE_NOT_FOUND` | 接口不存在 |
| 405 | `METHOD_NOT_ALLOWED` | HTTP 方法不支持 |
| 409 | `EVENT_CONFLICT` | 事件 ID 与工单版本冲突 |
| 409 | `REVIEW_CONFLICT` | 审核状态或版本冲突 |
| 409 | `PRECHECK_RETRY_CONFLICT` | 重试状态或版本冲突 |
| 503 | `CONTEXT_UNAVAILABLE` | 宿主上下文暂不可用 |
| 500 | `INTERNAL_ERROR` | 未分类服务错误 |
