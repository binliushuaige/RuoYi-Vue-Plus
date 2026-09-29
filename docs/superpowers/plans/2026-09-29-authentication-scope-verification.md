# 认证范围计划验收记录

日期：2026-09-29

对应计划：`2026-09-29-authentication-scope.md`。本记录只覆盖该计划的认证范围；设计文档中的可选模块精简、部署和生产环境范围属于后续批次。

## 构建与测试

- `./mvnw.cmd -B -ntp -pl ruoyi-admin -am test`：整个 reactor 成功，`ruoyi-admin` 13 个测试通过。
- `./mvnw.cmd -B -ntp -pl ruoyi-admin -am -DskipTests package`：成功。
- `./mvnw.cmd -B -ntp -pl ruoyi-admin -am dependency:tree -Dincludes=org.dromara:ruoyi-common-social`：成功，输出中没有 `ruoyi-common-social` 依赖。

## 新建库 HTTP 验收

在隔离的 MySQL 5.7.44 和 Redis 7.4.2 实例中，重新创建 UTF-8 数据库并完整导入 `script/sql/ry_vue.sql`，以 `dev` 配置启动 `ruoyi-admin`。新库含两个客户端，授权种子均为 `password`；没有 `sys_social` 表。

本项目的全局异常处理会将业务错误放在 JSON 的 `code` 字段中，以下请求的 HTTP 状态均为 200：

| 验收项 | 实际结果 |
| --- | --- |
| `POST /auth/register`、三个社交绑定/解绑路由、`GET /system/social/list` | JSON `code=404`，路由不存在。 |
| 默认配置的 `sms`、`email`、`social`、`xcx` 登录 | JSON `code=500`，均未产生令牌。`password-extra`、`PASSWORD` 和前置空格的 ` password` 也被拒绝。 |
| 默认配置的短信、邮箱验证码入口 | JSON `code=500`；测试用手机号、邮箱没有新增验证码缓存键。单元测试还验证未调用短信提供方或 Redis。 |
| 管理员密码登录与图片验证码 | JSON `code=200`，取得令牌。 |
| 管理员 `POST /system/user` 新增用户 | 携带 `Bearer` 令牌及客户端 ID，JSON `code=200`。接口要求 `system:user:add` 权限；此测试由新库默认管理员执行。 |
| 新增用户密码登录、错误密码、管理员退出 | 分别为 `code=200`、`code=500`、`code=200`。 |

又将隔离库的测试客户端授权临时改为 `password,sms,email,social,xcx` 并重启默认配置实例。上述被禁用的登录方式仍全部失败，密码登录及管理员建用户仍成功。测试后客户端授权已恢复为 `password`。

另用测试配置将服务端允许清单显式设为 `password,sms,email`：客户端仍只有 `password` 时，短信、邮箱登录被客户端授权门禁拒绝；客户端同时开放这两种授权后，请求进入各自策略，并因缺少验证码或邮箱等输入而失败。短信验证码入口已越过服务端门禁，进入手机号格式校验；邮箱入口进入原有的邮件功能开关检查。没有配置实际短信或邮件提供方，也没有发送真实消息。

## 数据库方言范围

MySQL 初始化脚本已真实导入。Oracle、PostgreSQL 和 SQL Server 脚本仅静态核对：均不再含 `sys_social`、自助注册配置及社交/小程序授权种子，两个客户端授权种子均为 `password`。这三种方言未做真实导入；已有数据库迁移不在本计划内。
