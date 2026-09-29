# Optional Backend Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让后端主程序默认只装入企业后台基础能力；Demo、AI、工作流、分布式任务、MCP、代码生成及其服务按需启用。

**Architecture:** 可选模块源码与 Maven reactor 保留，默认从 `ruoyi-admin` 的依赖或默认 profile 退出。配置、初始化 SQL 菜单和 Docker Compose 与实际装入的模块同步；接口文档和监控按开发、生产环境分别设定访问范围。

**Tech Stack:** Java 21、Spring Boot 4、Maven、Docker Compose、SQL 初始化脚本。

**Spec:** `docs/superpowers/specs/2026-09-29-backend-template-scope-design.md`

## Global Constraints

- 本计划只处理后端工程，不修改独立前端项目；应在 `2026-09-29-authentication-scope.md` 完成后执行。
- 可选模块源码及 Maven reactor 声明保留，默认 `ruoyi-admin` 包不含其业务入口。
- `ruoyi-system` 当前直接依赖 OSS、短信、推送；本计划不拆这些公共组件或改变文件管理、通知公告的业务行为。
- 各数据库方言的新库种子保持一致；已有数据库迁移另立任务。
- 原有无关未跟踪目录不参与提交；每项任务只提交自己的改动。

## Review Focus

1. 只删除主程序直接依赖，传递依赖仍把 Demo、AI 或 MCP 装回：Task 1、2、5 的依赖树检查覆盖。
2. 菜单被移除后 `sys_role_menu` 仍指向旧菜单 ID：Task 1、2、4、5 的 SQL 引用检查覆盖。
3. `warm-flow.enabled=false` 但规则编排或流程 UI 仍加载：Task 3 的启动映射检查覆盖。
4. Compose 默认启动可选服务，或可选服务因依赖而被自动启动：Task 6 的 `docker compose config --services` 检查覆盖。
5. 生产文档/监控配置改变后健康检查也不可用：Task 6 的真实 HTTP 验证覆盖。

---

## File map

| 边界 | 文件职责 |
| --- | --- |
| 打包入口 | `ruoyi-admin/pom.xml` 控制主程序依赖及 `gen` profile；`pom.xml`、`ruoyi-modules/pom.xml`、`ruoyi-common/pom.xml` 保留源码构建清单。 |
| 功能开关 | `ruoyi-admin/src/main/resources/application.yml`、`application-dev.yml`、`application-prod.yml` 描述默认与环境差异。 |
| 新库菜单 | `script/sql/ry_vue.sql` 及三份方言版 `*_ry_vue.sql` 与实际主程序功能同步；`ry_ai.sql`、`ry_job.sql`、`ry_workflow.sql` 作为独立功能数据脚本保留。 |
| 部署 | `script/docker/docker-compose.yml` 默认只启动选定的基础服务，可选后端服务按 Compose profile 显式启动。 |
| 交付说明 | 新建 `docs/backend-template-runbook.md` 记录默认清单、启用路径、配置替换与实际验证结果。 |

### Task 1: Demo 退出默认主程序

**Files:** Modify `ruoyi-admin/pom.xml` 和四份 `*_ry_vue.sql`；保留 `ruoyi-modules/ruoyi-demo/` 源码。

**Interfaces:** 默认主程序不注册 `org.dromara.demo` 下的接口；管理员、角色、菜单等系统接口保持。

- [ ] **Step 1: 记录基线。** 保存默认 `ruoyi-admin` 依赖树与启动路由清单；确认当前有 `ruoyi-demo`，并定位四份 SQL 的测试菜单、子菜单和 `sys_role_menu` 引用。
- [ ] **Step 2: 解除依赖与种子。** 删除主程序对 `ruoyi-demo` 的直接依赖；从四份新库脚本移除 Demo 菜单及角色菜单引用，不删除 Demo 源码和独立构建声明。
- [ ] **Step 3: 验证。** `./mvnw.cmd -B -ntp -pl ruoyi-admin -am -DskipTests package` 通过；默认依赖树与启动路由不含 Demo，基础系统接口仍可调用。检查四份 SQL 中不存在悬空菜单父 ID/角色菜单 ID。
- [ ] **Step 4: 提交。** `git diff --check` 和暂存清单通过后，仅提交 Task 1 文件。

### Task 2: AI 退出默认主程序

**Files:** Modify `ruoyi-admin/pom.xml` 和四份 `*_ry_vue.sql`；保留 `ruoyi-modules/ruoyi-ai/`、`ruoyi-common/ruoyi-common-ai/`、现有的 `script/sql/ry_ai.sql` 与 `script/sql/postgres/postgres_ry_ai.sql`。

**Interfaces:** 默认主程序不注册 AI 业务接口；SnailAI 客户端继续关闭，独立服务在 Task 6 调整为按需启动。

- [ ] **Step 1: 记录 AI 基线。** 确认 `ruoyi-ai` 的直接/传递依赖、AI 会话及控制台菜单，定位角色菜单引用和 `ry_ai.sql` 的导入前提。
- [ ] **Step 2: 解除依赖并清理默认菜单。** 保留源码和专用 SQL；四份新库脚本移除 AI 会话、AI 控制台及相关权限种子。
- [ ] **Step 3: 验证。** 运行 Task 1 打包命令；默认依赖树和启动映射不含 AI 业务，系统管理接口正常；四份 SQL 没有悬空 AI 菜单 ID。
- [ ] **Step 4: 提交。** 检查 `git diff --check`，只提交 AI 相关依赖与 SQL 改动。

### Task 3: 工作流退出默认主程序

**Files:** Modify `ruoyi-admin/pom.xml`、`ruoyi-admin/src/main/resources/application.yml`；保留 `ruoyi-modules/ruoyi-workflow/`、`ruoyi-common/ruoyi-common-liteflow/`、`script/sql/ry_workflow.sql` 及方言脚本。基础 `*_ry_vue.sql` 当前没有工作流菜单，本任务不改它们。

**Interfaces:** `warm-flow.enabled=false`，`liteflow.enable=false`，默认主程序不加载流程控制器、流程 UI 或规则编排链；系统消息模块保留现有通用接口。

- [ ] **Step 1: 列出流程依赖和入口。** 确认流程 API、规则自动配置、独立工作流 SQL，以及 `SysMessageServiceImpl` 中工作流消息分类的引用；记录当前启动映射。
- [ ] **Step 2: 解除主程序依赖并关闭配置。** 保留独立工作流 SQL，不在基础模板导入；不改基础脚本和通用消息表。
- [ ] **Step 3: 验证。** 运行 Task 1 打包命令及默认启动检查；依赖树和路由映射无工作流业务与流程 UI，`liteflow` 规则链未加载，用户与消息等基础接口仍可用。
- [ ] **Step 4: 提交。** 检查 `git diff --check`，只提交本任务文件。

### Task 4: 分布式任务退出默认主程序

**Files:** Modify `ruoyi-admin/pom.xml`、四份 `*_ry_vue.sql`；保留 `ruoyi-modules/ruoyi-job/`、`ruoyi-common/ruoyi-common-job/`、`script/sql/ry_job.sql` 及方言脚本。

**Interfaces:** 默认主程序不加载 SnailJob 客户端或任务处理器；原有环境配置中的 `snail-job.enabled=false` 保持。服务端在 Task 6 改为按需启动。

- [ ] **Step 1: 记录任务依赖、处理器和菜单基线。** 定位 SnailJob 控制台菜单和角色菜单引用，并确认专用 SQL 不参与基础库初始化。
- [ ] **Step 2: 解除依赖并移除默认菜单。** 保留模块源码和专用 SQL；四份基础脚本同步清理任务菜单及引用。
- [ ] **Step 3: 验证。** 运行 Task 1 打包命令；默认依赖树和启动信息无 SnailJob 客户端，基础应用可启动，四份 SQL 不存在悬空任务菜单 ID。
- [ ] **Step 4: 提交。** 检查暂存清单与 `git diff --check` 后提交。

### Task 5: MCP 默认关闭，代码生成改为显式工具

**Files:** Modify `ruoyi-admin/pom.xml`、`ruoyi-admin/src/main/resources/application.yml`、四份 `*_ry_vue.sql`；保留 `ruoyi-common/ruoyi-common-mcp/`、`ruoyi-modules/ruoyi-gen/` 源码。

**Interfaces:** 默认包不含 MCP 服务端，`spring.ai.mcp.server.enabled=false`；`gen` profile 不默认激活，显式启用后才引入 `ruoyi-gen`。

- [ ] **Step 1: 记录当前 MCP 和代码生成基线。** 确认 MCP 直接/传递依赖、`/mcp` 路由、`gen` profile 生效方式以及代码生成菜单/权限 ID。
- [ ] **Step 2: 调整依赖、开关与种子。** 从默认主程序解除 `ruoyi-common-mcp`；关闭 MCP 服务端；取消 `gen` 的 `activeByDefault`；四份新库脚本同步清理代码生成菜单与其权限、角色引用。源码及依赖版本管理保留。
- [ ] **Step 3: 验证默认与显式构建。** 默认执行 Task 1 打包命令，依赖树和路由无 MCP/代码生成；再用 `./mvnw.cmd -B -ntp -pl ruoyi-admin -am -Pgen -DskipTests package` 验证开发工具仍可显式构建。记录开发环境恢复菜单的步骤。
- [ ] **Step 4: 提交。** `git diff --check` 与四份 SQL 引用检查通过后，仅提交本任务文件。

### Task 6: 默认部署与管理端点收口

**Files:** Modify `script/docker/docker-compose.yml`、`ruoyi-admin/src/main/resources/application.yml`、`application-prod.yml`；Create `docs/backend-template-runbook.md`。

**Interfaces:** Compose 默认不启动 `ruoyi-monitor-admin`、`ruoyi-snailjob-server`、`ruoyi-snailai-server`；显式 profile 可选择各服务。生产配置默认不提供接口文档，Actuator 只暴露健康/必要信息；基础健康检查仍可用。

- [ ] **Step 1: 记录部署与访问基线。** 收集 Compose 服务列表、生产配置下 `/actuator/health`、OpenAPI、MCP 和已退出模块路由的实际状态；不在文档或日志中复制示例密钥。
- [ ] **Step 2: 配置默认与可选服务。** 为三个可选后端服务加独立 Compose profile；保留基础服务与现有 nginx 定义，不修改前端工程。清理 `security.excludes` 中仅属于已退出工作流/AI 的例外。生产配置关闭 OpenAPI，Actuator 暴露范围限定为 `health,info`；开发配置按需要保留文档。
- [ ] **Step 3: 写运行手册。** 记录默认模块和服务、各可选模块的依赖/配置/专用 SQL/菜单恢复步骤、开发 `gen` 命令、生产部署前必须替换的凭证与密钥、未覆盖数据库方言的验证状态。
- [ ] **Step 4: 验证配置与真实 HTTP。** `docker compose -f script/docker/docker-compose.yml config --services` 的默认结果不含三个可选服务；显式 profile 能显示对应服务。运行 Task 1 打包命令并启动基础应用，验证密码登录和基础管理接口可用，生产文档/MCP/流程/AI 路由不可用，`/actuator/health` 按既定鉴权可用。
- [ ] **Step 5: 提交。** 检查 `git diff --check`、Compose 配置、文档中的密钥占位说明和暂存清单，仅提交本任务文件。

## Optional-module plan completion gate

在全新主数据库导入基础脚本，至少完成一次真实启动与 HTTP 冒烟；静态核对其余三种数据库方言的菜单/角色引用。保存默认与 `-Pgen` 的依赖树、Compose 默认与显式 profile 服务列表、生产端点验证结果。若某项因外部服务或数据库不可用而未运行，逐项标明未验证，不写“全部通过”。
