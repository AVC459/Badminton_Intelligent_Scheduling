<template>
  <view class="container">
    <!-- 顶部问候 -->
    <view class="hello">
      <view class="grow">
        <text class="hello__text">{{ greeting }}，{{ displayName }}</text>
        <text class="hello__sub">比赛发起者 · 无需鉴权，注册即可办赛</text>
      </view>
      <view class="hello__avatar" @click="goTab('/pages/my/info')">
        <wb-icon name="user" :size="18" color="#ffffff" />
      </view>
    </view>

    <!-- 概览数据 -->
    <view class="kpis">
      <wb-stat
        class="kpis__item"
        icon="users"
        :value="overview.player_count"
        label="我的选手库"
        :hint="overview.team_count + ' 个单位'"
        color="#1e40af"
        soft-color="#e8effc"
        clickable
        @click="go('/pages/player/list')"
      />
      <wb-stat
        class="kpis__item"
        icon="trophy"
        :value="overview.tournament_count"
        label="我创建的赛事"
        :hint="overview.finished_count + ' 场已结束'"
        color="#d97706"
        soft-color="#fdf3e3"
        clickable
        @click="go('/pages/tournament/list')"
      />
      <wb-stat
        class="kpis__item"
        icon="play"
        :value="overview.ongoing_count"
        label="进行中"
        hint="已编排待开赛"
        color="#15803d"
        soft-color="#e6f4ea"
        clickable
        @click="go('/pages/tournament/list')"
      />
      <wb-stat
        class="kpis__item"
        icon="score"
        :value="overview.pending_score"
        label="待录入场次"
        hint="可开赛未记分"
        color="#dc2626"
        soft-color="#fdeaea"
        clickable
        @click="openScoreEntry"
      />
    </view>

    <!-- 最近赛事 -->
    <wb-section
      class="block"
      title="最近赛事"
      icon="list"
      :badge="recentTournaments.length"
      :action-text="recentTournaments.length ? '全部' : ''"
      @action="go('/pages/tournament/list')"
    >
      <view v-if="loading" class="skeleton-list">
        <view v-for="i in 3" :key="i" class="skeleton skeleton--row" />
      </view>

      <wb-empty
        v-else-if="!recentTournaments.length"
        icon="trophy"
        title="还没有赛事"
        desc="点击底部「创建赛事」，选择赛制并导入选手后即可执行智能抽签编排"
        action-text="创建赛事"
        @action="goTab('/pages/tournament/create')"
      />

      <view v-else>
        <view
          v-for="t in recentTournaments"
          :key="t.tournament_id"
          class="t-item"
          @click="go('/pages/tournament/detail?id=' + t.tournament_id)"
        >
          <view class="t-item__badge">
            <wb-icon name="trophy" :size="16" color="#1e40af" />
          </view>
          <view class="grow">
            <text class="t-item__title t-ellipsis">{{ t.title }}</text>
            <view class="t-item__meta">
              <text class="t-weak">{{ formatShort(t.format) }}</text>
              <text class="t-weak">·</text>
              <text class="t-weak">{{ eventTypeLabel(t.event_type) }}</text>
              <text class="t-weak">·</text>
              <text class="t-weak">{{ t.actual_participants || 0 }} 人</text>
            </view>
          </view>
          <wb-tag
            :text="tournamentStatusLabel(t.status)"
            :tone="tournamentStatusTone(t.status)"
            size="sm"
          />
        </view>
      </view>
    </wb-section>
  </view>
</template>

<script>
import { getOverview, getTournaments } from '../../utils/api';
import { getCurrentUser, requireLogin, ROLE_LABEL } from '../../utils/session';
import {
  formatShort,
  eventTypeLabel,
  tournamentStatusLabel,
  tournamentStatusTone
} from '../../utils/dict';

export default {
  data() {
    return {
      user: null,
      roleLabel: ROLE_LABEL,
      overview: {
        player_count: 0,
        team_count: 0,
        tournament_count: 0,
        ready_count: 0,
        ongoing_count: 0,
        finished_count: 0,
        pending_score: 0
      },
      myTournaments: [],
      recentTournaments: [],
      loading: true
    };
  },
  computed: {
    displayName() {
      if (!this.user) return '访客';
      return this.user.real_name || this.user.username || '用户';
    },
    greeting() {
      const h = new Date().getHours();
      if (h < 6) return '凌晨好';
      if (h < 11) return '早上好';
      if (h < 13) return '中午好';
      if (h < 18) return '下午好';
      return '晚上好';
    }
  },
  onShow() {
    if (!requireLogin()) return;
    this.user = getCurrentUser();
    this.load();
  },
  methods: {
    formatShort,
    eventTypeLabel,
    tournamentStatusLabel,
    tournamentStatusTone,
    async load() {
      this.loading = true;
      try {
        const [ov, ts] = await Promise.all([getOverview(), getTournaments()]);
        if (ov) this.overview = Object.assign(this.overview, ov);
        const all = Array.isArray(ts) ? ts : [];
        // 未结束的排前面，其次按创建时间倒序
        all.sort((a, b) => {
          const rank = (s) => (s === 'FINISHED' ? 1 : 0);
          const d = rank(a.status) - rank(b.status);
          if (d !== 0) return d;
          return String(b.create_time || '').localeCompare(String(a.create_time || ''));
        });
        this.myTournaments = all;
        this.recentTournaments = all.slice(0, 3);
      } catch (e) {
        console.error('加载首页数据失败', e);
      } finally {
        this.loading = false;
      }
    },
    /* ---------- 录入比分 ---------- */
    openScoreEntry() {
      const list = this.myTournaments.filter(
        (t) => t.status === 'DRAWN' || t.status === 'ONGOING' || t.status === 'FINISHED'
      );
      if (!list.length) {
        this.promptCreate('还没有生成赛程的赛事，完成抽签编排后即可录入比分');
        return;
      }
      this.pick(list, '选择要记分的赛事', (t) => {
        uni.navigateTo({ url: '/pages/match/list?id=' + t.tournament_id });
      });
    },
    pick(list, title, cb) {
      if (!list.length) return;
      if (list.length === 1) {
        cb(list[0]);
        return;
      }
      // 微信 showActionSheet 最多 6 项。超出时必须显式给出入口，
      // 否则第 7 场之后的赛事会被静默藏掉，用户根本不知道还有更多。
      const MAX = 6;
      const overflow = list.length > MAX;
      const shown = overflow ? list.slice(0, MAX - 1) : list;
      const itemList = shown.map((t) => t.title);
      if (overflow) itemList.push('查看全部 ' + list.length + ' 场赛事…');
      uni.showActionSheet({
        alertText: title,
        itemList,
        success: (res) => {
          if (overflow && res.tapIndex === itemList.length - 1) {
            uni.navigateTo({ url: '/pages/tournament/list' });
            return;
          }
          const t = shown[res.tapIndex];
          if (t) cb(t);
        },
        fail: () => {
          /* 用户取消 */
        }
      });
    },
    promptCreate(msg) {
      uni.showModal({
        title: '暂无可操作赛事',
        content: msg,
        confirmText: '创建赛事',
        success: (r) => {
          if (r.confirm) this.goTab('/pages/tournament/create');
        }
      });
    },
    go(url) {
      uni.navigateTo({ url });
    },
    goTab(url) {
      uni.switchTab({ url });
    }
  },
  onShareAppMessage() {
    return { title: '羽毛球比赛智能编排系统', path: '/pages/index/index' };
  }
};
</script>

<style scoped>
.hello {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 4px 2px 16px;
}

.hello__text {
  display: block;
  font-size: 19px;
  font-weight: 700;
  color: var(--c-text);
}

.hello__sub {
  display: block;
  font-size: 12px;
  color: var(--c-text-weak);
  margin-top: 3px;
}

.hello__avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background-color: var(--c-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kpis {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.kpis__item {
  width: calc(50% - 4px);
}

.block {
  margin-bottom: var(--sp-3);
}

/* ---------- 最近赛事 ---------- */
.t-item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px 0;
  border-bottom: 1px solid var(--c-muted);
}

.t-item:last-child {
  border-bottom: none;
}

.t-item__badge {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background-color: var(--c-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.t-item__title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.t-item__meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 3px;
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skeleton--row {
  height: 52px;
}
</style>
