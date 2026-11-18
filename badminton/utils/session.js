/**
 * utils/session.js — 登录态与本地会话存储
 *
 * 与网络层解耦：只负责 token / 当前用户的读写，便于任意模块引用而不产生循环依赖。
 *
 * v3.0：系统取消管理员 / 裁判 / 选手的角色分类，只有一种身份「比赛发起者」，
 * 因此这里不再提供任何角色判断函数（isAdmin / isReferee / isStaff 已移除）。
 */
import { clearToken } from './request';

const USER_KEY = 'current_user';

/** 系统唯一身份 */
export const ROLE_LABEL = '比赛发起者';

export function setCurrentUser(user) {
  try {
    uni.setStorageSync(USER_KEY, JSON.stringify(user));
    return true;
  } catch (error) {
    console.error('存储当前用户失败:', error);
    return false;
  }
}

export function getCurrentUser() {
  try {
    const raw = uni.getStorageSync(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (error) {
    console.error('获取当前用户失败:', error);
    return null;
  }
}

export function clearCurrentUser() {
  clearToken();
  try {
    uni.removeStorageSync(USER_KEY);
    return true;
  } catch (error) {
    console.error('清除当前用户失败:', error);
    return false;
  }
}

export function isLoggedIn() {
  return !!getCurrentUser();
}

/** 展示名：优先姓名，其次登录名 */
export function getDisplayName() {
  const u = getCurrentUser();
  if (!u) return '未登录';
  return u.real_name || u.username || '用户';
}

export function resetAllStorage() {
  clearToken();
  try {
    uni.clearStorageSync();
    return true;
  } catch (error) {
    console.error('重置本地存储失败:', error);
    return false;
  }
}

/** 未登录则跳转登录页，返回是否已登录 */
export function requireLogin() {
  if (isLoggedIn()) return true;
  uni.reLaunch({ url: '/pages/login/login' });
  return false;
}

/**
 * 页面必填参数守卫
 *
 * 形如 /pages/xxx?id=yyy 的页面，一旦 id 丢失就会发出「没有路径参数」的请求
 * （例如 /api/entry/list/），后端匹配不到任何接口，前端却只会看到
 * 「服务繁忙，请稍后再试」——把「参数缺失」伪装成「后端故障」，极难定位。
 *
 * 这里统一在页面入口拦掉，并退回上一页，避免用户卡在空白页上。
 *
 * @param {*} value 待校验的参数值
 * @param {string} label 参数中文名，用于提示文案
 * @returns {boolean} 参数是否有效
 */
export function requireParam(value, label) {
  const s = value === undefined || value === null ? '' : String(value).trim();
  if (s && s !== 'undefined' && s !== 'null') return true;
  uni.showToast({ title: '缺少' + (label || '页面参数') + '，请从赛事列表进入', icon: 'none' });
  setTimeout(() => {
    const pages = getCurrentPages();
    if (pages.length > 1) uni.navigateBack();
    else uni.reLaunch({ url: '/pages/tournament/list' });
  }, 900);
  return false;
}
