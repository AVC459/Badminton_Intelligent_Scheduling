<template>
  <view class="container">
    <!-- 头部：赛事 + 进度 -->
    <view class="head">
      <view class="head__top">
        <text class="head__title t-ellipsis">{{ t ? t.title : '对阵与积分' }}</text>
        <wb-tag v-if="t" :text="formatShort(t.format)" tone="info" size="sm" />
      </view>
      <view class="head__metrics">
        <view class="hm">
          <text class="hm__v t-num">{{ t && t.actual_participants || 0 }}</text>
          <text class="hm__l">参赛</text>
        </view>
        <view class="hm__sep" />
        <view class="hm">
          <text class="hm__v t-num">{{ playable.length }}</text>
          <text class="hm__l">总场次</text>
        </view>
        <view class="hm__sep" />
        <view class="hm">
          <text class="hm__v t-num">{{ doneCount }}</text>
          <text class="hm__l">已结束</text>
        </view>
        <view class="hm__sep" />
        <view class="hm">
          <text class="hm__v t-num" :style="{ color: progressPct === 100 ? '#a7f3d0' : '#fff' }">
            {{ progressPct }}%
          </text>
          <text class="hm__l">进度</text>
        </view>
      </view>
    </view>

    <!-- 视图切换 -->
    <view class="segment">
      <view
        v-for="v in views"
        :key="v.value"
        class="segment__item"
        :class="{ 'segment__item--active': view === v.value }"
        @click="view = v.value"
      >
        {{ v.label }}
      </view>
    </view>

    <!-- ============ 一、淘汰对阵 ============ -->
    <block v-if="view === 'knock'">
      <wb-empty
        v-if="!knockRounds.length"
        icon="grid"
        title="还没有淘汰赛对阵"
        :desc="t && t.format === 'GROUP_KNOCKOUT' ? '小组赛全部结束后，可在赛程页生成交叉淘汰赛程' : '执行抽签编排后这里会显示完整的对阵树'"
      />

      <view v-else>
        <wb-section
          v-for="(r, ri) in knockRounds"
          :key="r.key"
          class="block"
          :title="r.name"
          icon="trophy"
          :badge="r.matches.length + ' 场'"
        >
          <view class="round-line">
            <view class="round-line__bar">
              <view
                class="round-line__fill"
                :style="{ width: roundDonePct(r) + '%' }"
              />
            </view>
            <text class="round-line__text t-num">{{ roundDone(r) }} / {{ r.matches.length }}</text>
          </view>

          <view
            v-for="m in r.matches"
            :key="m.match_id"
            class="bout"
            :class="{ 'bout--live': m.status === 'READY' }"
            @click="openScore(m)"
          >
            <view class="bout__no">
              <text class="bout__no-text t-num">#{{ m.match_order }}</text>
            </view>
            <view class="bout__body">
              <view
                class="side"
                :class="{
                  'side--win': m.winner_id && m.winner_id === m.player_a_id,
                  'side--lose': m.winner_id && m.winner_id !== m.player_a_id
                }"
              >
                <text class="side__name t-ellipsis">{{ m.player_a_name || '待定' }}</text>
                <text class="side__team t-ellipsis">{{ m.player_a_team || (m.player_a_id ? '' : '—') }}</text>
                <text class="side__score t-num">{{ m.games_a || 0 }}</text>
              </view>
              <view
                class="side side--b"
                :class="{
                  'side--win': m.winner_id && m.winner_id === m.player_b_id,
                  'side--lose': m.winner_id && m.winner_id !== m.player_b_id
                }"
              >
                <text class="side__name t-ellipsis">{{ m.player_b_name || '待定' }}</text>
                <text class="side__team t-ellipsis">{{ m.player_b_team || (m.player_b_id ? '' : '—') }}</text>
                <text class="side__score t-num">{{ m.games_b || 0 }}</text>
              </view>

              <view class="bout__foot">
                <wb-tag :text="matchStatusLabel(m.status)" :tone="matchStatusTone(m.status)" size="sm" />
                <view v-if="m.games && m.games.length && m.status === 'DONE'" class="bout__games">
                  <text v-for="(g, gi) in m.games" :key="gi" class="bout__game t-num">
                    {{ g.a }}:{{ g.b }}
                  </text>
                </view>
              </view>
            </view>
          </view>
        </wb-section>

        <!-- 季军赛单独成块 -->
        <wb-section
          v-if="thirdMatches.length"
          class="block"
          title="季军赛"
          icon="medal"
          :badge="thirdMatches.length + ' 场'"
        >
          <view
            v-for="m in thirdMatches"
            :key="m.match_id"
            class="bout bout--third"
            :class="{ 'bout--live': m.status === 'READY' }"
            @click="openScore(m)"
          >
            <view class="bout__no">
              <wb-icon name="medal" :size="15" color="#d97706" />
            </view>
            <view class="bout__body">
              <view
                class="side"
                :class="{
                  'side--win': m.winner_id && m.winner_id === m.player_a_id,
                  'side--lose': m.winner_id && m.winner_id !== m.player_a_id
                }"
              >
                <text class="side__name t-ellipsis">{{ m.player_a_name || '待定' }}</text>
                <text class="side__team t-ellipsis">{{ m.player_a_team || '' }}</text>
                <text class="side__score t-num">{{ m.games_a || 0 }}</text>
              </view>
              <view
                class="side side--b"
                :class="{
                  'side--win': m.winner_id && m.winner_id === m.player_b_id,
                  'side--lose': m.winner_id && m.winner_id !== m.player_b_id
                }"
              >
                <text class="side__name t-ellipsis">{{ m.player_b_name || '待定' }}</text>
                <text class="side__team t-ellipsis">{{ m.player_b_team || '' }}</text>
                <text class="side__score t-num">{{ m.games_b || 0 }}</text>
              </view>
              <view class="bout__foot">
                <wb-tag :text="matchStatusLabel(m.status)" :tone="matchStatusTone(m.status)" size="sm" />
              </view>
            </view>
          </view>
        </wb-section>
      </view>
    </block>

    <!-- ============ 二、小组积分 ============ -->
    <block v-else-if="view === 'group'">
      <wb-empty
        v-if="!groups.length"
        icon="layers"
        title="当前赛事没有小组赛"
        desc="仅「小组循环 + 交叉淘汰」赛制会生成分组积分榜，切换上方「总排名」查看循环赛名次"
      />

      <wb-section
        v-for="g in groups"
        :key="g.group_no"
        class="block"
        :title="g.group_name + ' 组'"
        icon="layers"
        :badge="g.finished ? '已结束' : '进行中'"
        :badge-tone="g.finished ? 'success' : 'warning'"
      >
        <text class="group-desc">
          共 {{ g.standings.length }} 人 · 出线 {{ g.advance }} 人 · 胜一场得 2 分
        </text>

        <view class="table">
          <view class="table__head">
            <text class="table__cell table__cell--rank">#</text>
            <text class="table__cell table__cell--name">选手</text>
            <text class="table__cell">赛</text>
            <text class="table__cell">胜</text>
            <text class="table__cell">局差</text>
            <text class="table__cell">分差</text>
            <text class="table__cell">积分</text>
          </view>
          <view
            v-for="p in g.standings"
            :key="p.playerId"
            class="table__row"
            :class="{ 'table__row--advanced': p.advanced }"
          >
            <text class="table__cell table__cell--rank t-num">{{ p.rank }}</text>
            <view class="table__cell table__cell--name">
              <text class="cell-name t-ellipsis">{{ p.playerName }}</text>
              <text v-if="p.team" class="cell-team t-ellipsis">{{ p.team }}</text>
            </view>
            <text class="table__cell t-num">{{ p.played }}</text>
            <text class="table__cell t-num">{{ p.won }}</text>
            <text class="table__cell t-num">{{ signed(p.gameDiff) }}</text>
            <text class="table__cell t-num">{{ signed(p.pointDiff) }}</text>
            <text class="table__cell table__cell--pts t-num">{{ p.points }}</text>
          </view>
        </view>

        <view v-if="g.finished" class="advance-hint">
          <wb-icon name="check" :size="13" color="#15803d" :stroke-width="2.4" />
          <text class="advance-hint__text">高亮行为出线选手，已进入交叉淘汰</text>
        </view>
      </wb-section>
    </block>

    <!-- ============ 三、总排名 ============ -->
    <block v-else>
      <wb-empty
        v-if="!standings.length"
        icon="chart"
        title="还没有排名数据"
        desc="录入比分后，这里会按积分、局差、分差自动排出名次"
      />

      <wb-section
        v-else
        class="block"
        title="总排名"
        icon="chart"
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
            :class="{ 'table__row--podium': p.rank <= 3 }"
          >
            <view class="table__cell table__cell--rank">
              <view v-if="p.rank <= 3" class="medal" :class="'medal--' + p.rank">
                <text class="medal__text t-num">{{ p.rank }}</text>
              </view>
              <text v-else class="t-num rank-plain">{{ p.rank }}</text>
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

      <!-- 领奖台 -->
      <wb-section
        v-if="podium.length"
        class="block"
        title="获奖名单"
        icon="medal"
        desc="冠军、亚军、季军在决赛与季军赛结束后自动产生"
      >
        <view v-for="(p, pi) in podium" :key="p.label" class="podium-row">
          <view class="podium-row__badge" :class="'podium-row__badge--' + pi">
            <text class="podium-row__label">{{ p.label }}</text>
          </view>
          <view class="grow">
            <text class="podium-row__name t-ellipsis">{{ p.player_name || '待产生' }}</text>
            <text v-if="p.team" class="podium-row__team t-ellipsis">{{ p.team }}</text>
          </view>
        </view>
      </wb-section>
    </block>

    <view class="tip">
      <wb-icon name="info" :size="13" color="#8b98b0" />
      <text class="tip__text">{{ tipText }}</text>
    </view>
  </view>
</template>

<script>
import {
  getMatches,
  getTournamentById,
  getGroupStandings,
  getStandings,
  getReport
} from '../../utils/api';
import { requireLogin, requireParam } from '../../utils/session';
import {
  matchStatusLabel,
  matchStatusTone,
  formatShort,
  stageLabel
} from '../../utils/dict';

export default {
  data() {
    return {
      id: '',
      t: null,
      matches: [],
      groups: [],
      standings: [],
      podium: [],
      loading: true,
      view: 'knock'
    };
  },
  computed: {
    playable() {
      return this.matches.filter((m) => m.status !== 'BYE');
    },
    doneCount() {
      return this.playable.filter((m) => m.status === 'DONE' || m.status === 'WALKOVER').length;
    },
    progressPct() {
      if (!this.playable.length) return 0;
      return Math.round((this.doneCount * 100) / this.playable.length);
    },
    views() {
      const list = [];
      if (this.knockAll.length || (this.t && this.t.format !== 'ROUND_ROBIN')) {
        list.push({ value: 'knock', label: '淘汰对阵' });
      }
      if (this.groups.length || (this.t && this.t.format === 'GROUP_KNOCKOUT')) {
        list.push({ value: 'group', label: '小组积分' });
      }
      list.push({ value: 'rank', label: '总排名' });
      return list;
    },
    knockAll() {
      return this.matches.filter((m) => m.stage === 'KNOCKOUT');
    },
    thirdMatches() {
      return this.matches.filter((m) => m.stage === 'THIRD_PLACE');
    },
    /** 按轮次分组，1/8 → 1/4 → 半决赛 → 决赛（由外到内推进） */
    knockRounds() {
      const rounds = [];
      const seen = {};
      this.knockAll.forEach((m) => {
        const r = m.round || 1;
        if (!seen[r]) {
          seen[r] = {
            key: 'R' + r,
            round: r,
            name: m.round_name || stageLabel(m.stage),
            matches: []
          };
          rounds.push(seen[r]);
        }
        seen[r].matches.push(m);
      });
      rounds.forEach((r) => {
        r.matches.sort((a, b) => (a.match_order || 0) - (b.match_order || 0));
      });
      rounds.sort((a, b) => a.round - b.round);
      return rounds;
    },
    rankDesc() {
      if (this.t && this.t.format === 'ROUND_ROBIN') {
        return '循环赛全部结束后，此名次即最终成绩';
      }
      if (this.t && this.t.format === 'SINGLE_ELIM') {
        return '淘汰赛制仅能确定少数名次，未完成的选手按胜场数排序';
      }
      return '小组出线者按淘汰赛成绩排序，未出线者按小组赛积分排序';
    },
    tipText() {
      return '点击任意对阵卡片可直接录入该场比分';
    }
  },
  onLoad(options) {
    if (!requireLogin()) return;
    this.id = (options && options.id) || '';
    if (!requireParam(this.id, '赛事ID')) return;
    this.view = (options && options.view) || 'knock';
  },
  onShow() {
    if (this.id) this.load();
  },
  methods: {
    matchStatusLabel,
    matchStatusTone,
    formatShort,
    signed(v) {
      const n = Number(v || 0);
      return n > 0 ? '+' + n : String(n);
    },
    roundDone(r) {
      return r.matches.filter((m) => m.status === 'DONE' || m.status === 'WALKOVER' || m.status === 'BYE').length;
    },
    roundDonePct(r) {
      if (!r.matches.length) return 0;
      return Math.round((this.roundDone(r) * 100) / r.matches.length);
    },
    async load() {
      this.loading = true;
      try {
        const [t, ms] = await Promise.all([getTournamentById(this.id), getMatches(this.id)]);
        this.t = t || null;
        this.matches = Array.isArray(ms) ? ms : [];
        if (t) uni.setNavigationBarTitle({ title: (t.title || '') + ' · 对阵' });

        // 小组积分榜
        if (t && t.format === 'GROUP_KNOCKOUT') {
          try {
            const gs = await getGroupStandings(this.id);
            this.groups = Array.isArray(gs) ? gs : [];
          } catch (e) {
            this.groups = [];
          }
        } else {
          this.groups = [];
        }

        // 总排名
        try {
          const st = await getStandings(this.id);
          this.standings = Array.isArray(st) ? st : [];
        } catch (e) {
          this.standings = [];
        }

        // 获奖名单
        try {
          const rep = await getReport(this.id);
          const p = (rep && rep.podium) || [];
          // 只展示已产生的结果，避免占位噪声
          this.podium = p.filter((x) => x.player_id);
        } catch (e) {
          this.podium = [];
        }

        // 依据赛事赛制兜底切换视图，避免落在空视图
        const valid = this.views.map((v) => v.value);
        if (valid.indexOf(this.view) < 0) this.view = valid[0];
      } catch (e) {
        /* 已提示 */
      } finally {
        this.loading = false;
      }
    },
    openScore(m) {
      if (m.status === 'BYE') {
        uni.showToast({ title: '轮空场次无需记分', icon: 'none' });
        return;
      }
      if (!m.player_a_id || !m.player_b_id) {
        uni.showToast({ title: '双方选手尚未确定', icon: 'none' });
        return;
      }
      uni.navigateTo({ url: '/pages/match/score?id=' + this.id + '&matchId=' + m.match_id });
    }
  },
  onPullDownRefresh() {
    // 无论成功失败都要收起下拉动画
    const done = () => uni.stopPullDownRefresh();
    this.load().then(done, done);
  },
  onShareAppMessage() {
    return { title: '对阵与积分榜', path: '/pages/match/bracket?id=' + this.id };
  }
};
</script>

<style scoped>
/* ---------- 头部 ---------- */
.head {
  background: linear-gradient(150deg, #1e40af, #2563eb);
  border-radius: var(--r-md);
  padding: 16px;
  box-shadow: var(--sh-2);
  margin-bottom: var(--sp-3);
}

.head__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-2);
}

.head__title {
  flex: 1;
  font-size: 17px;
  font-weight: 700;
  color: #fff;
  line-height: 1.4;
}

.head__metrics {
  display: flex;
  align-items: center;
  margin-top: 14px;
  padding-top: 13px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}

.hm {
  flex: 1;
  text-align: center;
}

.hm__v {
  display: block;
  font-size: 18px;
  color: #fff;
}

.hm__l {
  display: block;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.72);
  margin-top: 2px;
}

.hm__sep {
  width: 1px;
  height: 20px;
  background-color: rgba(255, 255, 255, 0.2);
}

/* ---------- 分段器 ---------- */
.segment {
  margin-bottom: var(--sp-3);
}

/* ---------- 轮次进度 ---------- */
.round-line {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: 10px;
}

.round-line__bar {
  flex: 1;
  height: 5px;
  background-color: var(--c-muted);
  border-radius: 999px;
  overflow: hidden;
}

.round-line__fill {
  height: 100%;
  background: linear-gradient(90deg, #60a5fa, #1e40af);
  border-radius: 999px;
  transition: width 0.3s ease-out;
}

.round-line__text {
  font-size: 11px;
  color: var(--c-text-weak);
  flex-shrink: 0;
}

/* ---------- 对阵卡 ---------- */
.block {
  margin-bottom: var(--sp-3);
}

.bout {
  display: flex;
  gap: 10px;
  background-color: #f7f9fd;
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  padding: 10px;
  margin-bottom: 8px;
}

.bout:last-child {
  margin-bottom: 0;
}

.bout--live {
  background-color: #fff;
  border-color: var(--c-border);
  box-shadow: var(--sh-1);
}

.bout--third {
  background-color: #fffdf8;
  border-color: #f0d9b0;
}

.bout__no {
  width: 30px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 2px;
  flex-shrink: 0;
}

.bout__no-text {
  font-size: 11px;
  color: var(--c-text-weak);
}

.bout__body {
  flex: 1;
  min-width: 0;
}

.side {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-height: 32px;
}

.side--b {
  border-top: 1px solid var(--c-muted);
}

.side__name {
  flex: 1;
  font-size: 14px;
  color: var(--c-text-sub);
  font-weight: 500;
}

.side__team {
  font-size: 11px;
  color: var(--c-text-weak);
  max-width: 32%;
}

.side__score {
  width: 26px;
  text-align: center;
  font-size: 16px;
  color: var(--c-text-weak);
  flex-shrink: 0;
}

.side--win .side__name {
  color: var(--c-text);
  font-weight: 700;
}

.side--win .side__score {
  color: var(--c-primary);
}

.side--lose .side__name {
  color: var(--c-text-weak);
}

.bout__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--c-border);
}

.bout__games {
  display: flex;
  gap: 7px;
}

.bout__game {
  font-size: 11px;
  color: var(--c-text-sub);
  background-color: #eef2f9;
  border-radius: 5px;
  padding: 2px 6px;
}

.bout__meta {
  font-size: 11px;
  color: var(--c-text-weak);
  max-width: 50%;
}

/* ---------- 积分表 ---------- */
.group-desc {
  display: block;
  font-size: 12px;
  color: var(--c-text-weak);
  margin: -6px 0 10px;
}

.table__cell--pts {
  font-weight: 700;
  color: var(--c-primary);
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

.table__row--podium {
  background-color: #fbfcfe;
}

.advance-hint {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 10px;
  padding: 8px 10px;
  background-color: var(--c-success-soft);
  border-radius: var(--r-sm);
}

.advance-hint__text {
  font-size: 11px;
  color: var(--c-success);
}

/* ---------- 名次徽章 ---------- */
.medal {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
}

.medal--1 {
  background-color: #fef3c7;
}

.medal--2 {
  background-color: #e5e7eb;
}

.medal--3 {
  background-color: #fde8d5;
}

.medal__text {
  font-size: 11px;
  color: var(--c-text);
}

.rank-plain {
  font-size: 13px;
  color: var(--c-text-weak);
}

/* ---------- 领奖台 ---------- */
.podium-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 9px 0;
  border-bottom: 1px solid var(--c-muted);
}

.podium-row:last-child {
  border-bottom: none;
}

.podium-row__badge {
  width: 42px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.podium-row__badge--0 {
  background-color: #fef3c7;
}

.podium-row__badge--1 {
  background-color: #e5e7eb;
}

.podium-row__badge--2 {
  background-color: #fde8d5;
}

.podium-row__label {
  font-size: 12px;
  font-weight: 700;
  color: #16233f;
}

.podium-row__name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.podium-row__team {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 2px;
}

/* ---------- 底部提示 ---------- */
.tip {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  padding: 12px 4px 4px;
}

.tip__text {
  flex: 1;
  font-size: 11px;
  color: var(--c-text-weak);
  line-height: 1.6;
}
</style>
