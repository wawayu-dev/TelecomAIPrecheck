# 本地开发环境

前置：JDK 17、Maven 3.9+、Node 22.12+（推荐 Node 22 LTS）、npm；MySQL 模式额外需要 Docker Compose v2。

## 无数据库启动（默认）

在仓库根目录执行：

```sh
mvn -f backend/pom.xml spring-boot:run
```

另开终端：

```sh
cd frontend
npm ci
npm run dev
```

访问 Vite 输出的本地地址（默认 http://127.0.0.1:5173）。选择三个合成场景，点击“读取上下文”。前端不是 AI 预审页，仅验证宿主契约读取。

```sh
curl http://127.0.0.1:8080/actuator/health
curl http://127.0.0.1:8080/api/tickets/DEMO-002/context
```

后端默认 `mock` profile，禁用 JDBC 自动配置；Spring AI 仅引入基础模型依赖，无远程调用，也无需 API Key。未来接 MaaS 时再新增客户端及对应密钥变量，当前不放无效配置。

## MySQL 环境（可选）

Compose 按约定存放在本目录，仅启动 MySQL 8.4 LTS，不包含应用镜像。以下命令在仓库根目录执行；示例密码只供本地使用，应换成自己的值：

```sh
export DB_PASSWORD='replace-with-local-password'
export MYSQL_ROOT_PASSWORD='replace-with-local-root-password'
docker compose -f docs/TelecomAIPrecheck/docker-compose.yml up -d --wait
export SPRING_PROFILES_ACTIVE=mysql
export DB_USERNAME=precheck
mvn -f backend/pom.xml spring-boot:run
```

默认本地端口 3306；如已被占用，设置 `MYSQL_PORT=13306`，同时将 `DB_URL` 改为对应端口。连接使用 MySQL 默认 TLS 能力；CI 临时服务显式禁用 SSL 并允许公钥获取，仅适用于隔离测试环境。

```sh
export RUN_MYSQL_TESTS=true
mvn -B -ntp -f backend/pom.xml verify
```

`MySqlConnectivityTest` 通过 MyBatis 执行 `SELECT 1`；没有本阶段不需要的业务表。不要同时启用 mock 与 mysql profile，否则 mock 会排除数据源自动配置。

停止本项目数据库（保留数据卷）：

```sh
docker compose -f docs/TelecomAIPrecheck/docker-compose.yml down
```

## 环境变量

| 变量 | 默认／用途 |
| --- | --- |
| SPRING_PROFILES_ACTIVE | 未设置时 mock；可显式 mysql |
| SERVER_ADDRESS / SERVER_PORT | 127.0.0.1 / 8080 |
| CONTEXT_PROVIDER | mock；真实适配器设 host 并注册 Bean |
| DB_URL / DB_USERNAME / DB_PASSWORD | mysql profile 使用；密码无默认值 |
| MYSQL_ROOT_PASSWORD / MYSQL_PORT | Compose 管理密码／宿主端口，默认端口 3306 |
| BACKEND_URL | Vite 开发代理目标，默认 http://127.0.0.1:8080 |
| RUN_MYSQL_TESTS | true 时运行真实数据库测试 |

`backend/.env.example` 为变量模板，Spring Boot 不自动读取 .env。使用 export 或 IDE 配置；前端 `.env.local` 可设置 BACKEND_URL。不要提交 .env，不要把密钥放入 VITE_ 开头的变量。

## 构建与测试

```sh
mvn -B -ntp -f backend/pom.xml verify
cd frontend
npm ci
npm test
npm run build
```

Maven 生成 `backend/target/ai-precheck-0.1.0-SNAPSHOT.jar`。可从仓库根目录执行 `java -jar backend/target/ai-precheck-0.1.0-SNAPSHOT.jar`。前端产物为 `frontend/dist/`；生产托管需另行配置 API 反向代理，Vite preview 不是部署方案。

GitHub Actions 在 push、pull_request 或手动触发时构建，后端任务启动 MySQL 服务并启用数据库测试；前端使用 npm ci、测试与生产构建。镜像发布和演示环境部署留待 Issue #5。

提交格式：`feat: 中文说明`、`chore: 中文说明` 等类型前缀加英文冒号与中文描述。
