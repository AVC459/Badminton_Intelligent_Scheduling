/**
 * utils/request.js — 统一请求封装（全项目唯一网络出口）
 *
 * 能力：
 *  1. Promise 化 uni.request（修复原 dbHelper 中 await uni.request 无效的问题）
 *  2. 自动携带登录 token（Authorization: Bearer <token>）
 *  3. 统一响应解析：兼容新格式 { code, message, data } 与旧格式（直接返回业务对象）
 *  4. 统一错误提示与 401 登录态过期处理
 *  5. 请求超时控制
 *
 * 使用方式：
 *  import http from '@/utils/request.js'  （uni-app 配置了 @ 别名时）
 *  或 import { request } from '../../utils/request'
 */

// ==================== 环境配置 ====================
/**
 * 接口基址
 *
 * 重要：微信小程序正式环境**必须**使用已在公众平台「开发设置 → 服务器域名」
 * 备案过的 HTTPS 域名。以下配置仅用于本地联调，需在微信开发者工具中
 * 勾选「详情 → 本地设置 → 不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」。
 *
 * ⚠️ 关于手机调试（踩过的坑）：
 *   微信对真机有「服务器域名」强校验，而本地后端是 http://IP:端口 这种形式，
 *   永远不可能加进白名单，因此手机上会报
 *     request:fail url not in domain list（本项目的文案为「域名未放行」）
 *   解决办法：
 *     ✅ 用「真机调试」（工具 → 真机调试），而不是「预览」。
 *        真机调试的请求由开发者工具代理转发，上面那个「不校验合法域名」勾选项才生效；
 *        「预览」是独立运行、按正式规则校验域名，必然失败。
 *     ⭕ 若必须用「预览」，只能给小程序配一个已备案的 HTTPS 域名
 *        （可用内网穿透工具临时映射，再把域名填进公众平台的服务器域名白名单）。
 *
 * 联调地址选择：
 *   - 微信开发者工具（模拟器）：localhost / 127.0.0.1 均可
 *   - 真机调试：必须换成电脑的局域网 IP（如 http://172.33.206.182:8083/api），
 *     换 WiFi / 重新联网后 IP 会变，用根目录「切换接口环境.bat」一键刷新
 */
const ENV = {
  // 本机模拟器联调（微信开发者工具里跑，走本机回环）
  // ⚠️ 端口必须与 backend/src/main/resources/application.properties 的 server.port 保持一致
  dev: 'http://127.0.0.1:8083/api',
  // 真机联调：手机与电脑必须连同一个 WiFi，这里填电脑的局域网 IP
  // ⚠️ 换 WiFi / 路由器重启 / 重新联网后 IP 会变！变了就用根目录的
  //    「切换接口环境.bat」一键刷新（它会自动读当前 WLAN 的 IP 与后端端口）
  lan: 'http://172.33.206.182:8083/api',
  // 生产：上线前替换为备案过的 HTTPS 域名
  prod: 'https://api.your-domain.com/api'
};

/**
 * 运行环境自动识别
 *
 * 模拟器跑在电脑上，用 127.0.0.1 最稳；真机必须用局域网 IP（手机上的
 * 127.0.0.1 指向手机自己，永远连不上电脑）。
 *
 * 手动切换最容易忘——尤其换过 WiFi 之后。所以默认自动判断：
 *   微信开发者工具 → platform === 'devtools' → dev
 *   真机 iOS / Android                        → lan
 */
function detectEnv() {
  try {
    let platform = '';
    // 新基础库推荐 getDeviceInfo，旧版回退 getSystemInfoSync
    if (typeof uni.getDeviceInfo === 'function') {
      platform = (uni.getDeviceInfo() || {}).platform || '';
    }
    if (!platform && typeof uni.getSystemInfoSync === 'function') {
      platform = (uni.getSystemInfoSync() || {}).platform || '';
    }
    if (platform === 'devtools') return 'dev';
    return platform ? 'lan' : 'dev';
  } catch (e) {
    // 取不到设备信息时按模拟器处理（开发期最常见场景）
    return 'dev';
  }
}

// 'auto' 自动识别（推荐） | 'dev' 强制本机 | 'lan' 强制局域网 | 'prod' 生产
const CURRENT_ENV = 'auto';

const ENV_KEY = CURRENT_ENV === 'auto' ? detectEnv() : CURRENT_ENV;
const BASE_URL = ENV[ENV_KEY] || ENV.dev;

// ==================== Token 管理 ====================
const TOKEN_KEY = 'access_token';

export function getToken() {
  try {
    return uni.getStorageSync(TOKEN_KEY) || '';
  } catch (e) {
    return '';
  }
}

export function setToken(token) {
  try {
    uni.setStorageSync(TOKEN_KEY, token);
    return true;
  } catch (e) {
    console.error('保存 token 失败:', e);
    return false;
  }
}

export function clearToken() {
  try {
    uni.removeStorageSync(TOKEN_KEY);
  } catch (e) {
    /* ignore */
  }
}

// ==================== 错误处理 ====================
/**
 * 提示节流
 *
 * 页面里大量使用 Promise.all 并发拉取（首页一次并发 3~5 个接口），
 * 后端未启动时会**同时**触发多个 fail 回调，toast 叠着弹、互相覆盖，
 * 看起来就是「网络异常」无限循环。这里做两层去重：
 *  1. 同一条文案 2.5s 内只弹一次（覆盖并发风暴）
 *  2. 任意提示 800ms 内只弹一次（覆盖不同文案的瞬时轰炸）
 */
const TOAST_SAME_GAP = 2500;
const TOAST_ANY_GAP = 800;
let lastToastMsg = '';
let lastToastAt = 0;

function showError(message, options) {
  const msg = message || '请求失败';
  const now = Date.now();
  const sameMsg = msg === lastToastMsg && now - lastToastAt < TOAST_SAME_GAP;
  const tooSoon = now - lastToastAt < TOAST_ANY_GAP;
  if (sameMsg || tooSoon) return;

  lastToastMsg = msg;
  lastToastAt = now;
  uni.showToast({
    title: msg,
    icon: 'none',
    duration: 2200,
    ...(options || {})
  });
}

/**
 * 网络失败的可定位提示
 *
 * 「请求超时 / 网络异常」这类笼统文案在联调阶段几乎无法定位问题，
 * 这里根据 errMsg 细分常见成因，直接给出下一步动作。
 */
function describeNetworkFail(err) {
  const msg = (err && err.errMsg) || '';
  if (msg.indexOf('timeout') > -1) {
    return '请求超时：后端可能未启动或响应过慢';
  }
  if (msg.indexOf('domain') > -1 || msg.indexOf('not in domain list') > -1) {
    return '域名未放行：请勾选「不校验合法域名」或配置服务器域名';
  }
  if (msg.indexOf('Protocol') > -1 || msg.indexOf('protocol') > -1) {
    return '协议不符：正式环境需使用 HTTPS 域名';
  }
  if (msg.indexOf('abort') > -1) {
    return '请求被中断，请重试';
  }
  return '无法连接后端：请确认服务已启动（' + BASE_URL + '）';
}

/**
 * 登录态失效：清理本地登录信息并回到登录页
 */
function handleUnauthorized() {
  clearToken();
  try {
    uni.removeStorageSync('current_user');
  } catch (e) {
    /* ignore */
  }
  const pages = getCurrentPages();
  const current = pages[pages.length - 1];
  const route = current ? current.route : '';
  // 避免在登录/注册页重复跳转
  if (route !== 'pages/login/login' && route !== 'pages/register/register') {
    uni.reLaunch({ url: '/pages/login/login' });
  }
}

// ==================== 核心请求方法 ====================

/**
 * 剔除 data 中值为 undefined / null 的字段。
 *
 * 小程序把 GET 参数序列化进 query string 时，会把 undefined 原样写成
 * 「status=undefined」。后端若当成真实筛选值，就会查出 0 条 ——
 * 典型症状是「赛事明明创建了，列表却显示还没有赛事」。
 * 在唯一的网络出口统一清理，避免每个页面各自处理漏掉。
 */
function compactParams(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return data || {};
  const out = {};
  Object.keys(data).forEach((k) => {
    const v = data[k];
    if (v !== undefined && v !== null) out[k] = v;
  });
  return out;
}

/**
 * 判断 URL 是否含空的路径段（调用方漏传了 id）。
 *
 * 例：'/tournament/getById/' + '' 得到 '/tournament/getById/'，
 * 请求发出去后后端匹配不到任何接口，会把「参数缺失」误报成「服务繁忙」，
 * 极难定位。这里在发请求前就拦掉，并给出准确原因。
 */
function hasEmptyPathSegment(url) {
  const path = String(url).split('?')[0];
  return /\/$/.test(path) || /\/\/[^/]/.test(path) || /\/(undefined|null)(\/|$)/.test(path);
}

export function request(options) {
  if (hasEmptyPathSegment(options.url)) {
    const msg = '请求缺少必要参数（' + options.url + '）';
    console.error('[request] ' + msg);
    showError('页面参数缺失，请重新进入该页面');
    return Promise.reject({ code: -2, message: msg });
  }

  return new Promise((resolve, reject) => {
    const token = getToken();
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: compactParams(options.data),
      timeout: options.timeout || 15000,
      header: {
        'Content-Type': 'application/json',
        Authorization: token ? 'Bearer ' + token : '',
        ...(options.header || {})
      },
      success: (res) => {
        const { statusCode, data } = res;

        // HTTP 层错误
        if (statusCode === 401) {
          handleUnauthorized();
          showError('登录已过期，请重新登录');
          reject({ code: 401, message: 'unauthorized' });
          return;
        }
        if (statusCode < 200 || statusCode >= 300) {
          const msg = (data && data.message) || '请求失败(' + statusCode + ')';
          showError(msg);
          reject({ code: statusCode, message: msg });
          return;
        }

        // 业务层解析：新格式 { code: 0, message, data }；旧格式直接返回业务对象
        if (data && typeof data === 'object' && 'code' in data) {
          if (data.code === 0) {
            resolve(data.data);
          } else {
            showError(data.message || '操作失败');
            reject(data);
          }
          return;
        }
        resolve(data);
      },
      fail: (err) => {
        const msg = describeNetworkFail(err);
        showError(msg);
        // 联调期保留原始 errMsg，便于在控制台精确定位
        console.error('[request] 失败:', BASE_URL + options.url, err && err.errMsg);
        reject({ code: -1, message: msg, detail: err });
      }
    });
  });
}

// ==================== 文件上传 ====================
/**
 * 上传文件（用于 Excel 名单导入）
 *
 * uni.uploadFile 与 uni.request 是两套 API：不能直接复用 request()，
 * 但同样需要携带 token、统一解析 { code, message, data }、统一错误提示。
 */
export function uploadFile(options) {
  return new Promise((resolve, reject) => {
    const token = getToken();
    uni.uploadFile({
      url: BASE_URL + options.url,
      filePath: options.filePath,
      name: options.name || 'file',
      formData: options.formData || {},
      // Excel 导入会触发本地大模型识别，冷启动耗时可观，超时须大于后端 ollama.timeout-seconds
      timeout: options.timeout || 300000,
      header: {
        Authorization: token ? 'Bearer ' + token : '',
        ...(options.header || {})
      },
      success: (res) => {
        if (res.statusCode === 401) {
          handleUnauthorized();
          showError('登录已过期，请重新登录');
          reject({ code: 401, message: 'unauthorized' });
          return;
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          showError('上传失败(' + res.statusCode + ')');
          reject({ code: res.statusCode, message: 'upload failed' });
          return;
        }
        let data = res.data;
        if (typeof data === 'string') {
          try {
            data = JSON.parse(data);
          } catch (e) {
            showError('服务端返回格式异常');
            reject({ code: -1, message: 'invalid json' });
            return;
          }
        }
        if (data && typeof data === 'object' && 'code' in data) {
          if (data.code === 0) {
            resolve(data.data);
          } else {
            showError(data.message || '上传失败');
            reject(data);
          }
          return;
        }
        resolve(data);
      },
      fail: (err) => {
        const msg = describeNetworkFail(err);
        showError(msg);
        console.error('[upload] 失败:', BASE_URL + options.url, err && err.errMsg);
        reject({ code: -1, message: msg, detail: err });
      }
    });
  });
}

// ==================== 便捷方法 ====================
export const http = {
  get: (url, params, options) => request({ url, method: 'GET', data: params, ...options }),
  post: (url, data, options) => request({ url, method: 'POST', data, ...options }),
  put: (url, data, options) => request({ url, method: 'PUT', data, ...options }),
  del: (url, data, options) => request({ url, method: 'DELETE', data, ...options })
};

export default http;
