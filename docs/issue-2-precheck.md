# Issue #2：证据核验与 AI 预审

## 当前范围

已实现后端预审链路、MySQL 历史和证据快照、Spring AI OpenAI 兼容客户端、确定性规则、DTO/证据 ID/字段白名单校验、超时与异常兜底。真实讯飞验收须另外配置有效账户并运行一次真实请求，自动化模拟测试不替代该验收。

当前默认 `mock` profile 仍只提供 Issue #1 上下文联调；启用 `mysql` profile 才注册预审 API、数据库迁移和模型客户端。没有内存存储冒充数据库，也没有本地假 AI 回答冒充讯飞成功。

## 启动与配置

先按 [环境说明](TelecomAIPrecheck/README.md) 启动 MySQL，设置 DB_PASSWORD 等变量。然后在启动后端的同一个终端注入：

```sh
export SPRING_PROFILES_ACTIVE=mysql
export XFYUN_BASE_URL=https://maas-api.cn-huabei-1.xf-yun.com
export XFYUN_COMPLETIONS_PATH=/v2/chat/completions
export XFYUN_MODEL='你的控制台已开通的Model ID'
# XFYUN_API_KEY 请通过 IDE 环境变量或本机密钥配置注入，勿写入仓库或截图。
export XFYUN_TIMEOUT_MS=30000
mvn -f backend/pom.xml spring-boot:run
```

变量模板位于 `backend/.env.example`。Spring Boot 不自动读取 .env；不要把复制模板当成已经配置成功。默认不提供模型 ID，避免调用未授权或错误模型。

根据 [讯飞 MaaS 产品使用说明](https://www.xfyun.cn/doc/spark/产品使用说明.html) 和 [HTTP 推理服务文档](https://static.xfyun.cn/doc/spark/推理服务-http.html)，MaaS 提供 OpenAI 兼容协议。当前默认将官方 `/v2` 地址拆成 host 与 `/v2/chat/completions`。旧账户可能使用不同入口，必须以控制台为准。例如传统星火接口使用 host `https://spark-api-open.xf-yun.com` 与路径 `/v1/chat/completions`，凭据类型和模型 ID 也需匹配，不能混用 Coding Plan、Token Plan 或不同产品的密钥。

Spring AI 版本固定 1.1.2，使用 [OpenAiApi.Builder](https://docs.spring.io/spring-ai/docs/1.1.2/api/org/springframework/ai/openai/api/OpenAiApi.Builder.html) 配置 URL、路径及 HTTP 客户端；未使用未来版本的 SDK API。

## API

### 发起一轮预审

```sh
curl -X POST http://127.0.0.1:8080/api/ai-prechecks/tickets/DEMO-001
```

每次 POST 创建独立 UUID 轮次。同步等待模型调用；返回 HTTP 201 表示记录已创建，**不表示模型成功**。检查 `run.callStatus`：

- SUCCESS：`result` 为通过校验的结构化输出。
- FAILED：`result=null`，检查 `run.failureCode`；记录保持 PENDING_REVIEW，可供下一阶段人工处理。

响应含 `run` 元信息、`result`、`findings`（确定性规则结果）、`evidenceSnapshot`（本次原始合成上下文）。不返回模型未经校验的原文。金额提取字段 `disputedAmount` 为 JSON 数字或 null；金额计算在 Java 使用 BigDecimal。证据快照保持 Issue #1 金额字符串格式。

结构化 result 字段白名单：

| 字段 | 限制 |
| --- | --- |
| complaintType | VALUE_ADDED_CHARGE_DISPUTE / OTHER / UNCLEAR |
| disputedAmount | 客户明确争议金额；未知为 null；非负、最多两位小数，上限 99999999.99 |
| currency | 当前仅 CNY |
| summary | 非空、最多 1000 字符；模型生成文本只作为待人工确认的整理 |
| evidenceIds | 本次快照中的证据 ID，不重复，最多 100 项；存在可用证据时至少引用一项 |
| recommendation | VERIFY_AUTHORIZATION / VERIFY_BILLING / MANUAL_REVIEW |

拒绝未知属性、非法枚举、缺失必填项、重复 JSON 键、尾随 JSON、超长输出、非法证据引用。未设计退费执行、责任认定或业务变更字段。

### 查询

```sh
curl 'http://127.0.0.1:8080/api/ai-prechecks/tickets/DEMO-001?limit=20&offset=0'
curl http://127.0.0.1:8080/api/ai-prechecks/替换为返回的预审UUID
```

历史按创建时间倒序分页，limit 1–100、offset 0–100000；历史列表不重复返回整个快照，单条查询包含快照。不存在预审返回 404 PRECHECK_NOT_FOUND，参数错误返回 400 INVALID_REQUEST。整体宿主不可用时沿用 503，不调用模型；未知工单返回 404。

## 规则与数据关联

宿主适配器返回关联证据。代码按 subscriptionId 关联账单、订购与授权，并核对 serviceId 与简单月费金额；证据 ID 必须唯一、满足格式限制。上下文不合法时记录 INVALID_CONTEXT 且不调用模型。

规则区分未找到与不可查询，输出账单/订购/授权缺失或不可用、未关联订购、未关联授权、业务不一致、月费金额不一致。金额不一致只是待核对信息，不适用于复杂优惠或按天计费。存在凭据不代表已经确认授权有效。

授权缺失、不可查询或无法关联到争议账单时，服务端将摘要替换为中性提示，并强制使用 VERIFY_AUTHORIZATION。模型即使输出违规认定，也不能直接作为返回摘要使用。原始投诉保留在证据快照中，便于人工继续核验。该策略刻意优先保守性；不会保证模型在其他场景下的自然语言都完全准确，仍须人工确认。

## 持久化和失败恢复

Flyway `V1__precheck_runs_and_evidence.sql` 自动创建：

- `ai_precheck_run`：轮次 ID、宿主工单 ID、业务状态、调用状态、Prompt 版本、请求与返回模型 ID、调用耗时、失败码、已校验结果、规则结果、Trace ID、创建/完成时间、行版本。
- `ai_precheck_evidence_snapshot`：以 run_id 关联一份独立 JSON 快照；当前没有修改/删除业务接口。

先在一个短事务中插入 ANALYZING/RUNNING 记录与快照。事务成功后再调用模型，模型调用不占用数据库事务。最终以条件更新写入 PENDING_REVIEW + SUCCESS/FAILED；保存历史不会重新读取宿主数据。`durationMs` 测量模型适配调用（含排队）耗时，不含数据库写入；在规则核验前失败时为 0。

失败码：MODEL_NOT_CONFIGURED、MODEL_TIMEOUT、MODEL_ERROR、INVALID_OUTPUT、INVALID_EVIDENCE_REFERENCE、INVALID_CONTEXT、INTERRUPTED。只保存受控错误码，不保存供应商异常原文、密钥或未校验输出。

HTTP 默认 30 秒、可配置 100–120000 毫秒；有总等待时间限制、连接/读取超时、4 个模型工作线程及 16 个排队槽；不自动重试，避免重复计费。截断或非 stop 完成的响应拒绝。未配置工具回调，禁止内部工具执行。

每分钟检查一次超过 5 分钟的 ANALYZING 记录，将其转为 FAILED/INTERRUPTED + PENDING_REVIEW。用于进程意外终止或最终数据库写入失败后的兜底；迟到结果不能覆盖已恢复记录。不是持久化任务队列，也不保证宕机时马上完成。

## Prompt 管理

模板在 `backend/src/main/resources/prompts/charge-dispute-v1.txt`，输出 Schema 由 Spring AI BeanOutputConverter 根据 DTO 生成，版本 `charge-dispute-v1` 与每轮结果一起保存。发布后的模板/Schema 发生语义变更时新增 v2 文件并更新版本常量，不覆盖已发布的 v1。Git 提交用于追溯版本内容。

投诉和证据以独立 user 消息传递，规则位于 system 消息。提示词将投诉视为不可信数据；后端仍独立执行字段及证据校验，不依赖 Prompt 保证安全。发送内容省略 customerRef 与宿主身份；演示只使用合成数据。真实接入前仍须完善原文脱敏和访问控制。

## 测试与真实联调

无数据库运行：`mvn -B -ntp -f backend/pom.xml test`。MySQL 集成测试：设置 `RUN_MYSQL_TESTS=true` 与 DB 变量后运行 `mvn -B -ntp -f backend/pom.xml verify`。CI 已设置该开关，将自动执行新测试。

真实联调须有可用数据库和讯飞凭据，在同一终端执行（会产生一次真实模型调用）：

```sh
RUN_XFYUN_LIVE=true mvn -B -ntp -f backend/pom.xml -Dtest=XfyunLiveTest test
```

测试只使用 DEMO-001 合成工单，必须获得 SUCCESS 并成功查询快照才通过；缺少密钥会失败，不会假装通过。成功输出 runId、返回模型 ID、耗时和状态；不打印密钥或投诉正文。普通 CI 不开启该开关。

## 本阶段限制

未实现人工审核写接口、角色权限、审核审计（Issue #3），也未改造前端为 AI 预审页面（Issue #4）。服务仍默认只绑定本机。不要将当前无认证接口公开到互联网。

重复 POST 会产生不同轮次与不同模型调用；幂等触发和前端重复提交控制待后续集成完善。失败记录只是已进入人工待处理状态，不能把它描述为人工复核已经完成。真实 MaaS 成功、效果评估与本次提交远端 CI 均以实际执行记录为准。
