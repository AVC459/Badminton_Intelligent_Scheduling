<template>
  <view class="container">
    <!-- 用户名 -->
    <view class="profile">
      <view class="profile__avatar">
        <text class="profile__initial">{{ initial }}</text>
      </view>
      <view class="grow">
        <text class="profile__name t-ellipsis">{{ displayName }}</text>
        <view class="profile__tags">
          <wb-tag :text="roleLabel" tone="info" size="sm" icon="shield" />
        </view>
      </view>
    </view>

    <!-- 我的权限 -->
    <wb-section
      class="block"
      title="我的权限"
      icon="shield"
      desc="比赛发起者拥有赛事全流程的操作权限，无需额外授权"
    >
      <view v-for="p in permissions" :key="p" class="cap">
        <view class="cap__icon">
          <wb-icon name="check" :size="12" color="#15803d" :stroke-width="2.6" />
        </view>
        <text class="cap__text">{{ p }}</text>
      </view>
      <view v-if="!permissions.length" class="t-weak">权限信息加载中…</view>
    </wb-section>

    <!-- 选手库 / 我创建的赛事 -->
    <view class="links">
      <view class="link" @click="go('/pages/player/list')">
        <view class="link__icon" style="background-color: #e8effc">
          <wb-icon name="users" :size="18" color="#1e40af" />
        </view>
        <view class="grow">
          <text class="link__title">选手库</text>
          <text class="link__desc">{{ profile.player_count }} 位选手 · 支持 Excel 智能导入</text>
        </view>
        <wb-icon name="chevronRight" :size="15" color="#a9b6cc" />
      </view>

      <view class="link" @click="go('/pages/tournament/list')">
        <view class="link__icon" style="background-color: #fdf3e3">
          <wb-icon name="trophy" :size="18" color="#d97706" />
        </view>
        <view class="grow">
          <text class="link__title">我创建的赛事</text>
          <text class="link__desc">
            {{ profile.tournament_count }} 场赛事 · {{ profile.finished_count }} 场已结束
          </text>
        </view>
        <wb-icon name="chevronRight" :size="15" color="#a9b6cc" />
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="logout" @click="confirmLogout">
      <wb-icon name="logout" :size="16" color="#dc2626" />
      <text class="logout__text">退出登录</text>
    </view>

    <view class="foot">羽毛球比赛智能编排系统 · v{{ version }}</view>
  </view>
</template>

<script>
import { getMyProfile, logout } from '../../utils/api';
import {
  getCurrentUser,
  setCurrentUser,
  isLoggedIn,
  clearCurrentUser,
  ROLE_LABEL
} from '../../utils/session';

export default {
  data() {
    return {
      version: '3.0.0',
      roleLabel: ROLE_LABEL,
      user: {},
      profile: {
        player_count: 0,
        tournament_count: 0,
        finished_count: 0
      },
      permissions: [],
      loading: false
    };
  },
  computed: {
    /** 本页只展示用户名（不展示真实姓名、手机号等账号信息） */
    displayName() {
      return this.user.username || '未登录';
    },
    initial() {
      const n = this.displayName;
      return n ? String(n).slice(0, 1) : '?';
    }
  },
  onShow() {
    if (!isLoggedIn()) {
      uni.reLaunch({ url: '/pages/login/login' });
      return;
    }
    this.user = getCurrentUser() || {};
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const p = await getMyProfile();
        if (!p) return;
        if (p.user) {
          setCurrentUser(p.user);
          this.user = p.user;
        }
        this.permissions = Array.isArray(p.permissions) ? p.permissions : [];
        this.profile = {
          player_count: p.player_count || 0,
          tournament_count: p.tournament_count || 0,
          finished_count: p.finished_count || 0
        };
      } catch (e) {
        /* 已由请求层提示 */
      } finally {
        this.loading = false;
      }
    },
    go(url) {
      uni.navigateTo({ url });
    },
    confirmLogout() {
      uni.showModal({
        title: '退出登录',
        content: '确认退出当前账号？',
        confirmText: '退出',
        confirmColor: '#dc2626',
        success: async (r) => {
          if (!r.confirm) return;
          try {
            await logout();
          } catch (e) {
            /* 服务端注销失败也要保证本地登出 */
          }
          clearCurrentUser();
          uni.reLaunch({ url: '/pages/login/login' });
        }
      });
    }
  },
  onShareAppMessage() {
    return { title: '羽毛球比赛智能编排系统', path: '/pages/index/index' };
  }
};
</script>

<style scoped>
/* ---------- 用户名 ---------- */
.profile {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  background: linear-gradient(150deg, #1e40af, #2563eb);
  border-radius: var(--r-md);
  padding: 18px 16px;
  box-shadow: var(--sh-2);
  margin-bottom: var(--sp-3);
}

.profile__avatar {
  width: 54px;
  height: 54px;
  border-radius: 16px;
  background-color: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.32);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.profile__initial {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
}

.profile__name {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.profile__tags {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.block {
  margin-bottom: var(--sp-3);
}

/* ---------- 权限清单 ---------- */
.cap {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
  padding: 9px 0;
  border-bottom: 1px solid var(--c-muted);
}

.cap:last-child {
  border-bottom: none;
}

.cap__icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background-color: var(--c-success-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;
}

.cap__text {
  flex: 1;
  font-size: 13px;
  color: var(--c-text);
  line-height: 1.6;
}

/* ---------- 入口 ---------- */
.links {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.link {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 14px;
  border-radius: var(--r-md);
  background-color: var(--c-surface);
  box-shadow: var(--sh-1);
}

.link__icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.link__title {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.link__desc {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 3px;
}

/* ---------- 退出 ---------- */
.logout {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 48px;
  border-radius: var(--r-md);
  background-color: var(--c-surface);
  border: 1px solid #f3c9c9;
  box-shadow: var(--sh-1);
  margin-bottom: var(--sp-4);
}

.logout__text {
  font-size: 15px;
  font-weight: 600;
  color: var(--c-danger);
}

.foot {
  text-align: center;
  font-size: 11px;
  color: var(--c-text-weak);
  padding-bottom: var(--sp-4);
}
</style>
