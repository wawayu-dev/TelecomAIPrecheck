# Issue #3：人工审核、状态约束与业务审计

本阶段实现后端业务单元；审核页面在 Issue #4 实现。使用真实 MySQL 8.4、MyBatis、Flyway V2、Spring Security，不使用 H2 或内存仓库存储审核记录。

## 状态与人工决定

`ANALYZING → PENDING_REVIEW → REVIEWED`。模型调用结果独立保存为 RUNNING / SUCCESS / FAILED。超时、错误、非法输出以及进程中断均保留已有快照，并转入 PENDING_REVIEW。调度器每分钟恢复超过五分钟的 ANALYZING 记录，恢复同样有事务审计。

人工 CONFIRMED 表示确认人工修订后的处理建议；REJECTED 表示驳回本轮预审。两者均结束本轮，不能再次审核。重新预审创建新 run ID，旧结果、证据和审核意见保留。这里的确认不等于认定违规或批准退费。没有退费接口、工具或执行权限。

审核提交必须携带读取时的 `expectedVersion`。更新条件同时检查待审核状态、已结束的模型调用状态和版本；成功后 version + 1。并发请求最多一个成功，其他返回 409。人工意见独立存于 `ai_precheck_review`，不覆盖 AI 的 result_json。

## 身份与角色

mysql profile 的业务接口要求认证；健康检查保持公开。未配置演示密码时不创建演示账户，没有默认密码。mock profile 仅提供只读合成上下文，不提供预审或人工审核功能。

| 演示账号 / 服务端角色 | 权限 |
| --- | --- |
| operator / ROLE_OPERATOR | 发起预审，读取上下文、预审与人工结果 |
| reviewer / ROLE_REVIEWER | 读取业务数据，确认或驳回，查询审计 |
| auditor / ROLE_AUDITOR | 读取业务数据及审计，不允许写操作 |

密码通过 `DEMO_OPERATOR_PASSWORD`、`DEMO_REVIEWER_PASSWORD`、`DEMO_AUDITOR_PASSWORD` 配置，至少 12 字符，内存中以 BCrypt 编码。演示 Basic 认证仅用于本机回环地址；接入宿主时由宿主现有认证链提供 SecurityContext，不额外建设用户管理系统。

身份必须为宿主认证后的稳定不透明 ID，格式 `[A-Za-z][A-Za-z0-9_-]{0,63}`。不能用手机号、身份证号或邮箱作用户 ID。服务读取 SecurityContext 中的身份及角色，不信任 X-Actor-Id 等自报请求头。请求 DTO 不允许未知字段，不能提交 actorId、role、退费动作等字段。

写接口启用 CSRF。客户端登录后先 `GET /api/session`，保存会话 Cookie，之后在写请求中带上响应指定的 csrfHeader/csrfToken。身份与 CSRF 端点为后续前端集成提供契约。

## API

原有预审接口见 [Issue #2](issue-2-precheck.md)，现在同样受上述认证和角色约束。

- `POST /api/ai-prechecks/{id}/review`：REVIEWER 审核；必填字段如下。
- `GET /api/ai-prechecks/{id}/review`：读取人工结果，尚未审核时响应为空；未知 run 返回 404。
- `GET /api/ai-prechecks/{id}/audit?after=0&limit=50`：REVIEWER / AUDITOR 查看按事件 ID 升序的审计记录，limit 最大 100，after 为上一页最后事件 ID。
- `GET /api/ai-prechecks/tickets/{ticketId}`：分页查找该工单所有预审轮次，然后按 run ID 查询对应快照、人工意见和审计。

```json
{
  "expectedVersion": 1,
  "decision": "CONFIRMED",
  "summary": "人工核对后的摘要",
  "recommendation": "VERIFY_AUTHORIZATION",
  "reason": "授权材料仍需复核，仅确认处理建议"
}
```

summary、reason 不得为空且最多 1000 字符；recommendation 仅允许 VERIFY_AUTHORIZATION / VERIFY_BILLING / MANUAL_REVIEW。审核失败也可以人工整理建议。响应含 actorId、reviewedAt、traceId、version 以及保存后的意见。

400 表示参数不合法，401 未认证，403 无权限或 CSRF 失败，404 记录不存在，409 状态或版本冲突。409 后应刷新记录，不能静默覆盖他人结果。

## 事务与审计

V2 新增 `ai_precheck_review` 和 `ai_precheck_audit`。同一事务内执行条件状态更新、人工结果插入、审计插入；任何一步失败全部回滚。人工表以 run_id 为主键，提供第二层防重约束。模型完成与中断恢复也通过条件更新，迟到的模型结果不能覆盖已转人工的记录。

审计只追加，应用不提供修改或删除接口。事件包含操作人、操作时间、动作、前后状态、处理结果、run_version、Prompt 版本、实际/请求模型、Trace ID 和快照 run ID。事件 PRECHECK_STARTED / PRECHECK_SUCCESS / PRECHECK_FAILED / PRECHECK_INTERRUPTED / REVIEW_CONFIRMED / REVIEW_REJECTED 记录已提交的业务操作。认证失败、非法请求或竞争失败以 HTTP 错误及 Trace ID 返回，不伪造业务状态变更事件；完整安全访问日志平台不在本期范围。

升级已有 V1 数据时只增加 BASELINE_IMPORT 基线事件，时间为迁移时间，操作者为 system:migration；不声称还原升级前不存在的审计历史。数据库管理员仍可以修改数据，本阶段不宣称具备防篡改存证能力。

审计不复制投诉、证据正文或人工意见。人工 summary/reason 保存前掩码大陆手机号、18 位身份证号与常见邮箱；异常日志仅含异常类型和服务端 Trace ID，不输出原始异常或 SQL。脱敏为基础规则，不是完整 DLP；真实宿主仍须在 TicketContextProvider 中进行数据最小化、访问范围校验及必要脱敏。原始证据快照用于受权限保护的核查，不能混入普通日志。

## 宿主接入

保留现有 TicketContextProvider 隔离宿主数据表。在宿主的 SecurityContext 中映射 ROLE_OPERATOR、ROLE_REVIEWER、ROLE_AUDITOR，并保持方法安全启用。宿主使用自己的 SecurityFilterChain、UserDetailsService 时应排除演示 SecurityConfiguration 配置，保留服务上的 @PreAuthorize 校验，并按宿主策略处理认证、CSRF 与错误体。不要通过前端传入角色来替代认证。

本期只实现业务角色边界，不实现多租户或逐工单数据权限。真实宿主接入还需对历史结果和快照查询实施相同的数据范围限制。前端身份复用及页面路由在 Issue #4 验证。

## 本机演示

```sh
scripts/demo.sh up
scripts/demo.sh backend
# 另开终端：真实 HTTP 角色登录、预审、审核和审计
python3 scripts/demo-smoke.py
```

脚本自动生成 `docs/TelecomAIPrecheck/.env`（Git 忽略、权限 600）中的随机数据库和三个演示账户密码。数据库固定使用独立项目 telecom-ai-precheck-demo，默认端口 13307，命名卷 telecom-ai-precheck-demo_mysql-data；后端端口 8080。`scripts/demo.sh stop` 删除容器但保留卷，`up` 恢复数据。不要执行 `down -v`，也不要在已有数据库卷上随意修改数据库密码。

如需真实模型，启动后端前注入 XFYUN_API_KEY、XFYUN_MODEL，或在本机 .env 设置（不要提交密钥）。未配置模型时 smoke 演示 MODEL_NOT_CONFIGURED → 人工审核，不能将其报告为真实模型成功。设置模型后 smoke 会产生一次真实调用。`python3 scripts/demo-smoke.py --verify-run <runId>` 只读校验已有记录，可用于重启后验证。

该阶段已可演示服务端完整闭环；可视化页面和完整 30 分钟讲解流程仍需 Issue #4/#5 完成。
