<template>
  <view class="container container--with-footer">
    <view v-if="loading" class="skeleton-list">
      <view class="skeleton skeleton--hero" />
      <view class="skeleton skeleton--card" />
      <view class="skeleton skeleton--card" />
    </view>

    <block v-else-if="tournament">
      <!-- 标题卡 -->
      <view class="hero">
        <view class="hero__head">
          <text class="hero__title">{{ tournament.title }}</text>
          <wb-tag
            :text="tournamentStatusLabel(tournament.status)"
            :tone="tournamentStatusTone(tournament.status)"
          />
        </view>
        <view class="hero__tags">
          <wb-tag :text="formatLabel(tournament.format)" tone="info" size="sm" />
          <wb-tag :text="eventTypeLabel(tournament.event_type)" tone="neutral" size="sm" />
          <wb-tag
            v-if="tournament.avoid_same_team"
            text="同单位规避"
            tone="accent"
            size="sm"
            icon="shield"
          />
          <wb-tag
            v-if="tournament.best_of"
            :text="tournament.best_of + ' 局 ' + bestOfNeed + ' 胜 ' + (tournament.points_per_game || 21) + ' 分'"
            tone="neutral"
            size="sm"
          />
        </view>
        <text v-if="tournament.remark" class="hero__remark">{{ tournament.remark }}</text>
      </view>

      <!-- 进度 -->
      <wb-section class="block" title="完成进度" icon="chart">
        <view class="progress">
          <view class="progress__bar">
            <view class="progress__fill" :style="{ width: progress.progress + '%' }" />
          </view>
          <text class="progress__pct t-num">{{ progress.progress }}%</text>
        </view>
        <view class="mini-metrics">
          <view class="mini">
            <text class="mini__value t-num">{{ progress.match_done }}</text>
            <text class="mini__label">已完成</text>
          </view>
          <view class="mini">
            <text class="mini__value t-num">{{ progress.match_total - progress.match_done }}</text>
            <text class="mini__label">未开始</text>
          </view>
          <view class="mini">
            <text class="mini__value t-num">{{ progress.match_total }}</text>
            <text class="mini__label">总场次</text>
          </view>
          <view class="mini">
            <text class="mini__value t-num">{{ progress.match_bye }}</text>
            <text class="mini__label">轮空</text>
          </view>
        </view>
      </wb-section>

      <!-- 参赛名单 -->
      <wb-section
        class="block"
        title="参赛名单"
        icon="users"
        :badge="entries.total + ' 人'"
        action-text="管理名单"
        desc="从我的选手库直接选人，无报名与审核环节"
        @action="goEntries"
      >
        <view class="roster">
          <view v-for="p in rosterPreview" :key="p.player_id" class="roster__row">
            <text class="roster__seed t-num">{{ p.is_seed ? p.seed_rank : '·' }}</text>
            <text class="roster__name t-ellipsis">{{ p.name }}</text>
            <text class="roster__team t-ellipsis">{{ p.team || '未填单位' }}</text>
          </view>
          <view v-if="!entries.total" class="t-weak">还没有选手，点击「管理名单」从选手库中选人</view>
          <view v-else-if="entries.total > rosterPreview.length" class="roster__more">
            另有 {{ entries.total - rosterPreview.length }} 位选手，点击「管理名单」查看全部
          </view>
        </view>
        <view class="roster__foot">
          <text class="roster__stat">共 {{ entries.total }} 人 · 种子 {{ entries.seed_count }} 人</text>
        </view>
        <view v-if="entries.total < 2" class="notice notice--warn">
          <wb-icon name="alert" :size="14" color="#b45309" />
          <text class="notice__text">参赛人数不足 2 人，无法执行抽签编排</text>
        </view>
      </wb-section>

      <!-- 领奖台 -->
      <wb-section v-if="hasPodium" class="block" title="最终名次" icon="medal">
        <view class="podium">
          <view v-if="third" class="podium__item podium__item--3">
            <text class="podium__rank">3</text>
            <text class="podium__name t-ellipsis">{{ third }}</text>
          </view>
          <view v-if="champion" class="podium__item podium__item--1">
            <text class="podium__rank">1</text>
            <text class="podium__name t-ellipsis">{{ champion }}</text>
          </view>
          <view v-if="runnerUp" class="podium__item podium__item--2">
            <text class="podium__rank">2</text>
            <text class="podium__name t-ellipsis">{{ runnerUp }}</text>
          </view>
        </view>
        <view class="podium__actions">
          <view class="chip chip--ghost" @click="goReport">
            <wb-icon name="fileText" :size="13" color="#1e40af" />
            <text class="chip__text chip__text--ghost">查看赛事报告</text>
          </view>
        </view>
      </wb-section>

      <!-- 编排信息 -->
      <wb-section v-if="hasDrawn" class="block" title="编排参数" icon="shuffle">
        <view class="kv">
          <text class="kv__k">签位规模</text>
          <text class="kv__v t-num">{{ tournament.bracket_size || '—' }}</text>
        </view>
        <view class="kv">
          <text class="kv__k">轮空数</text>
          <text class="kv__v t-num">{{ tournament.bye_count || 0 }}</text>
        </view>
        <view class="kv">
          <text class="kv__k">种子数</text>
          <text class="kv__v t-num">{{ tournament.seed_count || 0 }}</text>
        </view>
        <view v-if="tournament.format === 'GROUP_KNOCKOUT'" class="kv">
          <text class="kv__k">分组 / 出线</text>
          <text class="kv__v t-num">
            {{ tournament.group_count || '自动' }} 组 · 每组 {{ tournament.group_advance || 2 }} 人
          </text>
        </view>

      </wb-section>

      <!-- 留痕 -->
      <wb-section
        v-if="logs.length"
        class="block"
        title="编排留痕"
        icon="shield"
        :badge="logs.length"
        desc="每一步抽签操作均已留痕，可用于公示与结果复核"
      >
        <view v-for="l in logs" :key="l.log_id" class="log">
          <view class="log__dot" />
          <view class="grow">
            <text class="log__msg">{{ l.message }}</text>
            <text class="log__time">{{ formatDateTime(l.create_time) }}</text>
          </view>
        </view>
      </wb-section>
    </block>

    <!-- 底部操作 -->
    <view v-if="tournament" class="footer-bar">
      <view v-if="!hasDrawn" class="btn btn--ghost btn--sm footer-btn" @click="goEntries">
        <text class="btn__text-ghost">参赛名单</text>
      </view>
      <view v-if="!hasDrawn" class="btn btn--accent btn--sm footer-btn" @click="goDraw">
        <text class="btn__text">抽签编排</text>
      </view>
      <view v-if="hasDrawn" class="btn btn--primary btn--sm footer-btn" @click="goMatches">
        <text class="btn__text">赛程记分</text>
      </view>
      <view v-if="hasDrawn" class="btn btn--ghost btn--sm footer-btn" @click="goBracket">
        <text class="btn__text-ghost">对阵</text>
      </view>
      <view class="btn btn--ghost btn--sm footer-btn" @click="openMore">
        <text class="btn__text-ghost">更多</text>
      </view>
    </view>
  </view>
</template>

<script>
import { getTournamentDetail, deleteTournament, resetDraw } from '../../utils/api';
import { requireLogin, requireParam } from '../../utils/session';
import {
  formatLabel,
  eventTypeLabel,
  tournamentStatusLabel,
  tournamentStatusTone,
  formatDateTime
} from '../../utils/dict';

export default {
  data() {
    return {
      id: '',
      loading: true,
      tournament: null,
      entries: { total: 0, seed_count: 0, players: [] },
      progress: { match_total: 0, match_done: 0, match_bye: 0, progress: 0 },
      logs: [],
      podiumNames: {}
    };
  },
  computed: {
    roster() {
      return Array.isArray(this.entries.players) ? this.entries.players : [];
    },
    rosterPreview() {
      return this.roster.slice(0, 6);
    },
    /** 是否已完成抽签编排（决定底部操作按钮） */
    hasDrawn() {
      const s = this.tournament && this.tournament.status;
      return s === 'DRAWN' || s === 'ONGOING' || s === 'FINISHED';
    },
    champion() {
      return this.podiumNames[this.tournament && this.tournament.champion_id] || '';
    },
    runnerUp() {
      return this.podiumNames[this.tournament && this.tournament.runner_up_id] || '';
    },
    third() {
      return this.podiumNames[this.tournament && this.tournament.third_id] || '';
    },
    hasPodium() {
      return !!(this.tournament && (this.tournament.champion_id || this.tournament.runner_up_id));
    },
    /** 需赢几局（与后端 bestOf/2+1 一致）：1 局定胜负=1、3 局两胜=2 */
    bestOfNeed() {
      const b = Number((this.tournament && this.tournament.best_of) || 3) || 3;
      return Math.floor(b / 2) + 1;
    }
  },
  onShareAppMessage() {
    const t = this.tournament || {};
    return {
      title: (t.title || '羽毛球赛事') + ' · 赛程与成绩',
      path: '/pages/tournament/detail?id=' + this.id
    };
  },
  onLoad(options) {
    if (!requireLogin()) return;
    this.id = (options && options.id) || '';
    if (!requireParam(this.id, '赛事ID')) return;
  },
  onShow() {
    if (this.id) this.load();
  },
  methods: {
    formatLabel,
    eventTypeLabel,
    tournamentStatusLabel,
    tournamentStatusTone,
    formatDateTime,
    async load() {
      this.loading = true;
      try {
        const d = await getTournamentDetail(this.id);
        this.tournament = d.tournament;
        if (d.entries) this.entries = d.entries;
        if (d.progress) this.progress = d.progress;
        this.logs = d.logs || [];
        if (this.tournament) {
          uni.setNavigationBarTitle({ title: this.tournament.title || '赛事详情' });
        }
        this.buildPodiumNames();
      } catch (e) {
        /* 已提示 */
      } finally {
        this.loading = false;
      }
    },
    /** 名次选手姓名：直接取自参赛名单，无需额外请求 */
    buildPodiumNames() {
      const t = this.tournament;
      if (!t) return;
      const ids = [t.champion_id, t.runner_up_id, t.third_id].filter(Boolean);
      if (!ids.length) return;
      const map = {};
      this.roster.forEach((p) => {
        map[p.player_id] = p.name + (p.team ? '（' + p.team + '）' : '');
      });
      const out = {};
      ids.forEach((pid) => {
        out[pid] = map[pid] || pid;
      });
      this.podiumNames = out;
    },
    goEntries() {
      uni.navigateTo({ url: '/pages/entry/list?id=' + this.id });
    },
    goDraw() {
      uni.navigateTo({ url: '/pages/draw/config?id=' + this.id });
    },
    goMatches() {
      uni.navigateTo({ url: '/pages/match/list?id=' + this.id });
    },
    goBracket() {
      uni.navigateTo({ url: '/pages/match/bracket?id=' + this.id });
    },
    goReport() {
      uni.navigateTo({ url: '/pages/stats/report?id=' + this.id });
    },
    openMore() {
      const items = ['编辑赛事信息', '查看编排结果'];
      if (this.hasDrawn) items.push('撤销抽签并重排');
      items.push('删除赛事');
      uni.showActionSheet({
        itemList: items,
        success: (res) => {
          const label = items[res.tapIndex];
          if (label === '编辑赛事信息') {
            // 创建赛事是底部 tab 页，switchTab 不支持带参：用本地存储传递赛事ID
            uni.setStorageSync('edit_tournament_id', this.id);
            uni.switchTab({ url: '/pages/tournament/create' });
          } else if (label === '查看编排结果') {
            uni.navigateTo({ url: '/pages/draw/result?id=' + this.id });
          } else if (label === '撤销抽签并重排') {
            this.confirmReset();
          } else if (label === '删除赛事') {
            this.confirmDelete();
          }
        }
      });
    },
    confirmReset() {
      uni.showModal({
        title: '撤销抽签',
        content: '将删除本赛事已生成的全部场次与分组结果，参赛名单保留。确定继续？',
        confirmColor: '#dc2626',
        success: async (r) => {
          if (!r.confirm) return;
          try {
            await resetDraw(this.id);
            uni.showToast({ title: '已撤销', icon: 'success' });
            this.load();
          } catch (e) {
            /* 已提示 */
          }
        }
      });
    },
    confirmDelete() {
      uni.showModal({
        title: '删除赛事',
        content: '赛事及其全部场次、参赛名单将被永久删除，无法恢复。确定删除？',
        confirmText: '删除',
        confirmColor: '#dc2626',
        success: async (r) => {
          if (!r.confirm) return;
          try {
            await deleteTournament(this.id);
            uni.showToast({ title: '已删除', icon: 'success' });
            setTimeout(() => uni.navigateBack(), 600);
          } catch (e) {
            /* 已提示 */
          }
        }
      });
    }
  }
};
</script>

<style scoped>
.hero {
  background: linear-gradient(150deg, #1e40af, #2563eb);
  border-radius: var(--r-md);
  padding: 18px;
  margin-bottom: var(--sp-3);
  box-shadow: var(--sh-2);
}

.hero__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-2);
}

.hero__title {
  flex: 1;
  font-size: 19px;
  font-weight: 700;
  color: #fff;
  line-height: 1.4;
}

.hero__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.hero__remark {
  display: block;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
  line-height: 1.6;
  margin-top: 10px;
}

.block {
  margin-bottom: var(--sp-3);
}

.progress {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.progress__bar {
  flex: 1;
  height: 9px;
  background-color: var(--c-muted);
  border-radius: 999px;
  overflow: hidden;
}

.progress__fill {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #1e40af);
  border-radius: 999px;
  transition: width 0.35s ease-out;
}

.progress__pct {
  font-size: 15px;
  color: var(--c-primary);
  width: 52px;
  text-align: right;
}

.mini-metrics {
  display: flex;
  margin-top: var(--sp-3);
}

.mini {
  flex: 1;
  text-align: center;
}

.mini__value {
  display: block;
  font-size: 17px;
  color: var(--c-text);
}

.mini__label {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 2px;
}

/* ---------- 参赛名单 ---------- */
.roster {
  display: flex;
  flex-direction: column;
}

.roster__row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-height: 40px;
  border-bottom: 1px solid var(--c-muted);
}

.roster__row:last-child {
  border-bottom: none;
}

.roster__seed {
  width: 20px;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  color: #d97706;
  flex-shrink: 0;
}

.roster__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
  flex: 1;
}

.roster__team {
  font-size: 11px;
  color: var(--c-text-weak);
  max-width: 40%;
  text-align: right;
}

.roster__more {
  font-size: 11px;
  color: var(--c-text-weak);
  padding-top: 8px;
}

.roster__foot {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--c-muted);
}

.roster__stat {
  font-size: 12px;
  color: var(--c-text-sub);
}

.notice {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: var(--sp-3);
  padding: 9px 11px;
  border-radius: var(--r-sm);
}

.notice--warn {
  background-color: var(--c-warning-soft);
}

.notice__text {
  font-size: 12px;
  color: var(--c-warning);
  flex: 1;
}

.podium {
  display: flex;
  align-items: flex-end;
  gap: var(--sp-2);
}

.podium__item {
  flex: 1;
  border-radius: var(--r-sm);
  padding: 10px 6px;
  text-align: center;
}

.podium__item--1 {
  background-color: #fdf3e3;
  border: 1.5px solid #f0c88a;
  min-height: 82px;
}

.podium__item--2 {
  background-color: #eef1f6;
  border: 1.5px solid #cfd8e6;
  min-height: 68px;
}

.podium__item--3 {
  background-color: #f6f1ea;
  border: 1.5px solid #ddc9b0;
  min-height: 58px;
}

.podium__rank {
  display: block;
  font-size: 15px;
  font-weight: 700;
  color: var(--c-text-sub);
}

.podium__name {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text);
  margin-top: 4px;
}

.podium__actions {
  margin-top: var(--sp-3);
}

.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 38px;
  border-bottom: 1px solid var(--c-muted);
}

.kv:last-child {
  border-bottom: none;
}

.kv__k {
  font-size: 13px;
  color: var(--c-text-sub);
}

.kv__v {
  font-size: 13px;
  color: var(--c-text);
  font-weight: 500;
}

.log {
  display: flex;
  gap: var(--sp-3);
  padding: 8px 0;
}

.log__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--c-secondary);
  margin-top: 6px;
  flex-shrink: 0;
}

.log__msg {
  display: block;
  font-size: 13px;
  color: var(--c-text);
  line-height: 1.6;
}

.log__time {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 3px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--c-border);
  background-color: var(--c-surface);
}

.chip__text--ghost {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-primary);
}

.footer-btn {
  flex: 1;
}

.btn__text {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.btn__text-ghost {
  color: var(--c-primary);
  font-size: 14px;
  font-weight: 600;
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.skeleton--hero {
  height: 140px;
}

.skeleton--card {
  height: 120px;
}
</style>
