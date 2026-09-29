# 后端基础模板运行说明

本说明适用于**新建数据库**。已有数据库中的菜单、权限和业务数据不受初始化脚本改动影响；已有环境若要退出模块，需另做数据迁移和回退方案。本次不修改独立前端工程。

## 默认运行范围

- `ruoyi-admin` 默认装入认证、`ruoyi-system`、`ruoyi-api` 和通用组件。账号密码登录、用户、部门、角色、菜单、字典、参数、日志、通知和文件等基础能力保留。`ruoyi-system` 仍直接依赖 OSS、短信和消息推送；短信、邮箱**登录**由 `auth.enabled-grant-types: [password]` 默认关闭。
- 默认包不含 `ruoyi-demo`、`ruoyi-ai`、`ruoyi-workflow`、`ruoyi-job`、`ruoyi-common-mcp` 和 `ruoyi-gen`。这些模块的源码和 Maven reactor 声明仍在仓库中。
- 新库先导入对应方言的 `*_ry_vue.sql`。默认菜单只对应基础能力；Demo 测试表与数据不再建入新库。`gen_table` 和 `gen_table_column` 仍在基础脚本中，方便开发时显式开启代码生成。
- `docker compose -f script/docker/docker-compose.yml config --services` 默认列出 `mysql`、`redis`、`minio`、`nginx-web`、`ruoyi-server1`、`ruoyi-server2`。这只是服务清单；启动前仍需准备镜像、配置和挂载目录。监控中心、SnailJob、SnailAI 均不会默认加入清单。

## 按需启用

先在非生产环境完成依赖、配置、数据、菜单和接口验收，再构建要交付的包。服务端能力不能靠增加菜单单独启用。恢复菜单后，还要给对应角色授权；需要页面时，另核对独立前端是否提供该页面。

| 能力 | 主程序与配置 | 数据和菜单 | 独立服务 |
| --- | --- | --- | --- |
| Demo | 在 `ruoyi-admin/pom.xml` 加回 `ruoyi-demo`。 | 单独准备 `test_demo`、`test_tree` 表和演示数据，再恢复测试单表、测试树表及按钮权限。当前没有独立 Demo SQL，不要直接导入到生产库。 | 无。 |
| AI | 加回 `ruoyi-ai`，按环境配置 SnailAI 客户端。 | AI 服务数据库导入 `script/sql/ry_ai.sql`；PostgreSQL 有 `script/sql/postgres/postgres_ry_ai.sql`。基础库按需恢复 AI 会话、控制台菜单及角色权限。Oracle/SQL Server 没有对应 AI 专用脚本。 | `docker compose -f script/docker/docker-compose.yml --profile snailai up -d`。先配置应用 ID、令牌和 AI 服务连接。 |
| 工作流 | 加回 `ruoyi-workflow`，显式设置 `warm-flow.enabled=true`、`warm-flow.ui=true`、`liteflow.enable=true`。 | 在基础库之后导入对应方言的 `ry_workflow.sql`，核对其中的流程菜单与权限。通用消息模块中的 `workflow` 分类仍保留。 | 无单独 Compose 服务。 |
| 分布式任务 | 加回 `ruoyi-job`，显式开启 `snail-job.enabled` 并配置组、令牌和服务地址。 | 在任务服务数据库导入对应方言的 `ry_job.sql`；基础库按需恢复 `monitor:snailjob:list` 控制台菜单与角色权限。 | `docker compose -f script/docker/docker-compose.yml --profile snailjob up -d`。 |
| MCP | 加回 `ruoyi-common-mcp`，在确定鉴权和工具范围后显式开启 `spring.ai.mcp.server.enabled`。 | 默认没有专用 SQL 或菜单。 | 与主程序共用端口，按启用后的路由和鉴权实测。 |
| 代码生成 | 开发构建使用 `./mvnw.cmd -B -ntp -pl ruoyi-admin -am -Pgen -DskipTests package`；生产构建不要加 `-Pgen`。 | 基础脚本保留生成业务表；在菜单管理中恢复系统工具下的代码生成入口、编辑入口，以及 `tool:gen:list/query/edit/remove/import/preview/code` 权限并授权开发角色。 | 无。 |
| 监控中心 | 主程序环境配置中的 `spring.boot.admin.client.enabled` 当前为 `false`；要接入监控中心时显式启用并配置认证信息。 | 基础库按需恢复 `monitor:admin:list` 菜单与角色权限。 | `docker compose -f script/docker/docker-compose.yml --profile monitor up -d`。 |

可先运行 `docker compose -f script/docker/docker-compose.yml --profile <profile> config --services` 核对清单。Compose profile 只控制服务选择，不能自动恢复主程序依赖、SQL 或菜单。

## 生产部署前检查

1. 替换数据库、Redis、MinIO、监控 Basic Auth、种子管理员和客户端的示例凭证。更换 Sa-Token JWT 密钥及 API 请求/响应加密密钥对；若启用邮件、短信、SnailJob 或 SnailAI，再配置各自的服务凭证和令牌。不要把实际密钥写入此文档或提交到仓库。
2. 核对 `application-prod.yml` 的连接地址、文件上传目录、日志目录以及 Compose 的主机挂载路径。生产接口文档与 Swagger UI 默认关闭；Actuator 只暴露 `health,info`，并继续要求 Basic Auth。健康检查若被部署探针使用，须为探针配置对应认证。
3. 只导入选定方言的基础脚本；可选模块的专用 SQL 需按需、按库执行。不要把新库脚本当成已有数据库的迁移脚本。

## 2026-09-29 验证范围

| 项目 | 结果 |
| --- | --- |
| Maven | 默认 `ruoyi-admin` 打包成功；默认依赖树和 JAR 不含六个可选模块。`-Pgen` 打包成功，依赖树和 JAR 包含 `ruoyi-gen`。 |
| 新库 | 隔离 MySQL 5.7.44 中完整导入最终 `script/sql/ry_vue.sql`：84 个菜单、76 条角色菜单关系、0 条悬空关系；客户端授权种子为 `password`，没有 Demo 测试表。 |
| 开发 HTTP | 使用默认图片验证码、现有 RSA/AES 请求加密及密码登录取得令牌，`/system/user/list` 返回业务 `code=200`；开发 OpenAPI 可访问。Demo、AI、工作流、MCP、代码生成路由返回业务 `code=404`。 |
| 生产 HTTP | 密码登录与用户列表返回业务 `code=200`；`/actuator/health` 无认证返回 HTTP 401，Basic Auth 后返回 HTTP 200、`status=UP`；`/actuator/info` 可用。`/actuator/env`、OpenAPI、Swagger UI、MCP、流程和 AI 路由均返回业务 `code=404`。项目的全局异常处理会把这类无路由结果放进 HTTP 200 的 JSON 响应。 |
| Compose | 默认清单没有三个可选服务；`monitor`、`snailjob`、`snailai` 三个 profile 分别能列出对应服务。只验证了 Compose 配置，没有启动整套部署。 |
| 其他数据库 | Oracle、PostgreSQL、SQL Server 的基础脚本做了菜单父子 ID 与角色引用静态核对；未做真实导入。已有数据库迁移未验证。 |

依赖树复核命令分别为 `./mvnw.cmd -B -ntp -pl ruoyi-admin -am dependency:tree` 和 `./mvnw.cmd -B -ntp -pl ruoyi-admin -am -Pgen dependency:tree`。默认树中 `ruoyi-demo`、`ruoyi-ai`、`ruoyi-workflow`、`ruoyi-job`、`ruoyi-common-mcp`、`ruoyi-gen` 均无条目；显式 `gen` 树中有 `org.dromara:ruoyi-gen:jar:6.0.0:compile`。这两次构建的 JAR 内容与依赖树一致。

上述 HTTP 验证在隔离的 MySQL、Redis 和本机 Java 进程中完成；未连接真实短信、邮件、AI 或任务服务。
