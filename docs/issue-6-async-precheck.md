# Issue #6：工单事件驱动的异步 AI 预处理

## 事件接入

宿主在工单成功进入投诉处理队列后调用：

```http
POST /api/internal/ticket-events/complaint-queue
Authorization: Basic <HOST 服务身份>
Content-Type: application/json

{"eventId":"EV-20261018-001","ticketId":"DEMO-001","ticketVersion":1}
```

`eventId` 和 `ticketId` 只能包含字母、数字、下划线和短横线，最长 64 位；`ticketVersion` 为正整数且必须随宿主工单内容变更递增。服务身份来自认证配置，事件体不能指定调用人或宿主来源。演示环境通过 `DEMO_HOST_PASSWORD` 创建 `host` 服务账号；真实集成应替换为宿主统一认证。

新任务返回 `202 Accepted`，同一宿主、工单及版本的重复事件返回 `200 OK` 和原 `runId`。同一 `eventId` 若被用于不同工单版本则返回 `409 EVENT_CONFLICT`。接口只保证任务已持久化，不等待证据查询或 MaaS 调用。

模拟宿主流转接口为 `POST /api/mock-host/tickets/{ticketId}/complaint-queue`。它使用 OPERATOR 身份和 CSRF，body 为 `eventId`、`ticketVersion`。响应中的 `transitioned` 在事件投递前已经成立；投递失败只会使 `precheckAccepted=false`，不会回滚宿主流转。

## 任务与幂等

自动任务复用 `ai_precheck_run`、证据快照和审计表。Flyway `V3` 增加宿主事件字段、唯一键和任务字段：

- `taskStatus`：`QUEUED → RUNNING → COMPLETED`，临时失败进入 `RETRY_WAIT`，超过次数进入 `FAILED`。
- 唯一键 `(event_source,ticket_id,ticket_version)` 防止同版本重复执行，`(event_source,event_id)` 防止事件标识被复用。
- `attemptCount/maxAttempts/nextAttemptAt` 支持有限重试，默认最多 3 次、间隔 30 秒。
- `currentResult=false` 表示相同宿主和工单已有更高业务版本，旧结果仍保留但不再是当前有效结果。

线程池饱和或进程在任务开始前退出时，数据库中的 `QUEUED/RETRY_WAIT` 任务由定时扫描重新投递。运行中断超过 5 分钟时，未耗尽次数的任务恢复到 `RETRY_WAIT`；已耗尽任务转为 `FAILED/PENDING_REVIEW`，供人工兜底。

模型调用和宿主证据查询均在短数据库事务之外。创建、认领、保存快照、重试和完成分别提交，避免远程调用长期占用数据库事务。

## 审计与配置

自动任务使用 `system:host-event`、`system:precheck-worker`、`system:recovery` 身份。审计记录关联同一证据快照，并补充事件 ID、工单 ID、工单版本、模型、耗时和受控错误类型。不会保存供应商异常原文或未经校验的模型输出。

| 环境变量 | 默认值 | 说明 |
| --- | ---: | --- |
| `HOST_EVENT_SOURCE` | `mock-host` | 稳定宿主标识 |
| `PRECHECK_TASK_CORE_POOL_SIZE` | 2 | 后台核心线程数 |
| `PRECHECK_TASK_MAX_POOL_SIZE` | 4 | 后台最大线程数 |
| `PRECHECK_TASK_QUEUE_CAPACITY` | 100 | 进程内投递缓冲；数据库仍是恢复依据 |
| `PRECHECK_TASK_MAX_ATTEMPTS` | 3 | 最大执行次数 |
| `PRECHECK_TASK_RETRY_DELAY_SECONDS` | 30 | 临时失败重试间隔 |
| `PRECHECK_TASK_SCAN_DELAY_MS` | 5000 | 待执行任务扫描周期 |

人工入口 `POST /api/ai-prechecks/tickets/{ticketId}` 保持同步语义，继续用于调试和异常重试；人工审核与审计查询接口不变。
