# 宿主集成契约 v1.0（Issue #1）

## 边界与扩展方式

`cn.telecom.precheck.context.TicketContextProvider#getContext(String ticketId)` 为只读 Java 接口，不依赖 Spring、MyBatis Mapper 或宿主数据库表。调用方为 HTTP Controller；演示使用 Mock 实现。

真实宿主需注册一个 `TicketContextProvider` Bean，并设置 `CONTEXT_PROVIDER=host` 禁用演示适配器。若未注册实现，应用启动失败，不悄悄降级为合成数据。实现方可调用已有 Service 或 API；模块不直接读取客户、账单、订购主表。自动化测试 `HostAdapterTest` 验证替换方式。

```java
@Bean
TicketContextProvider ticketContextProvider(HostTicketService service) {
    return ticketId -> service.readAuthorizedContext(ticketId);
}
```

上述 HostTicketService 是宿主自行实现的示意类型，不是当前仓库 API。宿主认证上下文须由服务端取得，先校验工单访问权限，再返回经过数据最小化处理的上下文，不能信任前端自报用户或角色。服务默认绑定 127.0.0.1。Issue #3 已在 mysql profile 实现服务端身份与角色校验，见下方审核契约。

## 数据字段

| 字段 | 契约 |
| --- | --- |
| schemaVersion | 当前为 `1.0`；破坏性改动需升级契约并迁移适配器 |
| ticketId | 1–64 位英文字母、数字、下划线或短横线；宿主其他格式需映射 |
| customerRef | 最小化的不透明客户引用，Demo 不使用真实姓名或手机号 |
| complaint | 原始投诉文本；将来送模型前仍需脱敏与长度限制，不作为指令执行 |
| synthetic | 是否合成；Mock 恒为 true，真实适配器必须按实际设置 |
| sourceSystem / capturedAt | 数据来源标识与本次读取 UTC 时间；不宣称跨宿主系统原子快照 |
| bills | evidenceId、serviceId、subscriptionId、billingPeriod（YYYY-MM）、amount、currency |
| subscriptions | evidenceId、subscriptionId、serviceId、serviceName、monthlyPrice、currency、orderedAt |
| authorizations | evidenceId、subscriptionId、channel、authorizedAt、description |

金额为 Java BigDecimal，JSON 使用十进制字符串（例如 `"30.00"`），币种为 `CNY`。时间为 ISO-8601 UTC。`subscriptionId` 关联账单、订购及授权，`serviceId` 关联业务；同一次上下文内所有证据的 evidenceId 必须唯一、非空且可回溯。宿主应返回与工单及客户相关的证据，不能仅按金额猜测关联。

每种证据返回 `EvidenceSet<T>`：

- `AVAILABLE`：查询成功且 items 非空。
- `NOT_FOUND`：查询成功但未找到，items 为空。
- `UNAVAILABLE`：此类证据暂不可查询，items 为空。

集合复制为不可变列表，并验证获取状态与非空性。不提供虚构证据占位 ID。整体工单不存在抛 `TicketNotFoundException`；整体宿主不可用抛 `ContextUnavailableException`；部分证据查询失败应保留已有数据并返回 `UNAVAILABLE`。本阶段只约束获取状态；关联字段、唯一证据 ID 等完整业务校验在 Issue #2 实现。

## 当前 HTTP 接口

- `GET /actuator/health`：应用健康；mock 为 UP，mysql 环境还检查数据库。默认不公开内部详情。
- `GET /api/tickets/{ticketId}/context`：读取上下文；演示 ID 为 DEMO-001/002/003。
- 每次请求生成服务端 `X-Trace-Id`，异常体含同一 traceId。
- 错误体：`{"code":"TICKET_NOT_FOUND","message":"未找到工单","traceId":"..."}`。
- 400 INVALID_REQUEST；404 TICKET_NOT_FOUND/RESOURCE_NOT_FOUND；405 METHOD_NOT_ALLOWED；503 CONTEXT_UNAVAILABLE；500 INTERNAL_ERROR。
- 不回显异常原文、SQL、密钥或堆栈；当前 Trace ID 是请求关联 ID，不是完整分布式链路实现。

开发时 Vite 代理 `/api`、`/actuator` 至后端，不使用宽泛 CORS。Vue Router 模块挂载与宿主身份复用已在 Issue #4 验证；人工结果通过既有查询 API 提供给宿主，不写宿主表。见 [前端模块集成](issue-4-integration.md)。

## 已实现的状态设计

业务状态：ANALYZING → PENDING_REVIEW → REVIEWED；人工决定单独记录 CONFIRMED 或 REJECTED。模型调用结果独立记录 SUCCESS 或 FAILED；失败后保留失败原因和已有证据，进入待人工处理。驳回结束当前轮，再次预审创建新轮次，历史不覆盖。Issue #3 已实现该语义。

模块只拥有自己的预审、证据快照、审核与审计数据；不写宿主表。Issue #2 已实现预审和独立证据快照表，新增接口与状态行为见 [预审契约](issue-2-precheck.md)。

人工审核、角色映射、CSRF、乐观锁和事务审计的接入说明见 [Issue #3 契约](issue-3-review.md)。
