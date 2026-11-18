<script>
import { getCurrentUser } from './utils/session';

export default {
  onLaunch() {
    console.log('羽毛球智能编排小程序启动');
    // 恢复登录态（同步读取本地存储）
    this.globalData.currentUser = getCurrentUser();
  },
  onShow() {
    // 回到前台时刷新登录态
    this.globalData.currentUser = getCurrentUser();
  },
  onHide() {},
  // 说明：小程序的 onShareAppMessage 只在「页面」上生效，写在 App.vue 里不起任何作用
  // （之前这里有一段"全局默认分享"，实际是死代码，右上角转发依然置灰）。
  // 分享钩子已改为按页面定义：首页、我的、赛事列表/详情、参赛名单、抽签、赛程、对阵、报告。
  globalData: {
    currentUser: null,
    version: '2.0.0'
  }
};
</script>

<style>
/* ==========================================================================
   设计系统 · Design Tokens
   风格：Data-Dense Dashboard（数据密集看板）
   取色：蓝色数据主色 + 琥珀强调色，状态色承载语义
   ========================================================================== */
page {
  /* ---- 主色阶 ---- */
  --c-primary: #1e40af;
  --c-primary-strong: #1b3a9c;
  --c-primary-soft: #e8effc;
  --c-on-primary: #ffffff;
  --c-secondary: #3b82f6;
  --c-accent: #d97706;
  --c-accent-soft: #fdf3e3;

  /* ---- 表面与文字 ---- */
  --c-bg: #f1f5fb;
  --c-surface: #ffffff;
  --c-muted: #e9eef6;
  --c-border: #dbeafe;
  --c-text: #16233f;
  --c-text-sub: #5b6b88;
  --c-text-weak: #8b98b0;

  /* ---- 语义状态色 ---- */
  --c-success: #15803d;
  --c-success-soft: #e6f4ea;
  --c-warning: #b45309;
  --c-warning-soft: #fdf1dd;
  --c-danger: #dc2626;
  --c-danger-soft: #fdeaea;
  --c-info: #1e40af;
  --c-info-soft: #e8effc;
  --c-neutral: #5b6b88;
  --c-neutral-soft: #eef1f6;

  /* ---- 圆角 ---- */
  --r-xs: 6px;
  --r-sm: 10px;
  --r-md: 14px;
  --r-lg: 20px;

  /* ---- 阴影（统一高度层级，避免随机阴影值） ---- */
  --sh-1: 0 1px 2px rgba(22, 35, 63, 0.06);
  --sh-2: 0 4px 14px rgba(22, 35, 63, 0.07);
  --sh-3: 0 10px 28px rgba(22, 35, 63, 0.12);

  /* ---- 间距节奏（4/8 基准） ---- */
  --sp-1: 4px;
  --sp-2: 8px;
  --sp-3: 12px;
  --sp-4: 16px;
  --sp-5: 24px;
  --sp-6: 32px;

  background-color: var(--c-bg);
  font-size: 14px;
  line-height: 1.5;
  color: var(--c-text);
  font-family: -apple-system, BlinkMacSystemFont, 'Fira Sans', 'PingFang SC',
    'Helvetica Neue', Helvetica, 'Microsoft YaHei', sans-serif;
}

/* ==========================================================================
   布局
   ========================================================================== */
.container {
  padding: var(--sp-4);
  min-height: 100vh;
  box-sizing: border-box;
}

/* 底部固定操作条时给内容预留空间，避免被遮挡 */
.container--with-footer {
  padding-bottom: 96px;
}

.row {
  display: flex;
  align-items: center;
}

.row--between {
  justify-content: space-between;
}

.row--gap {
  gap: var(--sp-2);
}

.grow {
  flex: 1;
  min-width: 0;
}

/* ==========================================================================
   卡片
   ========================================================================== */
.card {
  background-color: var(--c-surface);
  border-radius: var(--r-md);
  padding: var(--sp-4);
  box-shadow: var(--sh-1);
  border: 1px solid var(--c-border);
  box-sizing: border-box;
}

.card + .card {
  margin-top: var(--sp-3);
}

/* ==========================================================================
   文字
   ========================================================================== */
.t-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--c-text);
}

.t-sub {
  font-size: 13px;
  color: var(--c-text-sub);
}

.t-weak {
  font-size: 12px;
  color: var(--c-text-weak);
}

.t-num {
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum';
  font-weight: 600;
  letter-spacing: 0.3px;
}

.t-ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ==========================================================================
   按钮 —— 触控目标 ≥44px，按压态不改变布局
   ========================================================================== */
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 var(--sp-4);
  border-radius: var(--r-sm);
  font-size: 15px;
  font-weight: 600;
  border: 1px solid transparent;
  box-sizing: border-box;
  transition: opacity 0.18s ease-out, background-color 0.18s ease-out;
}

.btn::after {
  border: none;
}

.btn--primary {
  background-color: var(--c-primary);
  color: var(--c-on-primary);
}

.btn--accent {
  background-color: var(--c-accent);
  color: #fff;
}

.btn--ghost {
  background-color: var(--c-surface);
  color: var(--c-primary);
  border-color: var(--c-border);
}

.btn--danger {
  background-color: var(--c-danger);
  color: #fff;
}

.btn--danger-ghost {
  background-color: var(--c-surface);
  color: var(--c-danger);
  border-color: #f3c9c9;
}

.btn--block {
  width: 100%;
}

.btn--sm {
  min-height: 36px;
  font-size: 13px;
  padding: 0 var(--sp-3);
}

.btn--pressed {
  opacity: 0.72;
}

.btn[disabled],
.btn--disabled {
  opacity: 0.45;
  pointer-events: none;
}

/* ==========================================================================
   底部固定操作条
   ========================================================================== */
.footer-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: var(--c-surface);
  border-top: 1px solid var(--c-border);
  padding: var(--sp-3) var(--sp-4);
  padding-bottom: calc(var(--sp-3) + env(safe-area-inset-bottom));
  display: flex;
  gap: var(--sp-2);
  z-index: 100;
  box-sizing: border-box;
}

/* ==========================================================================
   表单
   ========================================================================== */
.field {
  margin-bottom: var(--sp-4);
}

.field__label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-sub);
  margin-bottom: var(--sp-2);
}

.field__label--required::after {
  content: '*';
  color: var(--c-danger);
  margin-left: 3px;
}

.field__input {
  width: 100%;
  min-height: 44px;
  padding: 0 var(--sp-3);
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  font-size: 15px;
  color: var(--c-text);
  box-sizing: border-box;
}

.field__textarea {
  min-height: 84px;
  padding: var(--sp-3);
  line-height: 1.5;
}

.field__helper {
  font-size: 12px;
  color: var(--c-text-weak);
  margin-top: var(--sp-1);
}

.field__error {
  font-size: 12px;
  color: var(--c-danger);
  margin-top: var(--sp-1);
}

/* 分段选择器 */
.segment {
  display: flex;
  background-color: var(--c-muted);
  border-radius: var(--r-sm);
  padding: 3px;
  gap: 3px;
}

.segment__item {
  flex: 1;
  text-align: center;
  min-height: 36px;
  line-height: 36px;
  font-size: 13px;
  color: var(--c-text-sub);
  border-radius: 7px;
  transition: background-color 0.18s ease-out, color 0.18s ease-out;
}

.segment__item--active {
  background-color: var(--c-surface);
  color: var(--c-primary);
  font-weight: 600;
  box-shadow: var(--sh-1);
}

/* 步进器 / 数字输入 */
.stepper {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.stepper__btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-sm);
  background-color: var(--c-muted);
  color: var(--c-primary);
  font-size: 20px;
  font-weight: 600;
}

.stepper__value {
  flex: 1;
  text-align: center;
  font-size: 17px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

/* 开关 */
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 52px;
  gap: var(--sp-3);
}

.switch-row__text {
  flex: 1;
  min-width: 0;
}

.switch-row__title {
  font-size: 14px;
  color: var(--c-text);
  font-weight: 500;
}

.switch-row__desc {
  font-size: 12px;
  color: var(--c-text-weak);
  margin-top: 2px;
}

/* ==========================================================================
   列表
   ========================================================================== */
.list-item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) 0;
  border-bottom: 1px solid var(--c-muted);
}

.list-item:last-child {
  border-bottom: none;
}

.divider {
  height: 1px;
  background-color: var(--c-muted);
  margin: var(--sp-3) 0;
}

/* ==========================================================================
   空状态
   ========================================================================== */
.empty-state {
  text-align: center;
  padding: 56px var(--sp-4);
  color: var(--c-text-weak);
  font-size: 13px;
}

/* ==========================================================================
   骨架屏（加载 >300ms 时的占位，避免空白闪烁）
   ========================================================================== */
.skeleton {
  background: linear-gradient(90deg, #eef1f6 25%, #f6f8fb 37%, #eef1f6 63%);
  background-size: 400% 100%;
  animation: skeleton-flow 1.2s ease-in-out infinite;
  border-radius: var(--r-sm);
}

@keyframes skeleton-flow {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

/* 尊重「减少动态效果」偏好 */
@media (prefers-reduced-motion: reduce) {
  .skeleton {
    animation: none;
  }
}

/* ==========================================================================
   数据表格（积分榜 / 排名）
   ========================================================================== */
.table {
  width: 100%;
}

.table__head,
.table__row {
  display: flex;
  align-items: center;
  padding: var(--sp-2) var(--sp-1);
  font-size: 13px;
}

.table__head {
  color: var(--c-text-weak);
  font-size: 12px;
  border-bottom: 1px solid var(--c-border);
}

.table__row {
  border-bottom: 1px solid var(--c-muted);
  color: var(--c-text);
}

.table__row:last-child {
  border-bottom: none;
}

.table__row--advanced {
  background-color: var(--c-primary-soft);
}

.table__cell {
  flex: 1;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.table__cell--name {
  flex: 2.4;
  text-align: left;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.table__cell--rank {
  flex: 0.7;
  font-weight: 600;
}
</style>
