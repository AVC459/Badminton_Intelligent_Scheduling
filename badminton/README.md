# 小程序前端（uni-app + Vue 2）

用 HBuilderX 打开这个目录，运行到微信开发者工具即可。完整说明见仓库根目录的 [README](../README.md)。

## 依赖的后端

接口默认走 `http://127.0.0.1:8083/api`，配置在 `utils/request.js` 顶部：

```js
const ENV = {
  dev:  'http://127.0.0.1:8083/api',
  lan:  'http://<电脑局域网IP>:8083/api',
  prod: 'https://api.your-domain.com/api'
};
const CURRENT_ENV = 'dev';
```

真机调试要切到 `lan`，并且用微信开发者工具的**真机调试**，不要用预览：预览按正式规则校验服务器域名，本地 http 地址过不去。仓库根目录的 `scripts/switch-api-env.bat` 会自动把当前 WLAN 的 IP 写进 `lan`。

## 目录

```
pages/        16 个页面（登录、总览、赛事、名单、抽签、记分、签表、报告、选手库、我的）
components/   5 个通用组件（wb-icon / wb-section / wb-stat / wb-tag / wb-empty）
utils/        request.js 是唯一网络出口，页面不直接调 uni.request
static/       tabBar 图标
```

## 几个约定

- 图标统一用 `<wb-icon name="..." />`，不要用 emoji
- 接口返回值是 `{ code, message, data }`，`code !== 0` 时 request.js 会统一提示
- 字段是下划线风格，例外是排名对象 `Standing` 用小驼峰
- 不要改 `unpackage/` 里的文件，那是编译产物
