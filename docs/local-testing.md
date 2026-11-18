# 羽毛球智能编排系统 · 本地测试指南

> 目标：在 Windows 电脑上完整跑通「前端小程序 + 后端接口」联调。
> 文档日期：2026-08-20（已按本机环境实测确认工具与数据库就绪）

## 0. 环境检查结果（已确认 ✅）

| 项 | 状态 | 路径/版本 |
|---|---|---|
| MySQL | ✅ 运行中（3306 监听） | MySQL 8.0.36，root/123456 |
| badminton 数据库 | ✅ 已就绪 | 7 张表 + 3 个演示账号（密码 123456） |
| Java | ✅ | JDK 21（`C:\Program Files\Java\jdk-21.0.7`），满足 Spring Boot 3 要求 |
| Maven | ✅ | `D:\maven-3.9.11\apache-maven-3.9.11`（若命令行 mvn 报错，用 IDEA 内置或此路径） |
| HBuilderX | ✅ | `D:\HBuilderX.4.66.2025051912` |
| 微信开发者工具 | ✅ | `D:\微信web开发者工具` |
| IDEA | ✅ | `D:\IDEA 2024.3.4` |

## 1. 三步启动

### 第一步：启动后端（两种方式任选）

**方式 A（推荐）：双击项目根目录的 `启动后端.bat`**

保持窗口开启即可。关闭窗口 = 停止服务。

**方式 B（IDEA）**
1. IDEA 打开 `backend` 目录：`C:\Users\25026\Desktop\大创项目\羽毛球智能编排系统\backend`（选择 `pom.xml`）
2. 等待右侧 Maven 面板下载依赖（首次约 3~10 分钟，需联网）
3. 运行主类：`com.ape.badminton.BadmintonApplication`（绿色三角 ▶）
4. 看到 `Tomcat started on port 8083` 即启动成功

**方式 C（命令行）**
```bash
cd C:\Users\25026\Desktop\大创项目\羽毛球智能编排系统\backend
JAVA_HOME="C:\Program Files\Java\jdk-21.0.7" D:\maven-3.9.11\apache-maven-3.9.11\bin\mvn spring-boot:run
```

> 📁 **注意**：后端已从 `badminton/` 拆分到同级 **`backend/`** 目录。
> 前端在 `badminton/`，用 HBuilderX 打开。

**验证后端**（登录是 POST 接口，不能直接用浏览器打开）：
```json
{ "code": 0, "message": "success", "data": { "token": "tk_...", "user": { "user_id": "u_1001", "username": "wenchenyao" } } }
```
（注意：响应中**没有 password 字段**，属正常——已做防泄露处理）

> ⚠️ 登录接口是 `POST /api/user/login`，**不是** `/api/auth/login`。

### 第二步：HBuilderX 运行小程序

1. 打开 HBuilderX → 文件 → 导入 → 选择 `C:\Users\25026\Desktop\大创项目\羽毛球智能编排系统\badminton`
2. 菜单栏「运行 → 运行到小程序模拟器 → 微信开发者工具」
3. HBuilderX 会自动唤起微信开发者工具并加载编译产物（`unpackage/dist/dev/mp-weixin`）

> 本项目已启用**组件按需注入**（`lazyCodeLoading`），首次加载更快。
> 主包当前约 1.7M（上限 2M）。

### 第三步：微信开发者工具设置（首次必做）

1. 右上角「详情」→「本地设置」
2. 勾选 **「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」**（本机访问必须勾选）
3. **AppID**：本项目已配置真实 AppID（`wx3a06529b6cedaf91`），无需再选测试号
4. 若要**手机真机预览**，见 `真机预览配置指南.md`

## 2. 功能测试用例清单

按顺序执行，验证核心链路：

| # | 步骤 | 预期结果 |
|---|---|---|
| 1 | 打开小程序，未登录 | 自动跳转登录页 |
| 2 | 输入 wenchenyao / 123456 登录 | 提示登录成功 → 进入首页（底部有 tabBar：首页 / 赛事 / 我的） |
| 3 | 注册新账号（无角色，注册即可办赛） | 注册成功跳登录页，可用新账号登录 |
| 4 | 首页 | 显示 4 张概览卡（我的选手库 / 我创建的赛事 / 进行中 / 待录入场次） |
| 5 | 选手管理 → 添加选手 | 填学号/姓名（可设种子+顺位）→ 保存成功，列表出现 |
| 6 | 选手管理 → 批量导入 | 粘贴 JSON 数组导入多条；空态提示 |
| 7 | 比赛编排 → 输入标题、勾选选手 | 实时显示"规模 2^n 签位、轮空数"预览 |
| 8 | 点击生成对阵 | 提示"编排成功！规模 X 签，轮空 Y 人"→ 跳转对阵页 |
| 9 | 对阵查看 | 按轮次展示对阵，种子选手分列上下半区 |
| 10 | 点击某场「胜」按钮 | 保存成功，胜方高亮，自动晋级到下一轮 |
| 11 | 个人中心 → 退出登录 | 回到登录页 |
| 12 | 未登录访问选手管理（可直接改 URL 或登出后进入） | 提示无权限并返回 |

## 3. 常见问题排查

| 现象 | 原因 | 解决 |
|---|---|---|
| 登录提示"网络异常/请求失败" | 后端未启动 | 确认 8083 已启动；`netstat -ano \| findstr 8083` 应见 LISTENING |
| 登录提示"登录已过期" | 后端重启后内存 token 失效 | 重新登录即可 |
| 微信开发者工具白屏/编译错误 | AppID 未填或首次加载 | 用"测试号"；重新编译（HBuilderX 再次运行） |
| 请求报 401 | token 未携带或过期 | 重新登录；检查 request.js 是否被改坏 |
| HBuilderX 找不到微信开发者工具 | 工具路径未配置 | HBuilderX：工具 → 设置 → 运行配置 → 微信开发者工具路径填 `D:\微信web开发者工具` |
| 后端启动报数据库连接失败 | MySQL 未启动或密码不符 | 启动 MySQL；核对 `application.properties` 中 root 密码 |
| **后端启动端口是随机数（非 8083）** | 本机存在环境变量 `SERVER__PORT=0`，Spring 宽松绑定会把它映射为 `server.port` 且优先级高于配置文件 | 见下方「重要：本机环境变量」 |
| 接口返回中 `user_id` 等下划线字段为 null | 驼峰映射开关误开 | 已在 `application.properties` 关闭 `map-underscore-to-camel-case` |

### ⚠️ 重要：本机环境变量 `SERVER__PORT=0`

经实测，你的电脑存在环境变量 `SERVER__PORT=0`（可能在系统/用户环境变量中，或由某软件注入）。Spring Boot 的宽松绑定规则会把它当作 `server.port=0`（随机端口），**覆盖 `application.properties` 中的 8083**，导致后端启动在随机端口、前端连不上。

**三种解决方式（任选其一）**：
1. **IDEA 运行配置**：Run/Debug Configurations → `BadmintonApplication` → Program arguments 填入 `--server.port=8083`（命令行参数优先级最高）
2. **命令行启动时移除**：`env -u SERVER__PORT mvn spring-boot:run`（本次验证即用此方式）
3. 删除/修改系统环境变量 `SERVER__PORT`（如不再需要该软件）

## 5. 实测验证记录（2026-08-20）

以下接口已在本机实测通过（后端运行于 `http://localhost:8083`）：

| 用例 | 结果 |
|---|---|
| POST /api/user/login（wenchenyao/123456） | ✅ 返回 token + 完整用户信息（含 user_id） |
| GET /api/player/getAll（带 token） | ✅ code=0，空列表正常 |
| GET /api/player/getAll（无 token） | ✅ HTTP 401（拦截器生效） |
| POST /api/user/login（错误密码） | ✅ code=40001「用户名或密码错误」 |
| POST /api/user/register（新账号） | ✅ code=0 data=true |
| POST /api/user/register（重复用户名） | ✅ code=40002「用户名已存在」 |

数据库已就绪（7 张表 + 3 个演示账号），后端当前已在后台运行于 8083 端口，可直接进入 HBuilderX 步骤测试小程序。

## 4. 数据库说明

```sql
-- 如误删数据，可重建（幂等，可重复执行）：
-- C:\Users\25026\Desktop\大创项目\羽毛球智能编排系统\backend\src\main\resources\schema.sql
-- 恢复 3 个演示账号（wenchenyao / liuyangyang / huangyixuan，密码 123456）
```
