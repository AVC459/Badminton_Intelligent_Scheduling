<template>
  <view class="container container--with-footer">
    <!-- 搜索 + 状态筛选 -->
    <view class="toolbar">
      <view class="search">
        <wb-icon name="search" :size="15" color="#8b98b0" />
        <input
          class="search__input"
          v-model="keyword"
          placeholder="搜索赛事名称"
          placeholder-class="ph"
          confirm-type="search"
          @confirm="load"
        />
        <view v-if="keyword" class="search__clear" @click="clearKeyword">
          <wb-icon name="close" :size="13" color="#8b98b0" />
        </view>
      </view>
      <view class="segment">
        <view
          v-for="s in statusTabs"
          :key="s.value"
          class="segment__item"
          :class="{ 'segment__item--active': status === s.value }"
          @click="switchStatus(s.value)"
        >
          {{ s.label }}
        </view>
      </view>
    </view>

    <!-- 骨架 -->
    <view v-if="loading" class="skeleton-list">
      <view v-for="i in 3" :key="i" class="skeleton skeleton--card" />
    </view>

    <!-- 空状态 -->
    <wb-empty
      v-else-if="!list.length"
      icon="trophy"
      :title="keyword || status ? '没有匹配的赛事' : '还没有赛事'"
      :desc="keyword || status ? '试试更换关键字或筛选条件' : '点击底部「创建赛事」，导入选手后即可执行智能抽签编排'"
      :action-text="keyword || status ? '' : '创建赛事'"
      @action="goCreate"
    />

    <!-- 列表 -->
    <view v-else>
      <view v-for="t in list" :key="t.tournament_id" class="tcard" @click="openDetail(t)">
        <view class="tcard__head">
          <text class="tcard__title t-ellipsis">{{ t.title }}</text>
          <wb-tag :text="tournamentStatusLabel(t.status)" :tone="tournamentStatusTone(t.status)" />
        </view>

        <view class="tcard__tags">
          <wb-tag :text="formatShort(t.format)" tone="info" size="sm" />
          <wb-tag :text="eventTypeLabel(t.event_type)" tone="neutral" size="sm" />
          <wb-tag v-if="t.avoid_same_team" text="同单位规避" tone="accent" size="sm" icon="shield" />
          <wb-tag v-if="t.best_of" :text="t.best_of + ' 局 ' + t.points_per_game + ' 分'" tone="neutral" size="sm" />
        </view>

        <view class="tcard__metrics">
          <view class="metric">
            <text class="metric__value t-num">{{ t.actual_participants || 0 }}</text>
            <text class="metric__label">参赛</text>
          </view>
          <view class="metric__sep" />
          <view class="metric">
            <text class="metric__value t-num">{{ t.bracket_size || '—' }}</text>
            <text class="metric__label">签位</text>
          </view>
          <view class="metric__sep" />
          <view class="metric">
            <text class="metric__value t-num">{{ t.bye_count || 0 }}</text>
            <text class="metric__label">轮空</text>
          </view>
          <view class="metric__sep" />
          <view class="metric">
            <text class="metric__value t-num">{{ t.seed_count || 0 }}</text>
            <text class="metric__label">种子</text>
          </view>
        </view>

        <!-- 主操作：按状态给出主 CTA -->
        <view class="tcard__actions">
          <view
            v-if="t.status === 'READY' || t.status === 'DRAFT'"
            class="chip chip--primary"
            @click.stop="goEntries(t)"
          >
            <wb-icon name="users" :size="13" color="#ffffff" />
            <text class="chip__text">参赛名单</text>
          </view>
          <view
            v-if="t.status === 'READY' || t.status === 'DRAFT'"
            class="chip chip--accent"
            @click.stop="goDraw(t)"
          >
            <wb-icon name="shuffle" :size="13" color="#ffffff" />
            <text class="chip__text">抽签编排</text>
          </view>
          <view v-if="t.status === 'DRAWN' || t.status === 'ONGOING'" class="chip chip--primary" @click.stop="goMatches(t)">
            <wb-icon name="score" :size="13" color="#ffffff" />
            <text class="chip__text">赛程记分</text>
          </view>
          <view
            v-if="t.status === 'DRAWN' || t.status === 'ONGOING' || t.status === 'FINISHED'"
            class="chip chip--ghost"
            @click.stop="goBracket(t)"
          >
            <wb-icon name="grid" :size="13" color="#1e40af" />
            <text class="chip__text chip__text--ghost">对阵</text>
          </view>
          <view v-if="t.status === 'FINISHED'" class="chip chip--ghost" @click.stop="goReport(t)">
            <wb-icon name="fileText" :size="13" color="#1e40af" />
            <text class="chip__text chip__text--ghost">报告</text>
          </view>
        </view>
      </view>
    </view>

    <view class="footer-bar">
      <view class="btn btn--primary btn--block" @click="goCreate">
        <wb-icon name="plus" :size="16" color="#ffffff" :stroke-width="2.4" />
        <text class="btn__text btn__text--gap">创建赛事</text>
      </view>
    </view>
  </view>
</template>

<script>
import { getTournaments } from '../../utils/api';
import { requireLogin } from '../../utils/session';
import {
  formatShort,
  eventTypeLabel,
  tournamentStatusLabel,
  tournamentStatusTone,
  TOURNAMENT_STATUS_OPTIONS
} from '../../utils/dict';

export default {
  data() {
    return {
      keyword: '',
      status: '',
      statusTabs: TOURNAMENT_STATUS_OPTIONS,
      list: [],
      loading: true
    };
  },
  onShow() {
    if (!requireLogin()) return;
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
        // 只传真正有值的筛选条件，避免把 undefined 序列化成 "undefined" 发给后端
        const params = {};
        if (this.status) params.status = this.status;
        const kw = (this.keyword || '').trim();
        if (kw) params.keyword = kw;
        const res = await getTournaments(params);
        this.list = Array.isArray(res) ? res : [];
      } catch (e) {
        this.list = [];
      } finally {
        this.loading = false;
      }
    },
    switchStatus(v) {
      if (this.status === v) return;
      this.status = v;
      this.load();
    },
    clearKeyword() {
      this.keyword = '';
      this.load();
    },
    openDetail(t) {
      uni.navigateTo({ url: '/pages/tournament/detail?id=' + t.tournament_id });
    },
    goCreate() {
      uni.switchTab({ url: '/pages/tournament/create' });
    },
    goEntries(t) {
      uni.navigateTo({ url: '/pages/entry/list?id=' + t.tournament_id });
    },
    goDraw(t) {
      uni.navigateTo({ url: '/pages/draw/config?id=' + t.tournament_id });
    },
    goMatches(t) {
      uni.navigateTo({ url: '/pages/match/list?id=' + t.tournament_id });
    },
    goBracket(t) {
      uni.navigateTo({ url: '/pages/match/bracket?id=' + t.tournament_id });
    },
    goReport(t) {
      uni.navigateTo({ url: '/pages/stats/report?id=' + t.tournament_id });
    }
  },
  onPullDownRefresh() {
    // 无论成功失败都要收起下拉动画，否则加载出问题时转圈会一直挂着
    const done = () => uni.stopPullDownRefresh();
    this.load().then(done, done);
  },
  onShareAppMessage() {
    return { title: '羽毛球赛事 · 我的赛事', path: '/pages/tournament/list' };
  }
};
</script>

<style scoped>
.toolbar {
  margin-bottom: var(--sp-3);
}

.search {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  min-height: 42px;
  padding: 0 var(--sp-3);
  margin-bottom: var(--sp-2);
}

.search__input {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: var(--c-text);
}

.search__clear {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ph {
  color: #b3bfd2;
  font-size: 14px;
}

.tcard {
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: var(--sp-4);
  box-shadow: var(--sh-1);
  margin-bottom: var(--sp-3);
}

.tcard__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-2);
}

.tcard__title {
  flex: 1;
  font-size: 16px;
  font-weight: 700;
  color: var(--c-text);
  line-height: 1.4;
}

.tcard__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.tcard__metrics {
  display: flex;
  align-items: center;
  background-color: #f7f9fd;
  border-radius: var(--r-sm);
  padding: 10px 6px;
  margin-top: 12px;
}

.metric {
  flex: 1;
  text-align: center;
}

.metric__value {
  display: block;
  font-size: 16px;
  color: var(--c-primary);
}

.metric__label {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 1px;
}

.metric__sep {
  width: 1px;
  height: 22px;
  background-color: var(--c-border);
}

.tcard__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.chip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid transparent;
}

.chip--primary {
  background-color: var(--c-primary);
}

.chip--accent {
  background-color: var(--c-accent);
}

.chip--ghost {
  background-color: var(--c-surface);
  border-color: var(--c-border);
}

.chip__text {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.chip__text--ghost {
  color: var(--c-primary);
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.skeleton--card {
  height: 168px;
}

.btn__text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.btn__text--gap {
  margin-left: 6px;
}
</style>
