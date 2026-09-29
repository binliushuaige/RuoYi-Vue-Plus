# RuoYi-Vue-Plus 后端基础模板功能收口设计

状态：待业务方评审
日期：2026-09-29
范围：当前仓库的后端源码、Maven、配置、初始化 SQL 和后端部署文件；不包含任何前端工程。

## 1. 目标与边界

将当前开源工程整理为企业业务系统可复用的后端基础模板。默认可用的主程序只承载账号密码登录、组织与权限管理、基础配置、审计等通用能力。明确不需要的自助注册和社交账号绑定从服务入口及初始化数据中移除。未来可能使用的独立能力保留源码，通过主程序依赖、配置与部署清单控制是否启用。

已确认的业务方向：

- 不提供用户自助注册；管理员创建和维护用户仍然需要。
- 不提供个人社交账号登录或绑定；基础模板默认使用账号密码登录。
- 企业统一身份认证不复用现有的个人账号绑定流程。将来有明确需求时另行设计身份映射、账户开通和退出规则。
- 本次只形成设计，不修改产品代码、配置或数据库，也不处理前端。

本设计以**新建企业项目的初始化模板**为目标。若现有环境已经导入数据或开始使用这些能力，实施前须另列数据迁移方案，不直接删除在用表或历史数据。

## 2. 当前工程依据

| 事实 | 依据 |
| --- | --- |
| 认证控制器整体带 `@SaIgnore`；注册接口存在，运行时读取可修改的注册开关；初始化值为 `false`。 | `ruoyi-admin/src/main/java/org/dromara/web/controller/AuthController.java:52-56,180-187`；`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysConfigServiceImpl.java:84-92`；`script/sql/ry_vue.sql:660` |
| 社交能力横跨认证接口、登录策略、系统模块、独立公共模块、客户端授权类型和 SQL 表。 | `AuthController.java:109-161`；`ruoyi-admin/src/main/java/org/dromara/web/service/impl/SocialAuthStrategy.java:39-84`；`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysSocialController.java:25-37`；`ruoyi-common/ruoyi-common-social/pom.xml:12-29`；`script/sql/ry_vue.sql:4,868-869` |
| 小程序登录策略也直接导入 `JustAuth`，不可仅解除 `ruoyi-common-social` 依赖。 | `ruoyi-admin/src/main/java/org/dromara/web/service/impl/XcxAuthStrategy.java:8-14,35-63` |
| 密码、短信、邮箱、社交和小程序登录由 `IAuthStrategy` 按授权类型查找策略 Bean；客户端管理可修改授权类型。 | `ruoyi-admin/src/main/java/org/dromara/web/service/IAuthStrategy.java:19-39`；`AuthController.java:75-92`；`ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysClientController.java:80-111` |
| 短信验证码发送接口独立存在；邮箱发送已有 `mail.enabled` 检查。 | `ruoyi-admin/src/main/java/org/dromara/web/controller/CaptchaController.java:55-99` |
| 主程序当前直接引入社交、邮件、MCP、任务、AI、Demo 和工作流；代码生成通过默认激活的 `gen` profile 引入。 | `ruoyi-admin/pom.xml:48-111,128-143` |
| 工作流、MCP、消息推送和接口文档当前配置为启用；SnailJob 与 SnailAI 客户端在环境配置中关闭。 | `ruoyi-admin/src/main/resources/application.yml:159-162,216-250,346-358`；`ruoyi-admin/src/main/resources/application-dev.yml:14-45`；`application-prod.yml:17-48` |
| Docker Compose 同时定义基础服务及监控、SnailJob、SnailAI 服务。 | `script/docker/docker-compose.yml:2-174` |

`pom.xml` 中的 `<modules>` 表示参与 Maven 构建，`ruoyi-admin/pom.xml` 中的 `<dependencies>` 决定主程序直接引入的模块。父工程的 `dependencyManagement` 只管理依赖版本。三者不能当作同一种开关。

## 3. 可选方案与决定

| 方案 | 做法 | 结果 |
| --- | --- | --- |
| A. 仅隐藏与改配置 | 保留所有接口及主程序依赖，只关闭已有开关、隐藏菜单。 | 改动少，但注册开关可被重新打开，社交和短信入口仍在，不满足模板边界。 |
| **B. 分层收口（采用）** | 永久排除的能力清理入口和数据；未来可用的独立模块保留源码、退出默认主程序；共用组件使用明确的默认关闭配置。 | 默认运行包清楚，后续重新启用有路径，改动范围可分批验证。 |
| C. 大规模删除 | 删除所有当前不用的模块、配置、SQL 和依赖。 | 运行包更小，但升级上游及恢复通用能力的成本明显增加。 |

采用 B。代码注释只用于解释选择，不承担功能开关；是否能访问接口必须由实际路由、策略和运行时配置决定。

## 4. 默认能力边界

### 4.1 基础能力：保留并验证

- 账号密码登录、退出、图片验证码、登录失败限制、在线用户与强制退出。
- 管理员维护用户、部门、岗位、角色、菜单、客户端和数据权限。
- 字典、参数、操作日志、登录日志，以及现有基础的数据访问、安全与缓存组件。
- 文件管理、消息推送、短信和邮件的公共代码暂保留；其中是否对业务开放，按下文的功能入口逐项控制。`ruoyi-system` 已直接依赖 OSS、短信和推送，不能把它们视为只需删除一个主程序依赖的独立模块（`ruoyi-modules/ruoyi-system/pom.xml:49-100`）。

### 4.2 永久排除：自助注册、个人社交与小程序登录

**自助注册**：取消 `POST /auth/register`、`SysRegisterService`、`RegisterBody`、`ISysUserService.registerUser` 与实现、`selectRegisterEnabled` 及初始化配置 `sys.account.registerUser`。保留 `SysUserController` 中管理员新增用户的链路。移除专用文案与测试时，只处理确定不再被其他功能引用的内容。

**社交与小程序**：取消 `/auth/binding/{source}`、`/auth/social/callback`、`/auth/unlock/{socialId}`、`/system/social/list`、`SocialAuthStrategy`、`XcxAuthStrategy`、`SysLoginService.socialRegister` 及其专用模型、服务、Mapper。新建库的各数据库方言初始化脚本不再创建 `sys_social`，客户端种子数据不再包含 `social` 授权类型。清理 `application-dev.yml`、`application-prod.yml` 中的 `justauth` 配置；主程序解除 `ruoyi-common-social` 依赖。`ruoyi-common-social` 源码及其构建版本声明可暂保留在仓库，作为上游参考，不进入默认主程序。

清理后，即使管理员在客户端管理中手动写入 `social` 或 `xcx`，服务端也没有对应登录策略，不能产生登录令牌。企业单点登录后续须作为单独需求设计，不以恢复个人绑定接口作为捷径。

### 4.3 可选能力：保留源码，退出默认运行包

| 能力 | 默认动作 | 重新启用时需要同步处理 |
| --- | --- | --- |
| Demo | 解除主程序对 `ruoyi-demo` 的依赖，清理新库测试菜单及演示数据。 | 加回依赖、演示数据和专用菜单。 |
| AI | 解除 `ruoyi-ai` 依赖；默认不部署 SnailAI 服务。 | 接入配置、服务、数据脚本、权限和菜单统一恢复。 |
| 工作流 | 解除 `ruoyi-workflow` 依赖；`warm-flow.enabled` 与关联规则编排默认关闭；新库不导入工作流专用 SQL。 | 恢复模块、专用 SQL、流程菜单、服务配置并验证消息通知。 |
| 分布式任务 | 解除 `ruoyi-job` 默认依赖；SnailJob 客户端继续默认关闭，不启动服务端。 | 恢复依赖、服务端、任务 SQL、客户端配置和管理入口。 |
| MCP | 解除主程序的 `ruoyi-common-mcp` 依赖，服务端默认关闭。 | 明确鉴权和可用工具范围后恢复依赖、配置及访问测试。 |
| 代码生成 | `ruoyi-gen` 保留为开发工具，`gen` profile 改为显式启用；默认新库菜单不展示。 | 开发环境显式激活 profile，配套权限与菜单；生产包不引入。 |

模块源码可以留在 Maven reactor 中继续检查能否编译，但不能因此把它们重新加入 `ruoyi-admin` 运行包。部署文件应提供“基础模板”和“可选服务”的清晰选择；默认部署不启动监控中心、SnailJob、SnailAI 等未选择的服务。数据库菜单也应与实际启用的后端能力对应，不能用隐藏菜单代替接口收口。

### 4.4 共用组件与登录方式：默认关闭入口

基础模板的客户端种子数据只允许 `password`。短信与邮箱登录策略源码暂保留，增加服务端允许的登录方式清单，默认只有 `password`。一次登录须同时通过“服务端允许清单”和“当前客户端授权类型”两道检查，随后才可进入相应策略。短信、邮箱验证码发送入口也须检查同一服务端允许清单；未启用时直接返回明确的失败结果，不调用发送服务或写入验证码缓存。`/resource/sms/code` 当前没有单独的启用检查，是实施时必须覆盖的点（`CaptchaController.java:61-81`）。

默认登录链路为 `POST /auth/login` → 校验客户端及两道授权范围 → `PasswordAuthStrategy` 校验账号、密码与图片验证码 → 生成 Sa-Token → 查询用户权限。不存在注册或社交绑定分支。对未知、已删除或未启用的授权类型，统一在策略执行前拒绝；不能因数据库中的客户端配置变化而启用它们。重新启用短信或邮箱登录时，还需同时配置提供方和客户端授权，任一条件缺失都应失败。

现有消息推送和 OSS 与系统模块有直接依赖，第一轮不拆。先核对企业后台是否需要通知公告、文件上传和登录欢迎消息，再决定是否做独立化重构；本方案不以关闭配置代替已存在的调用链验证。

接口文档和监控保留开发用途，生产默认缩小访问范围。实施时分别确认 `/mcp`、OpenAPI 文档和 Actuator 的实际路由与鉴权；现有 `management.endpoints.web.exposure.include: '*'` 不作为企业模板默认值（`application.yml:205-214`）。示例中的密钥、默认口令和连接配置另列部署基线检查，不在设计文档中复制具体值。

## 5. 建议实施批次

1. **认证收口**：先处理注册、社交、小程序的接口与策略，再处理客户端种子、配置、表结构和多数据库脚本。保证管理员新增用户和密码登录仍可用。
2. **默认运行包精简**：逐项解除 Demo、AI、工作流、任务、MCP 的主程序依赖；关闭对应配置、整理新库菜单及 Compose 默认服务。每退出一个模块都做一次构建与启动检查。
3. **共用入口收口**：为短信、邮箱登录建立服务端默认关闭门禁；将代码生成改为显式开发 profile；检查文档和监控访问范围。
4. **模板交付**：记录默认启用清单、可选模块恢复步骤、初始化 SQL 选择和部署前必须替换的配置。完成后冻结一个可重复创建新项目的基线。

## 6. 验收标准

| 编号 | 可验证结果 |
| --- | --- |
| A1 | 新建数据库和默认配置下，管理员创建用户、密码登录、退出、权限访问、验证码及登录失败限制正常。 |
| A2 | `POST /auth/register` 及社交绑定、解绑、绑定列表接口不再注册为可用路由；`social`、`xcx` 授权类型无法产生令牌。 |
| A3 | 默认客户端只允许 `password`；即使修改客户端授权类型，已移除的认证策略也不能恢复。短信、邮箱的默认关闭门禁同时约束验证码发送和登录。 |
| A4 | 默认主程序依赖树和启动映射中不包含 Demo、AI、工作流、任务、社交、MCP 的业务入口；所选基础管理接口仍可用。 |
| A5 | 新库初始化脚本在当前支持的各数据库方言中保持一致：不建 `sys_social`，不种入自助注册配置和已退出模块的默认菜单；不误删用户、角色、权限等基础数据。 |
| A6 | 默认部署清单不启动未选择的扩展服务；生产环境的文档、MCP 和监控访问范围经过实际 HTTP 验证。 |
| A7 | `gen` 仅在显式启用时进入主程序；仓库保留可选模块源码及重新启用说明。 |

验证分为三层：静态依赖树与路由清单、主程序构建及基础集成测试、使用全新数据库的实际 HTTP 与部署冒烟测试。SQL 的主要数据库方言做一次真实导入，其他方言至少做结构与种子一致性检查；不能把代码检查当作所有数据库均已运行通过。

## 7. 风险与处理

| 风险 | 处理 |
| --- | --- |
| 仅从客户端数据删 `social`，管理员又配置回来。 | 同时删除服务端策略和绑定路由；验收包含手工写入已禁用授权类型的失败结果。 |
| 解除社交依赖后，小程序策略的 `JustAuth` 导入导致编译失败。 | 社交和小程序策略在同一批处理，依赖树和编译一起验证。 |
| 解除工作流或推送后，系统模块仍调用其接口。 | 按 Maven 依赖和 Java 引用逐项核对；工作流消息分类等残留只在明确业务口径后处理。 |
| 菜单、初始化 SQL、配置与实际代码不同步。 | 每个可选模块用一张启用清单贯穿依赖、配置、SQL、菜单、部署和验收。 |
| 对已有数据库直接删表造成数据损失。 | 本设计只规定新项目种子；已有环境先盘点数据并单独制定迁移与回退。 |

## 8. 本阶段交付边界

本文是待评审的正式设计，确定了默认后端能力、模块取舍和验收口径。后续实施需要再拆成可执行任务，逐批修改并验证；本文完成不代表代码、数据库或部署已改变。
