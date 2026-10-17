# Issue #3 验证记录

日期：2026-10-17。验证对象为人工审核后端；全部工单为合成数据，不表示运营商生产验收。

## 自动化结果

Java 17.0.20、Spring Boot 3.5.9、真实 MySQL 8.4；测试库单独使用端口 13306，与持久化演示库 13307 隔离。

```sh
RUN_MYSQL_TESTS=true \
DB_URL='jdbc:mysql://127.0.0.1:13306/telecom_ai_precheck?serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false' \
DB_USERNAME=precheck DB_PASSWORD='<测试库密码>' \
mvn -f backend/pom.xml verify
```

本地 Maven 共 69 项：68 通过、0 失败、0 错误、1 跳过。跳过项为显式开关控制的真实讯飞调用；Issue #3 不重复消耗模型额度，Issue #2 的真实联调结果见对应报告。

新增 ReviewIntegrationTest 的 14 项执行结果覆盖：

- 模型失败后确认和驳回，状态、版本及完整三条审计链。
- 人工修订独立保存，原始 AI 结果与证据快照不变。
- ANALYZING、过期版本、已审核状态均不能审核。
- 两个线程同时审核同一版本，只有一个成功，只有一条人工记录与一条审核事件。
- 强制审计写入失败，人工结果与状态一起回滚。
- 预审创建及完成阶段的审计失败也整体回滚。
- 中断恢复只记一次事件，迟到模型不能覆盖。
- 手机号、身份证号、邮箱脱敏，审计不复制意见正文。
- 未认证请求、越权、CSRF 缺失、伪造 actorId、未知枚举、错误分页、未知记录。
- 演示 Basic 登录与 CSRF 获取，直接调用服务方法也必须符合角色权限。

前端现有 6 项测试通过，`npm run build` 生产构建通过。前端审核 UI 尚未实现，不将当前只读页描述为完整审核页面。

## 真实 HTTP 与持久化

演示后端 localhost:8080，连接 telecom-ai-precheck-demo 项目的 MySQL 8.4 命名卷。运行 `python3 scripts/demo-smoke.py` 验证：

1. operator 登录，获得 CSRF 后发起合成 DEMO-001 预审。
2. 未配置模型时保留 MODEL_NOT_CONFIGURED 失败原因并进入 PENDING_REVIEW。
3. reviewer 登录并确认人工建议，状态 REVIEWED、version=2。
4. 重复提交返回 409；operator 越权审核返回 403。
5. auditor 查询原始快照、人工结果与三条审计。
6. `scripts/demo.sh stop` 后再 `up`，实际删除并重建数据库容器，但保留命名卷；只读复验同一 run 仍成功。

[机器可读执行结果](evidence/issue-3-http-persistence.json) 保留 run ID 和真实状态，不包含密码或密钥。这是失败转人工及持久化验证，不是新的真实 AI 成功记录。

## CI 与边界

现有 GitHub Actions 的 Maven verify 会在 MySQL 8.4 服务上自动执行新增用例；前端 job 继续测试和构建。远端执行结果以本次提交对应的 Actions 页面为准。

本阶段没有真实退费、审核页面、多租户数据权限或防篡改审计平台。审计只记已提交的业务状态变更；拒绝请求通过 HTTP 错误与 Trace ID 返回。30 分钟业务演示所需的页面和讲解材料在 Issue #4/#5 继续完善。
