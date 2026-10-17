# Issue #1 验收记录

执行日期：2026-10-17。以下为本地实际结果，不代表 GitHub Actions 已运行或生产可用性认证。

## 环境与版本

macOS arm64；Temurin Java 17.0.20；Maven 3.9.12；Node 25.8.0；npm 11.11.0；Docker Engine 29.2.1。

工程固定 Spring Boot 3.5.9、Spring AI 1.1.2、MyBatis Starter 3.0.5、Vue 3.5.44、Vite 7.3.7、Vitest 4.1.12。Spring AI 只导入基础 model 包；没有调用模型。CI 使用 Java 17、Node 22 和 MySQL 8.4，本地 Node 与 CI 的版本不同，远端执行仍需确认。

MyBatis 3.0 系列与 Spring Boot 3.2–3.5、Java 17 的对应关系参考 [官方兼容矩阵](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)。依赖以当前固定组合实测为准，不宣称均为最新版本。

## 自动化测试

| 命令／测试组 | 实际结果 |
| --- | --- |
| `mvn -B -ntp -f backend/pom.xml verify`（不启用数据库测试） | 15 成功，1 明确跳过，0 失败；打包成功 |
| 启动本项目 Compose MySQL，映射本机 13306，启用 `RUN_MYSQL_TESTS=true` 后执行 Maven verify | 16 成功，0 跳过，0 失败；真实 MyBatis 查询 MySQL 成功 |
| `npm ci` | 按 lockfile 安装成功 |
| `npm test` | 2 个文件、6 项测试全部通过 |
| `npm run build` | Vite 生产构建成功 |
| npm 官方依赖审计 | 更新 Vitest 后报告 0 个已知漏洞；这不是完整安全审计 |

后端测试覆盖：无数据库/模型凭据健康检查；三个模拟场景的 JSON 字段和金额精度；工单不存在；非法 ID；不存在路由；只读接口拒绝 POST；关联证据一致性；缺失证据不虚构记录；金额不一致原样保留；证据集合不可变及状态约束；宿主 Bean 替换；宿主不可用 503；异常原文不泄漏；MyBatis 真实 MySQL 查询。

前端测试覆盖：读取缺失授权并显示审慎提示；切换工单清理旧数据；加载时防止重复请求；失败后清理旧结果并允许重试；URL 编码；错误码白名单；非 JSON 代理错误兜底。

## 实际启动及浏览器联调

- `java -jar backend/target/ai-precheck-0.1.0-SNAPSHOT.jar` 启动成功，默认 mock profile。
- HTTP `GET /actuator/health` 返回 `200` 和 `{"status":"UP"}`。
- HTTP `GET /api/tickets/DEMO-002/context` 返回 30.00 元账单、对应订购以及 `authorizations.status=NOT_FOUND`、空证据列表。
- Vite 开发服务启动成功，浏览器打开 `http://127.0.0.1:5173/`。
- 页面实际点击读取 DEMO-001，显示账单、订购与模拟授权；切换 DEMO-002，显示“未找到”；切换 DEMO-003，显示账单 30.00 CNY 与订购 20.00 CNY/月。
- 上述浏览器请求通过 Vite 代理访问实际后端，不是前端静态假数据。
- Compose `up -d --wait` 成功，MySQL 容器健康。

最初沙箱禁止监听端口及访问 Docker socket；在获得工具执行许可后完成真实运行验证，不属于应用错误。

## CI 与未完成项

`.github/workflows/ci.yml` 配置 push/PR/手动触发，后端启动真实 MySQL 并运行 Maven verify，前端运行 npm ci/test/build。YAML 已通过本地语法解析。**尚未推送和触发 GitHub Actions，不能将本地成功写成远端 CI 通过。**

Issue #1 的代码、文档与本地验收已完成；远端 CI 执行待推送后确认。未实施 AI 预审、人工审核、业务规则判断、业务数据持久化、宿主认证、镜像发布或演示服务器部署。行业调研为公开资料研究，没有真实客服访谈或业务收益测量。
