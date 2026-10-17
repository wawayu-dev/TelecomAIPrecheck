# Issue #2 验证记录

执行日期：2026-10-17。仅记录实际执行结果；本次已补齐 spark-x2.5 真实接口联调，远端 CI 状态见文末。

## 最终自动化结果

在 Java 17.0.20、Maven 3.9.12、Docker MySQL 8.4 下，使用独立 `telecom-issue2-test` Compose 项目和本机 13306 端口，执行：

```sh
RUN_MYSQL_TESTS=true mvn -B -ntp -f backend/pom.xml verify
```

数据库连接变量通过测试环境注入。最终结果：**55 项测试，54 通过，0 失败，0 错误，1 跳过；JAR 构建成功。** 普通回归跳过项为 `XfyunLiveTest`，以避免 CI 自动产生真实付费请求；真实联调已另行显式执行并通过，见下节。

| 测试组 | 项数 | 结果与覆盖 |
| --- | --- | --- |
| Issue #1 回归测试 | 16 | 全部通过；上下文、异常、适配替换、健康检查及真实 MyBatis/MySQL |
| EvidenceVerifierTest | 7 | 完整证据、授权缺失/不可查询、金额不一致、错误关联、重复证据 ID |
| ResultValidatorTest | 14 | 合法输出、未知金额、伪造/重复/空引用、未知字段、非法业务动作与枚举、金额范围、缺字段、空摘要、非法/重复/尾随 JSON、授权缺失的中性输出 |
| XfyunPrecheckModelTest | 6 | 实际 Spring AI 客户端连接本地 HTTP stub：请求路径/模型/角色/认证、401 无重试及无原文泄漏、超时、协议异常、截断拒绝、缺少凭据失败 |
| PrecheckIntegrationTest | 11 | MySQL 迁移、成功记录及历史快照、缺少授权与金额差异、非法 ID 拒绝、三种模型失败转人工、解析失败、快照写失败事务回滚、中断恢复不被迟到结果覆盖、接口参数/404 |
| XfyunLiveTest | 1 | 普通回归跳过；单独开启后的真实联调 1 项通过 |

本地 HTTP stub 是测试夹具，不是讯飞平台。数据库集成测试替换模型接口为受控响应，不用于证明真实模型效果。初次加入截断检查时，正常响应测试发现 Spring AI 将 finish reason 转为大写 STOP；已修正为大小写兼容判断，最终正常和截断两条路径均通过。

## 实际打包应用验证

使用 mysql profile、独立测试数据库，在本机 18081 启动 JAR：

- `GET /actuator/health`：200，status=UP。
- `POST /api/ai-prechecks/tickets/DEMO-002`：201，runId=`17de2f54-6ea1-4fed-80d4-d1b94e5cd772`。
- 该次未配置模型凭据：callStatus=FAILED、failureCode=MODEL_NOT_CONFIGURED、status=PENDING_REVIEW、result=null。
- 单条查询返回本次 DEMO-002 证据快照；按工单查询历史可以找到同一 runId。
- 没有真实模型调用，该次 durationMs=0，不作为模型性能数据。

测试数据仅在临时数据库中，用完清理；上述 runId 是执行记录，不承诺清理后仍可查询。前端本轮没有修改，仍为 Issue #1 上下文页面。

## 真实讯飞 MaaS 联调补充

2026-10-17，使用用户授权的 MaaS API Key、官方 Chat 接口与 `spark-x2.5`，仅发送 DEMO-001 合成数据；没有将密钥写入代码、Git 或验收文件。

| 尝试 | 配置及结果 |
| --- | --- |
| 第一次 | 原始参数、30 秒超时；实际 30015 ms，保存 FAILED/MODEL_TIMEOUT，状态 PENDING_REVIEW |
| 第二次 | 原始参数、120 秒超时；收到模型响应，实际 21251 ms，但 DTO/JSON 校验不通过，保存 FAILED/INVALID_OUTPUT；没有将未校验结果作为业务建议 |
| 第三次 | 根据官方 Spark-X2.5 文档使用 max_completion_tokens=1500、thinking.type=disabled、不设置 temperature；120 秒超时，实际 6470 ms，SUCCESS |

第三次 `XfyunLiveTest`：**1 项通过、0 失败、0 跳过，BUILD SUCCESS**。两次失败均保留了预审记录与本次证据，未修改宿主工单；第二次失败的具体 JSON 未留存，不能断言其唯一根因。

成功记录：

- runId：`5bdeb239-e07f-466a-ab19-743b8042b2a9`
- 实际返回模型：spark-x2.5；Prompt：charge-dispute-v1。
- 投诉类型：VALUE_ADDED_CHARGE_DISPUTE；争议金额：30.00 CNY。
- 引用证据：AUTH-001、BILL-001、ORDER-001，均存在于本次快照。
- 建议：VERIFY_AUTHORIZATION；摘要明确为客户陈述并建议进一步核验授权真实性。
- 结果、快照及调用元信息已通过真实 MySQL 保存并回读。
- 完整合成记录：[脱敏验收凭据](evidence/issue-2-xfyun-live.json)。该文件不含密钥或真实客户资料；临时数据库清理后仍可查阅。

6.47 秒仅为这一个成功样本的耗时，不是平均时延、准确率或效率提升指标。修改后的客户端还经过完整离线与 MySQL 回归；传输测试新增断言，验证 thinking、max_completion_tokens 以及不再发送旧 max_tokens/temperature 字段。

## 已知限制与远端验证

1. 真实讯飞单样本联调已通过；其他模型、复杂场景和稳定性仍需进一步验证。运行方法见 [Issue #2 接入说明](issue-2-precheck.md)。
2. GitHub Actions 在 main 推送后自动执行 MySQL 后端测试及前端构建；远端结果以 [对应提交的 Actions 记录](https://github.com/wawayu-dev/TelecomAIPrecheck/actions) 为准。Issue #1 的 main/1140824 工作流成功已有用户截图确认。
3. Flyway 11.7.2 在 MySQL 8.4 下打印“最新已测试版本为 8.1”的兼容提醒；本地迁移、重复启动校验与数据库测试实际通过，不宣称全面认证。
4. PENDING_REVIEW 表示允许后续人工接管；人工审核、权限和审计写接口仍在 Issue #3，当前不能完成真实人工审核操作。
5. 不提供 AI 正确率、业务效率或收益数据；自然语言摘要仍需人工验证。合成月费规则不覆盖真实复杂资费。
