<template>
  <view class="container">
    <!-- 报告头 -->
    <view class="hero">
      <view class="hero__top">
        <view class="grow">
          <text class="hero__title t-ellipsis">{{ t ? t.title : '赛事报告' }}</text>
          <view class="hero__tags">
            <wb-tag v-if="t" :text="eventTypeLabel(t.event_type)" tone="accent" size="sm" />
            <wb-tag v-if="t" :text="formatLabel(t.format)" tone="info" size="sm" />
            <wb-tag
              v-if="t"
              :text="tournamentStatusLabel(t.status)"
              :tone="tournamentStatusTone(t.status)"
              size="sm"
            />
          </view>
        </view>
        <view class="ring">
          <text class="ring__num t-num">{{ progressPct }}</text>
          <text class="ring__unit">%</text>
        </view>
      </view>

      <view class="hero__bar">
        <view class="hero__bar-fill" :style="{ width: progressPct + '%' }" />
      </view>
      <text class="hero__bar-text">
        {{ r.match_done || 0 }} / {{ r.match_total || 0 }} 场已完成
      </text>
    </view>

    <!-- KPI -->
    <view class="kpis">
      <wb-stat
        class="kpis__item"
        icon="users"
        :value="r.participants || 0"
        label="参赛人数"
        :hint="teamCount + ' 个单位'"
        color="#1e40af"
        soft-color="#e8effc"
      />
      <wb-stat
        class="kpis__item"
        icon="grid"
        :value="r.match_total || 0"
        label="总场次"
        :hint="(r.match_bye || 0) + ' 场轮空'"
        color="#d97706"
        soft-color="#fdf3e3"
      />
      <wb-stat
        class="kpis__item"
        icon="check"
        :value="r.match_done || 0"
        label="已结束"
        hint="含弃权判定"
        color="#15803d"
        soft-color="#e6f4ea"
      />
      <wb-stat
        class="kpis__item"
        icon="clock"
        :value="r.match_pending || 0"
        label="待比赛"
        hint="尚未录入比分"
        color="#dc2626"
        soft-color="#fdeaea"
      />
    </view>

    <!-- 获奖名单 -->
    <wb-section
      v-if="podium.length"
      class="block"
      title="获奖名单"
      icon="medal"
      desc="决赛与季军赛结束后自动产生"
    >
      <view v-for="(p, pi) in podium" :key="p.label" class="podium">
        <view class="podium__badge" :class="'podium__badge--' + pi">
          <wb-icon :name="pi === 0 ? 'trophy' : 'medal'" :size="16" :color="podiumColor(pi)" />
        </view>
        <view class="grow">
          <text class="podium__label">{{ p.label }}</text>
          <text class="podium__name t-ellipsis">{{ p.player_name || '待产生' }}</text>
        </view>
        <text v-if="p.team" class="podium__team t-ellipsis">{{ p.team }}</text>
      </view>
    </wb-section>

    <!-- 参与结构 -->
    <wb-section
      class="block"
      title="参与结构"
      icon="users"
      desc="单位与性别构成，用于评估赛事覆盖面"
    >
      <view class="sub">
        <text class="sub__title">单位分布</text>
        <text class="sub__hint t-num">{{ teamCount }} 个</text>
      </view>
      <view v-if="!teamDist.length" class="t-weak">暂无数据</view>
      <view v-else class="bars">
        <view v-for="d in teamDist" :key="d.name" class="bar">
          <text class="bar__name t-ellipsis">{{ d.name }}</text>
          <view class="bar__track">
            <view class="bar__fill" :style="{ width: d.pct + '%' }" />
          </view>
          <text class="bar__value t-num">{{ d.count }}</text>
        </view>
      </view>

      <view class="sub sub--gap">
        <text class="sub__title">性别分布</text>
      </view>
      <view class="genders">
        <view v-for="g in genderDist" :key="g.name" class="gender">
          <text class="gender__value t-num">{{ g.count }}</text>
          <text class="gender__name">{{ g.name }}</text>
          <text class="gender__pct t-num">{{ g.pct }}%</text>
        </view>
      </view>
    </wb-section>

    <!-- 运行指标 -->
    <wb-section class="block" title="运行指标" icon="chart">
      <view class="kv">
        <text class="kv__k">平均每场总分</text>
        <text class="kv__v t-num">{{ r.avg_points_per_match || 0 }} 分</text>
      </view>
      <view class="kv">
        <text class="kv__k">比赛局制</text>
        <text class="kv__v">{{ bestOfText }}</text>
      </view>
      <view class="kv">
        <text class="kv__k">每局分数</text>
        <text class="kv__v">{{ pointsText }}</text>
      </view>
      <view class="kv">
        <text class="kv__k">签位 / 轮空</text>
        <text class="kv__v t-num">{{ (t && t.bracket_size) || '—' }} / {{ (t && t.bye_count) || 0 }}</text>
      </view>
      <view class="kv">
        <text class="kv__k">同单位规避</text>
        <text class="kv__v">{{ t && t.avoid_same_team ? '已开启' : '未开启' }}</text>
      </view>
    </wb-section>

    <!-- 最终排名 -->
    <wb-section
      v-if="standings.length"
      class="block"
      title="最终排名"
      icon="trophy"
      :badge="standings.length + ' 人'"
      :desc="rankDesc"
    >
      <view class="table">
        <view class="table__head">
          <text class="table__cell table__cell--rank">名次</text>
          <text class="table__cell table__cell--name">选手</text>
          <text class="table__cell">赛</text>
          <text class="table__cell">胜/负</text>
          <text class="table__cell">局差</text>
          <text class="table__cell">积分</text>
        </view>
        <view
          v-for="p in standings"
          :key="p.playerId"
          class="table__row"
          :class="{ 'table__row--medal': p.rank <= 3 }"
        >
          <view class="table__cell table__cell--rank">
            <view v-if="p.rank <= 3" class="mini-medal" :class="'mini-medal--' + p.rank">
              <text class="mini-medal__text t-num">{{ p.rank }}</text>
            </view>
            <text v-else class="rank-plain t-num">{{ p.rank }}</text>
          </view>
          <view class="table__cell table__cell--name">
            <text class="cell-name t-ellipsis">{{ p.playerName }}</text>
            <text v-if="p.team" class="cell-team t-ellipsis">{{ p.team }}</text>
          </view>
          <text class="table__cell t-num">{{ p.played }}</text>
          <text class="table__cell t-num">{{ p.won }}/{{ p.lost }}</text>
          <text class="table__cell t-num">{{ signed(p.gameDiff) }}</text>
          <text class="table__cell table__cell--pts t-num">{{ p.points }}</text>
        </view>
      </view>
    </wb-section>

    <!-- 小组积分（分组赛制） -->
    <wb-section
      v-for="g in groups"
      :key="g.group_no"
      class="block"
      :title="g.group_name + ' 组积分'"
      icon="layers"
      :badge="g.finished ? '已结束' : '进行中'"
      :badge-tone="g.finished ? 'success' : 'warning'"
    >
      <view class="table">
        <view class="table__head">
          <text class="table__cell table__cell--rank">#</text>
          <text class="table__cell table__cell--name">选手</text>
          <text class="table__cell">赛</text>
          <text class="table__cell">胜</text>
          <text class="table__cell">局差</text>
          <text class="table__cell">积分</text>
        </view>
        <view
          v-for="p in g.standings"
          :key="p.playerId"
          class="table__row"
          :class="{ 'table__row--advanced': p.advanced }"
        >
          <text class="table__cell table__cell--rank t-num">{{ p.rank }}</text>
          <text class="table__cell table__cell--name">{{ p.playerName }}</text>
          <text class="table__cell t-num">{{ p.played }}</text>
          <text class="table__cell t-num">{{ p.won }}</text>
          <text class="table__cell t-num">{{ signed(p.gameDiff) }}</text>
          <text class="table__cell table__cell--pts t-num">{{ p.points }}</text>
        </view>
      </view>
    </wb-section>

    <!-- 留痕 -->
    <wb-section
      class="block"
      title="报告说明"
      icon="fileText"
      desc="可用于赛后公示与赛事归档"
    >
      <view class="note">
        <wb-icon name="info" :size="13" color="#5b6b88" />
        <text class="note__text">
          本报告数据来源于赛事过程中的抽签留痕与逐场比分记录，
          名次依据「积分 → 局差 → 分差」依次比较得出。所有数据均可回溯至具体场次。
        </text>
      </view>
      <view class="note">
        <wb-icon name="shield" :size="13" color="#5b6b88" />
        <text class="note__text">
          抽签过程已全程留痕，公平性报告可在「编排结果」页查看，确保赛程可复现、可申诉。
        </text>
      </view>
    </wb-section>

    <view class="foot">{{ generatedAt }}</view>
  </view>
</template>

<script>
import { getReport } from '../../utils/api';
import { requireLogin, requireParam } from '../../utils/session';
import {
  formatLabel,
  eventTypeLabel,
  tournamentStatusLabel,
  tournamentStatusTone
} from '../../utils/dict';

export default {
  data() {
    return {
      id: '',
      r: {},
      t: null,
      groups: [],
      standings: [],
      podium: []
    };
  },
  computed: {
    progressPct() {
      const p = this.r.progress;
      return p === null || p === undefined ? 0 : Math.round(Number(p));
    },
    teamDist() {
      return this.toDist(this.r.team_distribution);
    },
    genderDist() {
      const total = (this.r.gender_distribution && this.total(this.r.gender_distribution)) || 0;
      const out = [];
      const src = this.r.gender_distribution || {};
      Object.keys(src).forEach((k) => {
        out.push({
          name: k,
          count: src[k],
          pct: total ? Math.round((src[k] * 100) / total) : 0
        });
      });
      return out;
    },
    teamCount() {
      return this.teamDist.length;
    },
    /** 与后端 bestOf/2+1 口径一致：1 局赛事不能再显示成「3 局 2 胜」 */
    bestOfText() {
      const b = Number((this.t && this.t.best_of) || 3) || 3;
      return b + ' 局 ' + (Math.floor(b / 2) + 1) + ' 胜';
    },
    pointsText() {
      const per = (this.t && this.t.points_per_game) || 21;
      const cap = (this.t && this.t.points_cap) || 30;
      return per + ' 分制，封顶 ' + cap + ' 分';
    },
    rankDesc() {
      if (this.t && this.t.format === 'ROUND_ROBIN') return '循环赛全部结束，此名次即最终成绩';
      if (this.t && this.t.format === 'SINGLE_ELIM') return '淘汰赛制仅确定前若干名次，其余按胜场排序';
      return '小组出线者按淘汰赛成绩排序，未出线者按小组赛积分排序';
    },
    generatedAt() {
      const d = new Date();
      const p = (n) => (n < 10 ? '0' + n : String(n));
      return '报告生成于 ' + d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) +
        ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
    }
  },
  onLoad(options) {
    if (!requireLogin()) return;
    this.id = (options && options.id) || '';
    if (!requireParam(this.id, '赛事ID')) return;
    this.load();
  },
  methods: {
    formatLabel,
    eventTypeLabel,
    tournamentStatusLabel,
    tournamentStatusTone,
    signed(v) {
      const n = Number(v || 0);
      return n > 0 ? '+' + n : String(n);
    },
    podiumColor(pi) {
      return ['#d97706', '#5b6b88', '#b45309'][pi] || '#5b6b88';
    },
    total(map) {
      let s = 0;
      Object.keys(map || {}).forEach((k) => {
        s += Number(map[k]) || 0;
      });
      return s;
    },
    /** { 单位: 人数 } → 按人数倒序的条形数据 */
    toDist(map) {
      const src = map || {};
      const total = this.total(src);
      const list = Object.keys(src).map((k) => ({
        name: k,
        count: src[k],
        pct: total ? Math.round((src[k] * 100) / total) : 0
      }));
      list.sort((a, b) => b.count - a.count || String(a.name).localeCompare(String(b.name)));
      return list;
    },
    async load() {
      try {
        const r = await getReport(this.id);
        this.r = r || {};
        this.t = (r && r.tournament) || null;
        this.standings = (r && r.standings) || [];
        this.groups = (r && r.groups) || [];
        this.podium = ((r && r.podium) || []).filter((p) => p.player_id);
        if (this.t) uni.setNavigationBarTitle({ title: (this.t.title || '') + ' · 报告' });
      } catch (e) {
        /* 已提示 */
      }
    }
  },
  onPullDownRefresh() {
    // 无论成功失败都要收起下拉动画
    const done = () => uni.stopPullDownRefresh();
    this.load().then(done, done);
  },
  onShareAppMessage() {
    return { title: '赛事报告 · 名次与统计', path: '/pages/stats/report?id=' + this.id };
  }
};
</script>

<style scoped>
/* ---------- 头部 ---------- */
.hero {
  background: linear-gradient(150deg, #1e40af, #2563eb);
  border-radius: var(--r-md);
  padding: 16px;
  box-shadow: var(--sh-2);
  margin-bottom: var(--sp-3);
}

.hero__top {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
}

.hero__title {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  line-height: 1.4;
}

.hero__tags {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.ring {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  border: 4px solid rgba(255, 255, 255, 0.28);
  display: flex;
  align-items: baseline;
  justify-content: center;
  flex-shrink: 0;
}

.ring__num {
  font-size: 20px;
  color: #fff;
  align-self: center;
}

.ring__unit {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.8);
  align-self: flex-end;
  margin-bottom: 6px;
}

.hero__bar {
  height: 6px;
  background-color: rgba(255, 255, 255, 0.24);
  border-radius: 999px;
  overflow: hidden;
  margin-top: 15px;
}

.hero__bar-fill {
  height: 100%;
  background-color: #fbbf24;
  border-radius: 999px;
  transition: width 0.35s ease-out;
}

.hero__bar-text {
  display: block;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.78);
  margin-top: 7px;
}

/* ---------- KPI ---------- */
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

/* ---------- 获奖 ---------- */
.podium {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px 0;
  border-bottom: 1px solid var(--c-muted);
}

.podium:last-child {
  border-bottom: none;
}

.podium__badge {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.podium__badge--0 {
  background-color: #fef3c7;
}

.podium__badge--1 {
  background-color: #eef1f6;
}

.podium__badge--2 {
  background-color: #fde8d5;
}

.podium__label {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
}

.podium__name {
  display: block;
  font-size: 15px;
  font-weight: 700;
  color: var(--c-text);
  margin-top: 1px;
}

.podium__team {
  font-size: 12px;
  color: var(--c-text-sub);
  max-width: 34%;
}

/* ---------- 分布 ---------- */
.sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.sub--gap {
  margin-top: var(--sp-4);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-muted);
}

.sub__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-sub);
}

.sub__hint {
  font-size: 12px;
  color: var(--c-text-weak);
}

.bars {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.bar {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.bar__name {
  width: 88px;
  font-size: 12px;
  color: var(--c-text-sub);
  flex-shrink: 0;
}

.bar__track {
  flex: 1;
  height: 9px;
  background-color: var(--c-muted);
  border-radius: 999px;
  overflow: hidden;
}

.bar__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #60a5fa, #1e40af);
  transition: width 0.4s ease-out;
}

.bar__value {
  width: 26px;
  text-align: right;
  font-size: 12px;
  color: var(--c-text);
  flex-shrink: 0;
}

.genders {
  display: flex;
  gap: var(--sp-2);
}

.gender {
  flex: 1;
  background-color: #f7f9fd;
  border-radius: var(--r-sm);
  padding: 12px;
  text-align: center;
}

.gender__value {
  display: block;
  font-size: 22px;
  color: var(--c-primary);
}

.gender__name {
  display: block;
  font-size: 12px;
  color: var(--c-text-sub);
  margin-top: 2px;
}

.gender__pct {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 2px;
}

/* ---------- 指标 ---------- */
.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  min-height: 40px;
  border-bottom: 1px solid var(--c-muted);
}

.kv:last-child {
  border-bottom: none;
}

.kv__k {
  font-size: 13px;
  color: var(--c-text-sub);
  flex-shrink: 0;
}

.kv__v {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
  text-align: right;
  max-width: 62%;
}

/* ---------- 表格 ---------- */
.table__cell--pts {
  font-weight: 700;
  color: var(--c-primary);
}

.table__row--medal {
  background-color: #fbfcfe;
}

.cell-name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}

.cell-team {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 1px;
}

.mini-medal {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
}

.mini-medal--1 {
  background-color: #fef3c7;
}

.mini-medal--2 {
  background-color: #e5e7eb;
}

.mini-medal--3 {
  background-color: #fde8d5;
}

.mini-medal__text {
  font-size: 11px;
  color: var(--c-text);
}

.rank-plain {
  font-size: 13px;
  color: var(--c-text-weak);
}

/* ---------- 说明 ---------- */
.note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  background-color: #f7f9fd;
  border-radius: var(--r-sm);
  padding: 9px 11px;
  margin-bottom: 8px;
}

.note:last-child {
  margin-bottom: 0;
}

.note__text {
  flex: 1;
  font-size: 12px;
  color: var(--c-text-sub);
  line-height: 1.65;
}

.foot {
  text-align: center;
  font-size: 11px;
  color: var(--c-text-weak);
  padding: var(--sp-2) 0 var(--sp-4);
}
</style>
