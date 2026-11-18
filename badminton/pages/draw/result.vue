<template>
  <view class="container">
    <!-- 概览 -->
    <view class="hero">
      <view class="hero__head">
        <text class="hero__title">{{ t ? t.title : '编排结果' }}</text>
        <wb-tag :text="formatLabel(format)" tone="info" size="sm" />
      </view>
      <view v-if="isFresh" class="hero__banner">
        <wb-icon name="check" :size="14" color="#ffffff" :stroke-width="2.6" />
        <text class="hero__banner-text">抽签编排已完成，赛程已生成</text>
      </view>
      <view class="hero__metrics">
        <view class="hm">
          <text class="hm__v t-num">{{ t && t.actual_participants || 0 }}</text>
          <text class="hm__l">参赛</text>
        </view>
        <view class="hm__sep" />
        <view class="hm">
          <text class="hm__v t-num">{{ t && t.bracket_size || '—' }}</text>
          <text class="hm__l">签位</text>
        </view>
        <view class="hm__sep" />
        <view class="hm">
          <text class="hm__v t-num">{{ t && t.bye_count || 0 }}</text>
          <text class="hm__l">轮空</text>
        </view>
        <view class="hm__sep" />
        <view class="hm">
          <text class="hm__v t-num">{{ totalMatches }}</text>
          <text class="hm__l">场次</text>
        </view>
      </view>
    </view>

    <!-- 公平性报告 -->
    <wb-section
      class="block"
      title="公平性报告"
      icon="shield"
      desc="从规模、种子覆盖、实力均衡、同单位规避四个维度量化抽签质量"
    >
      <view v-if="!fairness" class="t-weak">暂无公平性数据</view>
      <block v-else>
        <view class="score">
          <view class="score__ring" :style="{ borderColor: scoreColor }">
            <text class="score__num t-num" :style="{ color: scoreColor }">{{ fairness.score }}</text>
            <text class="score__unit">分</text>
          </view>
          <view class="grow">
            <text class="score__title">{{ scoreTitle }}</text>
            <view class="score__bar">
              <view
                class="score__fill"
                :style="{ width: fairness.score + '%', backgroundColor: scoreColor }"
              />
            </view>
            <text class="score__hint">
              {{ fairness.format === 'GROUP_KNOCKOUT' ? '分组均衡度评估' : '签位质量评估' }}
            </text>
          </view>
        </view>

        <!-- 淘汰赛指标 -->
        <view v-if="fairness.seedPerQuarter && fairness.seedPerQuarter.length" class="metric-block">
          <text class="metric-block__title">各 1/4 区分布</text>
          <view class="q-grid">
            <view v-for="(n, i) in fairness.seedPerQuarter" :key="i" class="q">
              <text class="q__label">{{ quarterName(i) }}</text>
              <text class="q__seed t-num">{{ n }} 种子</text>
              <text class="q__level t-num">
                实力 {{ fairness.levelPerQuarter && fairness.levelPerQuarter[i] != null ? fairness.levelPerQuarter[i] : '—' }}
              </text>
            </view>
          </view>
          <view class="kv">
            <text class="kv__k">实力离散度（标准差）</text>
            <text class="kv__v t-num">{{ round2(fairness.levelStdDev) }}</text>
          </view>
          <view v-if="fairness.byePerHalf && fairness.byePerHalf.length" class="kv">
            <text class="kv__k">上下半区轮空数</text>
            <text class="kv__v t-num">{{ fairness.byePerHalf.join(' / ') }}</text>
          </view>
        </view>

        <!-- 分组指标 -->
        <view v-if="fairness.groupSizes && fairness.groupSizes.length" class="metric-block">
          <text class="metric-block__title">各小组均衡度</text>
          <view class="q-grid">
            <view v-for="(n, i) in fairness.groupSizes" :key="i" class="q">
              <text class="q__label">{{ groupLetter(i) }} 组</text>
              <text class="q__seed t-num">{{ n }} 人</text>
              <text class="q__level t-num">
                实力 {{ fairness.levelPerGroup && fairness.levelPerGroup[i] != null ? fairness.levelPerGroup[i] : '—' }}
              </text>
            </view>
          </view>
          <view class="kv">
            <text class="kv__k">实力总分极差</text>
            <text class="kv__v t-num">{{ fairness.groupLevelRange }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">实力离散度（标准差）</text>
            <text class="kv__v t-num">{{ round2(fairness.groupLevelStdDev) }}</text>
          </view>
        </view>

        <!-- 通用指标 -->
        <view class="metric-block">
          <view class="kv">
            <text class="kv__k">同单位配对数</text>
            <text class="kv__v t-num">{{ fairness.sameTeamPairs != null ? fairness.sameTeamPairs : fairness.sameTeamInGroup }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">首轮同单位相遇（死签）</text>
            <text class="kv__v t-num" :class="{ 'kv__v--good': !fairness.firstRoundClashes }">
              {{ fairness.firstRoundClashes || 0 }}
            </text>
          </view>
          <view class="kv">
            <text class="kv__k">同单位规避达成率</text>
            <text class="kv__v t-num">{{ pct(fairness.avoidanceRate) }}</text>
          </view>
        </view>

        <!-- 风险提示 -->
        <view v-if="fairness.notes && fairness.notes.length" class="notes">
          <view v-for="(n, i) in fairness.notes" :key="i" class="note">
            <wb-icon name="info" :size="13" color="#5b6b88" />
            <text class="note__text">{{ n }}</text>
          </view>
        </view>
      </block>
    </wb-section>

    <!-- 分组 -->
    <wb-section
      v-if="groups.length"
      class="block"
      title="分组名单"
      icon="layers"
      :badge="groups.length + ' 组'"
      action-text="查看积分榜"
      @action="goBracket"
    >
      <view v-for="g in groups" :key="g.group_no" class="group">
        <view class="group__head">
          <text class="group__name">{{ g.group_name }} 组</text>
          <text class="t-weak">{{ g.standings.length }} 人 · 出线 {{ g.advance }} 人</text>
        </view>
        <view class="group__players">
          <view v-for="p in g.standings" :key="p.playerId" class="gplayer">
            <text class="gplayer__rank t-num">{{ p.rank }}</text>
            <view class="grow">
              <text class="gplayer__name t-ellipsis">{{ p.playerName }}</text>
              <text class="gplayer__team t-ellipsis">{{ p.team || '未填单位' }}</text>
            </view>
            <wb-tag
              v-if="p.advanced"
              text="出线"
              tone="success"
              size="sm"
            />
            <text v-else class="t-weak t-num">{{ p.points }} 分</text>
          </view>
        </view>
      </view>
    </wb-section>

    <!-- 签位表（淘汰制） -->
    <wb-section
      v-if="slots.length"
      class="block"
      title="签位表"
      icon="grid"
      :badge="slots.length + ' 签位'"
      action-text="查看对阵图"
      @action="goBracket"
    >
      <view class="slots">
        <view
          v-for="s in slots"
          :key="s.slot"
          class="slot"
          :class="{ 'slot--bye': s.bye, 'slot--seed': s.seed }"
        >
          <text class="slot__no t-num">{{ s.slot }}</text>
          <view class="grow">
            <text class="slot__name t-ellipsis">{{ s.bye ? '轮空' : s.name }}</text>
            <text v-if="!s.bye && s.team" class="slot__team t-ellipsis">{{ s.team }}</text>
          </view>
          <wb-tag v-if="s.seed && !s.bye" :text="'种子 ' + s.seedRank" tone="accent" size="sm" />
        </view>
      </view>
    </wb-section>

    <!-- 留痕 -->
    <wb-section
      v-if="logs.length"
      class="block"
      title="编排留痕"
      icon="list"
      :badge="logs.length"
      desc="每一步抽签操作均已留痕，可回溯、可公示"
    >
      <view v-for="l in logs" :key="l.log_id" class="log">
        <view class="log__dot" />
        <view class="grow">
          <text class="log__msg">{{ l.message }}</text>
          <text class="log__time">{{ formatDateTime(l.create_time) }}</text>
        </view>
      </view>
    </wb-section>

    <view class="spacer" />

    <view class="footer-bar">
      <view class="btn btn--ghost btn--sm footer-btn" @click="goConfig">
        <text class="btn__text-ghost">重新编排</text>
      </view>
      <view class="btn btn--primary btn--sm footer-btn footer-btn--main" @click="goMatches">
        <text class="btn__text">进入赛程记分</text>
      </view>
    </view>
  </view>
</template>

<script>
import {
  getFairness,
  getDrawLogs,
  getTournamentById,
  getEntryPlayers,
  getGroupStandings,
  getMatches
} from '../../utils/api';
import { requireLogin, requireParam } from '../../utils/session';
import { formatLabel, formatDateTime } from '../../utils/dict';

export default {
  data() {
    return {
      id: '',
      isFresh: false,
      format: 'SINGLE_ELIM',
      t: null,
      fairness: null,
      logs: [],
      groups: [],
      slots: [],
      totalMatches: 0
    };
  },
  computed: {
    scoreColor() {
      const s = this.fairness ? this.fairness.score : 0;
      if (s >= 85) return '#15803d';
      if (s >= 70) return '#d97706';
      return '#dc2626';
    },
    scoreTitle() {
      const s = this.fairness ? this.fairness.score : 0;
      if (s >= 85) return '抽签质量优秀';
      if (s >= 70) return '抽签质量良好';
      if (s >= 55) return '抽签质量一般，建议调整种子或重新编排';
      return '抽签质量偏低，建议检查种子与单位分布';
    }
  },
  onShareAppMessage() {
    return { title: '抽签编排结果 · 公平性报告', path: '/pages/draw/result?id=' + this.id };
  },
  onLoad(options) {
    if (!requireLogin()) return;
    this.id = (options && options.id) || '';
    if (!requireParam(this.id, '赛事ID')) return;
    this.isFresh = !!(options && options.fresh);
    this.load();
  },
  methods: {
    formatLabel,
    formatDateTime,
    round2(v) {
      if (v == null) return '—';
      return Math.round(Number(v) * 100) / 100;
    },
    pct(v) {
      if (v == null) return '—';
      return Math.round(Number(v) * 100) + '%';
    },
    quarterName(i) {
      return '第 ' + (i + 1) + ' 区';
    },
    groupLetter(i) {
      return String.fromCharCode(65 + i);
    },
    async load() {
      try {
        const [t, f, lg, ms] = await Promise.all([
          getTournamentById(this.id),
          getFairness(this.id).catch(() => null),
          getDrawLogs(this.id).catch(() => []),
          getMatches(this.id).catch(() => [])
        ]);
        this.t = t;
        this.format = (t && t.format) || 'SINGLE_ELIM';
        this.fairness = f;
        this.logs = Array.isArray(lg) ? lg : [];
        const all = Array.isArray(ms) ? ms : [];
        this.totalMatches = all.filter((m) => m.status !== 'BYE').length;

        if (this.format === 'GROUP_KNOCKOUT') {
          try {
            const gs = await getGroupStandings(this.id);
            this.groups = Array.isArray(gs) ? gs : [];
          } catch (e) {
            this.groups = [];
          }
        } else {
          await this.buildSlots();
        }
      } catch (e) {
        /* 已提示 */
      }
    },
    /** 从抽签留痕的最后一份快照还原签位表，并补上选手姓名 */
    async buildSlots() {
      const snapLog = [...this.logs].reverse().find((l) => l.snapshot && l.snapshot.length);
      if (!snapLog) {
        this.slots = [];
        return;
      }
      const ids = String(snapLog.snapshot).split('|');
      let nameMap = {};
      try {
        const ps = await getEntryPlayers(this.id);
        (ps || []).forEach((p) => {
          nameMap[p.player_id] = {
            name: p.name,
            team: p.team,
            seed: !!p.is_seed,
            seedRank: p.seed_rank
          };
        });
      } catch (e) {
        nameMap = {};
      }
      this.slots = ids.map((v, i) => {
        const isBye = !v || v === '-' || v === 'BYE';
        const info = nameMap[v] || {};
        return {
          slot: i + 1,
          bye: isBye,
          name: isBye ? '轮空' : info.name || v,
          team: info.team || '',
          seed: !!info.seed,
          seedRank: info.seedRank || 0
        };
      });
    },
    goBracket() {
      uni.navigateTo({ url: '/pages/match/bracket?id=' + this.id });
    },
    goConfig() {
      uni.redirectTo({ url: '/pages/draw/config?id=' + this.id });
    },
    goMatches() {
      uni.redirectTo({ url: '/pages/match/list?id=' + this.id });
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
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  line-height: 1.4;
}

.hero__banner {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 12px;
  padding: 8px 10px;
  border-radius: var(--r-sm);
  background-color: rgba(255, 255, 255, 0.16);
}

.hero__banner-text {
  font-size: 12px;
  color: #fff;
}

.hero__metrics {
  display: flex;
  align-items: center;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}

.hm {
  flex: 1;
  text-align: center;
}

.hm__v {
  display: block;
  font-size: 19px;
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
  height: 22px;
  background-color: rgba(255, 255, 255, 0.2);
}

.block {
  margin-bottom: var(--sp-3);
}

.score {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
}

.score__ring {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  border: 5px solid var(--c-primary);
  display: flex;
  align-items: baseline;
  justify-content: center;
  flex-shrink: 0;
}

.score__num {
  font-size: 26px;
  line-height: 1;
  align-self: center;
}

.score__unit {
  font-size: 11px;
  color: var(--c-text-weak);
  align-self: flex-end;
  margin-bottom: 2px;
}

.score__title {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.score__bar {
  height: 7px;
  background-color: var(--c-muted);
  border-radius: 999px;
  overflow: hidden;
  margin-top: 8px;
}

.score__fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.4s ease-out;
}

.score__hint {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 6px;
}

.metric-block {
  margin-top: var(--sp-4);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-muted);
}

.metric-block__title {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-sub);
  margin-bottom: 10px;
}

.q-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.q {
  width: calc(50% - 4px);
  background-color: #f7f9fd;
  border-radius: var(--r-sm);
  padding: 9px 10px;
  box-sizing: border-box;
}

.q__label {
  display: block;
  font-size: 12px;
  color: var(--c-text-sub);
  font-weight: 600;
}

.q__seed {
  display: block;
  font-size: 13px;
  color: var(--c-primary);
  margin-top: 4px;
}

.q__level {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 1px;
}

.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 36px;
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
  font-weight: 600;
  color: var(--c-text);
}

.kv__v--good {
  color: var(--c-success);
}

.notes {
  margin-top: var(--sp-3);
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  background-color: #f7f9fd;
  border-radius: var(--r-sm);
  padding: 8px 10px;
}

.note__text {
  flex: 1;
  font-size: 12px;
  color: var(--c-text-sub);
  line-height: 1.55;
}

.group {
  margin-bottom: var(--sp-3);
}

.group:last-child {
  margin-bottom: 0;
}

.group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.group__name {
  font-size: 14px;
  font-weight: 700;
  color: var(--c-primary);
}

.group__players {
  background-color: #f7f9fd;
  border-radius: var(--r-sm);
  padding: 4px 10px;
}

.gplayer {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-height: 46px;
  border-bottom: 1px solid var(--c-muted);
}

.gplayer:last-child {
  border-bottom: none;
}

.gplayer__rank {
  width: 20px;
  font-size: 13px;
  color: var(--c-text-weak);
  text-align: center;
}

.gplayer__name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}

.gplayer__team {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 1px;
}

.slots {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.slot {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 8px 10px;
  border-radius: var(--r-sm);
  background-color: #f7f9fd;
  border: 1px solid transparent;
}

.slot--bye {
  background-color: #f2f4f8;
}

.slot--seed {
  border-color: #f0c88a;
  background-color: #fffdf8;
}

.slot__no {
  width: 22px;
  font-size: 12px;
  color: var(--c-text-weak);
  text-align: center;
}

.slot__name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}

.slot--bye .slot__name {
  color: var(--c-text-weak);
  font-weight: 500;
}

.slot__team {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 1px;
}

.log {
  display: flex;
  gap: var(--sp-3);
  padding: 7px 0;
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

.spacer {
  height: 76px;
}

.footer-btn {
  flex: 1;
}

.footer-btn--main {
  flex: 1.7;
}

.btn__text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.btn__text-ghost {
  color: var(--c-primary);
  font-size: 14px;
  font-weight: 600;
}
</style>
