# Backend Authentication Scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让后端模板保留管理员建用户与密码登录，移除自助注册、社交绑定和小程序登录，并让短信、邮箱登录默认关闭。

**Architecture:** 永久排除的认证方式删除路由、策略和专用数据；可选的短信、邮箱策略保留源码，由服务端允许清单和客户端授权共同控制。验证码发送入口使用同一允许清单；新建库种子与服务端默认值一致。

**Tech Stack:** Java 21、Spring Boot 4、Sa-Token、Maven、MySQL/Oracle/PostgreSQL/SQL Server 初始化 SQL。

**Spec:** `docs/superpowers/specs/2026-09-29-backend-template-scope-design.md`

## Global Constraints

- 仅改当前后端仓库；不涉及前端。
- 自助注册、个人社交绑定及小程序登录不进入默认模板。企业单点登录另立需求。
- 保留 `SysUserController` 的管理员新增用户、密码登录、图片验证码和登录失败限制。
- `ruoyi-common-social` 源码及其构建版本可留在仓库，但不进入默认 `ruoyi-admin` 运行包。
- 只修改新建库初始化 SQL；已有数据库迁移不在本计划内。
- 不碰仓库中已有的无关未跟踪目录；每个任务只提交自己的文件。

## Review Focus

1. 管理员把客户端授权类型手工改成 `social` 或 `xcx`：登录仍应失败且不生成令牌；Task 2 的 HTTP 回归覆盖。
2. 管理员把授权类型改成 `sms` 或 `email`，但服务端允许清单未开放：登录和验证码发送都应失败；Task 3 覆盖。
3. 登录方式大小写或前后多余字符：不可被字符串子串匹配误放行；Task 3 的精确匹配测试覆盖。
4. 删除自助注册后，管理员新增用户仍应成功；Task 1 的管理接口回归覆盖。
5. 停用短信/邮箱时，验证码提供方未配置：请求不得调用提供方或写入缓存；Task 3 的接口测试覆盖。

---

## File map

| 边界 | 文件职责 |
| --- | --- |
| 认证 HTTP | `ruoyi-admin/src/main/java/org/dromara/web/controller/AuthController.java`、`CaptchaController.java` 只保留当前允许的入口。 |
| 认证策略 | `ruoyi-admin/src/main/java/org/dromara/web/service/impl/` 中保留密码、短信、邮箱策略；删除社交、小程序策略。 |
| 服务端门禁 | 新建 `ruoyi-admin/src/main/java/org/dromara/web/config/AuthGrantProperties.java` 与 `ruoyi-admin/src/main/java/org/dromara/web/service/AuthGrantPolicy.java`，统一判断允许的登录方式。 |
| 系统与 API | `ruoyi-modules/ruoyi-system`、`ruoyi-api` 删除注册/社交专用接口和模型，管理员建用户链路保持。 |
| 新库种子 | `script/sql/ry_vue.sql` 及 `oracle/`、`postgres/`、`sqlserver/` 的 `*_ry_vue.sql` 同步处理表、配置和客户端授权。 |
| 主程序依赖 | `ruoyi-admin/pom.xml` 解除 `ruoyi-common-social`；公共模块源码及 BOM 版本保留。 |

### Task 1: 删除用户自助注册

**Files:** Modify `ruoyi-admin/src/main/java/org/dromara/web/controller/AuthController.java`、`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/{ISysUserService,ISysConfigService}.java` 及对应 `impl/`；Delete `ruoyi-admin/src/main/java/org/dromara/web/service/SysRegisterService.java`、`ruoyi-api/src/main/java/org/dromara/system/api/model/RegisterBody.java`；Modify 四份 `*_ry_vue.sql`；Create `ruoyi-admin/src/test/java/org/dromara/web/controller/AuthRouteTest.java`。

**Interfaces:** 删除 `POST /auth/register`、`ISysUserService.registerUser(SysUserBo)`、`ISysConfigService.selectRegisterEnabled()`；保留 `ISysUserService.insertUser(SysUserBo)` 与 `SysUserController` 管理接口。

- [ ] **Step 1: 写路由回归测试。** 在 `AuthRouteTest` 用 `MockMvcBuilders.standaloneSetup(AuthController)` 断言 `POST /auth/register` 无映射；另用管理员用户接口的现有调用或集成用例断言新增用户仍成功。前者在删除路由前应失败。
- [ ] **Step 2: 运行红灯。** `./mvnw.cmd -B -ntp -pl ruoyi-admin -am -Dtest=AuthRouteTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期注册路由断言失败。
- [ ] **Step 3: 做最小清理。** 删除上述注册入口、专用服务/模型/方法，四份初始化 SQL 删除 `sys.account.registerUser` 种子。保留 `SysUserServiceImpl.insertUser(SysUserBo)`，逐个检查专用文案是否仍被引用。
- [ ] **Step 4: 运行绿灯与管理接口回归。** 重跑 Task 1 命令；新库或现有测试环境调用管理员新增用户接口后验证用户可由密码登录。记录管理接口使用的权限和数据库前提。
- [ ] **Step 5: 仅提交 Task 1 文件。** 提交前检查 `git diff --check`、暂存清单和四份 SQL 的注册键一致性。

### Task 2: 删除社交绑定与小程序登录

**Files:** Modify `AuthController.java`、`ruoyi-admin/src/main/java/org/dromara/web/service/SysLoginService.java`、`ruoyi-admin/pom.xml`、`ruoyi-admin/src/main/resources/application-{dev,prod}.yml`；Delete `ruoyi-admin/src/main/java/org/dromara/web/service/impl/{SocialAuthStrategy,XcxAuthStrategy}.java`、`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysSocialController.java` 及 `SysSocial` 的 service/mapper/domain/VO/BO/Mapper.xml、`ruoyi-api` 的 `SocialLoginBody` 与小程序专用模型；Modify 四份 `*_ry_vue.sql`；Extend `AuthRouteTest.java`。

**Interfaces:** 删除 `/auth/binding/{source}`、`/auth/social/callback`、`/auth/unlock/{socialId}`、`/system/social/list`，以及 `SysLoginService.socialRegister(AuthUser)`。`IAuthStrategy.login(String, SysClientVo, String)` 仍作为其余登录方式的入口。

- [ ] **Step 1: 写红灯测试。** 扩展 `AuthRouteTest`，验证三个 `/auth` 社交路由不再映射；`/system/social/list` 在启动后的实际 HTTP 回归中检查。把 `social`、`xcx` 写入客户端授权类型后，实际 HTTP 登录不得得到令牌。先运行，预期现有社交路由断言失败。
- [ ] **Step 2: 清理 Java、Maven 与环境配置。** 同一批删除社交和小程序策略，避免 `XcxAuthStrategy` 的 `JustAuth` 导入让解除 `ruoyi-common-social` 后编译失败。删除社交服务链路和专用模型；保留 `ruoyi-common-social` 源码/BOM。
- [ ] **Step 3: 清理新库数据。** 四份 SQL 删除 `sys_social` 表及社交授权种子；本任务只从客户端授权字符串移除 `social`，其余授权方式由 Task 3 处理。核对是否有 `sys_social` 的索引、约束和引用。
- [ ] **Step 4: 运行绿灯与依赖检查。** 重跑 Task 1 Maven 命令及 `./mvnw.cmd -B -ntp -pl ruoyi-admin -am -DskipTests package`；确认默认 `ruoyi-admin` 依赖树不含 `ruoyi-common-social`，实际登录请求中的 `social`、`xcx` 失败。保留密码登录成功样本。
- [ ] **Step 5: 仅提交 Task 2 文件。** 检查所有 Java 引用、四份 SQL 与 `application-dev.yml`/`application-prod.yml` 一致，提交本任务变更。

### Task 3: 短信、邮箱登录默认关闭且可以显式启用

**Files:** Create `ruoyi-admin/src/main/java/org/dromara/web/config/AuthGrantProperties.java`、`ruoyi-admin/src/main/java/org/dromara/web/service/AuthGrantPolicy.java`、`ruoyi-admin/src/test/java/org/dromara/web/service/AuthGrantPolicyTest.java`；Modify `AuthController.java`、`CaptchaController.java`、`ruoyi-admin/src/main/resources/application.yml`、四份 `*_ry_vue.sql`；Extend `AuthRouteTest.java`。

**Interfaces:** `AuthGrantProperties.enabledGrantTypes: Set<String>` 对应 `auth.enabled-grant-types`，默认精确值 `password`；`AuthGrantPolicy.isEnabled(String grantType): boolean` 做服务端精确集合匹配；`AuthGrantPolicy.isClientEnabled(String clientGrantTypes, String grantType): boolean` 按逗号分隔客户端授权并精确匹配。`AuthController.login` 在调用 `IAuthStrategy.login` 前要求两者均允许；验证码发送入口要求同一服务端允许清单。

- [ ] **Step 1: 写红灯测试。** `AuthGrantPolicyTest` 断言服务端对 `password=true`，对 `sms/email/social/xcx/unknown/空值/password-extra/PASSWORD=false`；客户端授权 `password,sms` 允许 `password`，`password-extra,social` 不允许 `password`。`AuthRouteTest` 增加未启用 `sms/email` 时登录失败、两个验证码发送接口失败且不调用提供方/缓存的断言；启用对应方式时只检查请求能越过门禁，不在单元测试中真实发短信或邮件。
- [ ] **Step 2: 运行红灯。** `./mvnw.cmd -B -ntp -pl ruoyi-admin -am -Dtest=AuthGrantPolicyTest,AuthRouteTest -Dsurefire.failIfNoSpecifiedTests=false test`；预期新策略测试或禁用入口测试失败。
- [ ] **Step 3: 实现门禁与种子。** 按 `auth.enabled-grant-types: [password]` 绑定配置；登录先做服务端精确匹配，再按逗号分隔的客户端授权值精确匹配，替换现有的字符串子串判断；短信、邮箱验证码入口在接触发送器和 Redis 前拒绝未启用状态。四份 SQL 的客户端授权种子统一改为只含 `password`；管理员仍可配置客户端，但不能绕过服务端门禁。
- [ ] **Step 4: 运行绿灯及真实请求。** 重跑 Task 3 Maven 命令；新库默认配置下确认密码登录成功，`sms/email/social/xcx` 登录失败，短信/邮箱验证码请求不触发发送。再用测试配置显式开放 `sms` 或 `email`，验证还需要对应客户端授权与提供方配置。
- [ ] **Step 5: 仅提交 Task 3 文件。** 检查 `git diff --check`，核对默认配置与四份 SQL 一致后提交。

## Authentication plan completion gate

Task 1–3 完成后，以一份新建数据库实例做 HTTP 回归，并记录实际路由映射、依赖树、登录结果和管理员建用户结果。若任一数据库方言未真实导入，明确标记“仅静态核对”。再启动下一份可选模块计划。
