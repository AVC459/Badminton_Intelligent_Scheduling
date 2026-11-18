<template>
  <view class="container container--with-footer">
    <!-- 进度 -->
    <view class="topbar">
      <view class="topbar__left">
        <text class="topbar__title t-ellipsis">{{ t ? t.title : '赛程' }}</text>
        <text class="topbar__sub">{{ doneCount }} / {{ playableCount }} 场已完成</text>
      </view>
      <wb-tag
        v-if="t"
        :text="tournamentStatusLabel(t.status)"
        :tone="tournamentStatusTone(t.status)"
      />
    </view>

    <view class="progress">
      <view class="progress__fill" :style="{ width: progressPct + '%' }" />
    </view>

    <!-- 交叉淘汰待生成提示 -->
    <view v-if="canGenerateKnockout" class="gen">
      <view class="gen__text">
        <wb-icon name="sparkle" :size="14" color="#d97706" />
        <view class="grow">
          <text class="gen__title">小组赛已全部结束</text>
          <text class="gen__desc">可以生成交叉淘汰赛程，各组出线者将进入淘汰阶段</text>
        </view>
      </view>
      <view class="chip chip--accent" @click="doGenerateKnockout">
        <wb-icon name="shuffle" :size="13" color="#ffffff" />
        <text class="chip__text">生成淘汰赛</text>
      </view>
    </view>

    <!-- 筛选 -->
    <view class="filters">
      <view
        v-for="f in filters"
        :key="f.value"
        class="fchip"
        :class="{ 'fchip--active': filter === f.value }"
        @click="filter = f.value"
      >
        {{ f.label }}
        <text v-if="f.count != null" class="fchip__count">{{ f.count }}</text>
      </view>
    </view>

    <!-- 骨架 -->
    <view v-if="loading" class="skeleton-list">
      <view v-for="i in 4" :key="i" class="skeleton skeleton--row" />
    </view>

    <!-- 空状态 -->
    <wb-empty
      v-else-if="!filtered.length"
      icon="grid"
      :title="matches.length ? '当前筛选下没有场次' : '还没有生成赛程'"
      :desc="matches.length ? '切换上方筛选查看其它场次' : '完成抽签编排后即可生成赛程'"
      :action-text="matches.length ? '' : '去抽签编排'"
      @action="goConfig"
    />

    <!-- 场次列表 -->
    <view v-else>
      <view
        v-for="m in filtered"
        :key="m.match_id"
        class="mcard"
        :class="{ 'mcard--done': m.status === 'DONE' || m.status === 'WALKOVER' }"
        @click="openMatch(m)"
      >
        <view class="mcard__head">
          <view class="mcard__tags">
            <wb-tag :text="stageShort(m.stage)" tone="neutral" size="sm" />
            <wb-tag v-if="m.group_name" :text="m.group_name + ' 组'" tone="info" size="sm" />
            <wb-tag :text="m.round_name" tone="neutral" size="sm" />
          </view>
          <wb-tag :text="matchStatusLabel(m.status)" :tone="matchStatusTone(m.status)" size="sm" />
        </view>

        <view class="side">
          <view class="player" :class="{ 'player--win': m.winner_id && m.winner_id === m.player_a_id }">
            <text class="player__name t-ellipsis">{{ m.player_a_name || '待定' }}</text>
            <text v-if="m.player_a_team" class="player__team t-ellipsis">{{ m.player_a_team }}</text>
          </view>
          <view class="score">
            <text class="score__a t-num" :class="{ 'score__win': m.winner_id && m.winner_id === m.player_a_id }">
              {{ m.games_a || 0 }}
            </text>
            <text class="score__sep">:</text>
            <text class="score__b t-num" :class="{ 'score__win': m.winner_id && m.winner_id === m.player_b_id }">
              {{ m.games_b || 0 }}
            </text>
          </view>
          <view class="player player--right" :class="{ 'player--win': m.winner_id && m.winner_id === m.player_b_id }">
            <text class="player__name t-ellipsis">{{ m.player_b_name || '待定' }}</text>
            <text v-if="m.player_b_team" class="player__team t-ellipsis">{{ m.player_b_team }}</text>
          </view>
        </view>

        <!-- 各局比分 -->
        <view v-if="m.games && m.games.length && m.status === 'DONE'" class="games">
          <text v-for="(g, i) in m.games" :key="i" class="games__item t-num">
            第{{ i + 1 }}局 {{ g.a }}:{{ g.b }}
          </text>
        </view>

        <view class="mcard__foot">
          <view class="meta">
            <text class="meta__text">第 {{ m.round }} 轮 · 第 {{ m.match_order }} 场</text>
          </view>
          <view v-if="m.status === 'READY'" class="meta__cta">
            <text class="meta__cta-text">去记分</text>
            <wb-icon name="chevronRight" :size="13" color="#1e40af" />
          </view>
          <text v-else-if="m.remark" class="meta__remark t-ellipsis">{{ m.remark }}</text>
        </view>
      </view>
    </view>

    <view class="footer-bar">
      <view class="btn btn--ghost btn--sm footer-btn" @click="goBracket">
        <text class="btn__text-ghost">对阵与积分</text>
      </view>
      <view class="btn btn--primary btn--sm footer-btn footer-btn--main" @click="goNext">
        <text class="btn__text">{{ nextMatch ? '下一场记分' : '全部已完成' }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import {
  getMatches,
  getTournamentById,
  generateKnockout
} from '../../utils/api';
import { requireLogin, requireParam } from '../../utils/session';
import {
  matchStatusLabel,
  matchStatusTone,
  stageShort,
  tournamentStatusLabel,
  tournamentStatusTone
} from '../../utils/dict';

export default {
  data() {
    return {
      id: '',
      t: null,
      matches: [],
      loading: true,
      filter: 'todo'
    };
  },
  computed: {
    playable() {
      return this.matches.filter((m) => m.status !== 'BYE');
    },
    playableCount() {
      return this.playable.length;
    },
    doneCount() {
      return this.playable.filter((m) => m.status === 'DONE' || m.status === 'WALKOVER').length;
    },
    progressPct() {
      if (!this.playableCount) return 0;
      return Math.round((this.doneCount * 1000) / this.playableCount) / 10;
    },
    filters() {
      const g = this.matches.filter((m) => m.stage === 'GROUP').length;
      const k = this.matches.filter((m) => m.stage === 'KNOCKOUT' || m.stage === 'THIRD_PLACE').length;
      const todo = this.matches.filter((m) => m.status === 'READY' || m.status === 'PENDING').length;
      return [
        { value: 'todo', label: '待比赛', count: todo },
        { value: 'group', label: '小组赛', count: g },
        { value: 'knockout', label: '淘汰赛', count: k },
        { value: 'done', label: '已完成', count: this.doneCount },
        { value: 'all', label: '全部', count: this.matches.length }
      ];
    },
    filtered() {
      const f = this.filter;
      const list = this.matches.slice();
      let out = list;
      if (f === 'todo') out = list.filter((m) => m.status === 'READY' || m.status === 'PENDING');
      else if (f === 'group') out = list.filter((m) => m.stage === 'GROUP');
      else if (f === 'knockout') out = list.filter((m) => m.stage === 'KNOCKOUT' || m.stage === 'THIRD_PLACE');
      else if (f === 'done') out = list.filter((m) => m.status === 'DONE' || m.status === 'WALKOVER');
      // 按小组、轮次、场次编号排序，贴近赛场实际推进顺序
      out.sort((a, b) => {
        if ((a.group_no || 0) !== (b.group_no || 0)) return (a.group_no || 0) - (b.group_no || 0);
        if ((a.round || 0) !== (b.round || 0)) return (a.round || 0) - (b.round || 0);
        return (a.match_order || 0) - (b.match_order || 0);
      });
      return out;
    },
    nextMatch() {
      return this.filtered.find((m) => m.status === 'READY');
    },
    canGenerateKnockout() {
      if (!this.t || this.t.format !== 'GROUP_KNOCKOUT') return false;
      const g = this.matches.filter((m) => m.stage === 'GROUP');
      if (!g.length) return false;
      const hasKnockout = this.matches.some(
        (m) => m.stage === 'KNOCKOUT' || m.stage === 'THIRD_PLACE'
      );
      if (hasKnockout) return false;
      return g.every((m) => m.status === 'DONE' || m.status === 'WALKOVER' || m.status === 'BYE');
    }
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
    matchStatusLabel,
    matchStatusTone,
    stageShort,
    tournamentStatusLabel,
    tournamentStatusTone,
    async load() {
      this.loading = true;
      try {
        const [ms, t] = await Promise.all([getMatches(this.id), getTournamentById(this.id)]);
        this.matches = Array.isArray(ms) ? ms : [];
        this.t = t || null;
        if (t) uni.setNavigationBarTitle({ title: '赛程 · ' + (t.title || '') });
        // 有待比赛时默认看「待比赛」，全部打完自动切到「已完成」
        const todo = this.matches.filter((m) => m.status === 'READY' || m.status === 'PENDING').length;
        if (!todo && this.filter === 'todo') this.filter = 'done';
        if (todo && this.filter === 'done' && !this.matches.some((m) => m.status === 'DONE')) {
          this.filter = 'todo';
        }
      } catch (e) {
        /* 已提示 */
      } finally {
        this.loading = false;
      }
    },
    openMatch(m) {
      if (m.status === 'BYE') {
        uni.showToast({ title: '轮空场次无需记分', icon: 'none' });
        return;
      }
      if (m.status === 'DONE' || m.status === 'WALKOVER') {
        uni.navigateTo({ url: '/pages/match/score?id=' + this.id + '&matchId=' + m.match_id });
        return;
      }
      if (!m.player_a_id || !m.player_b_id) {
        uni.showToast({ title: '双方选手尚未确定', icon: 'none' });
        return;
      }
      uni.navigateTo({ url: '/pages/match/score?id=' + this.id + '&matchId=' + m.match_id });
    },
    goNext() {
      if (!this.nextMatch) {
        uni.showToast({ title: '全部场次已完成', icon: 'none' });
        return;
      }
      this.openMatch(this.nextMatch);
    },
    goConfig() {
      uni.redirectTo({ url: '/pages/draw/config?id=' + this.id });
    },
    goBracket() {
      uni.navigateTo({ url: '/pages/match/bracket?id=' + this.id });
    },
    doGenerateKnockout() {
      uni.showModal({
        title: '生成交叉淘汰赛程',
        content: '将根据各组出线名次生成交叉淘汰对阵。首轮按出线名次交叉配对（如 A1-B1），同组出线选手尽量避免过早相遇。',
        success: async (r) => {
          if (!r.confirm) return;
          try {
            const res = await generateKnockout(this.id);
            uni.showToast({
              title: '已生成 ' + ((res && res.match_count) || 0) + ' 场淘汰赛',
              icon: 'success'
            });
            this.load();
          } catch (e) {
            /* 已提示 */
          }
        }
      });
    }
  },
  onPullDownRefresh() {
    // 无论成功失败都要收起下拉动画
    const done = () => uni.stopPullDownRefresh();
    this.load().then(done, done);
  },
  onShareAppMessage() {
    return { title: '羽毛球赛程与比分', path: '/pages/match/list?id=' + this.id };
  }
};
</script>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

.topbar__left {
  flex: 1;
  min-width: 0;
}

.topbar__title {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: var(--c-text);
}

.topbar__sub {
  display: block;
  font-size: 12px;
  color: var(--c-text-weak);
  margin-top: 3px;
}

.progress {
  height: 7px;
  background-color: var(--c-muted);
  border-radius: 999px;
  overflow: hidden;
  margin: var(--sp-3) 0;
}

.progress__fill {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #1e40af);
  border-radius: 999px;
  transition: width 0.35s ease-out;
}

.gen {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  padding: 11px;
  background-color: var(--c-accent-soft);
  border: 1px solid #f0d9b0;
  border-radius: var(--r-md);
  margin-bottom: var(--sp-3);
}

.gen__text {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  flex: 1;
  min-width: 0;
}

.gen__title {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-warning);
}

.gen__desc {
  display: block;
  font-size: 11px;
  color: #9a7434;
  margin-top: 2px;
  line-height: 1.5;
}

.filters {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
  margin-bottom: var(--sp-2);
}

.fchip {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 34px;
  padding: 0 13px;
  border-radius: 999px;
  border: 1px solid var(--c-border);
  background-color: var(--c-surface);
  font-size: 13px;
  color: var(--c-text-sub);
  white-space: nowrap;
  flex-shrink: 0;
}

.fchip--active {
  background-color: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
  font-weight: 600;
}

.fchip__count {
  font-size: 11px;
  opacity: 0.75;
  font-variant-numeric: tabular-nums;
}

.mcard {
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: var(--sp-3);
  box-shadow: var(--sh-1);
  margin-bottom: var(--sp-2);
}

.mcard--done {
  background-color: #fbfcfe;
}

.mcard__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-bottom: 10px;
}

.mcard__tags {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}

.side {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.player {
  flex: 1;
  min-width: 0;
}

.player--right {
  text-align: right;
}

.player__name {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--c-text-sub);
}

.player--win .player__name {
  color: var(--c-text);
  font-weight: 700;
}

.player__team {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 2px;
}

.score {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 6px;
  flex-shrink: 0;
}

.score__a,
.score__b {
  font-size: 19px;
  color: var(--c-text-weak);
  min-width: 20px;
  text-align: center;
}

.score__sep {
  font-size: 15px;
  color: var(--c-border);
}

.score__win {
  color: var(--c-primary);
}

.games {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.games__item {
  font-size: 11px;
  color: var(--c-text-sub);
  background-color: #f2f5fa;
  border-radius: 6px;
  padding: 3px 7px;
}

.mcard__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-top: 11px;
  padding-top: 10px;
  border-top: 1px solid var(--c-muted);
}

.meta {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.meta__text {
  font-size: 11px;
  color: var(--c-text-weak);
  margin-right: 6px;
}

.meta__cta {
  display: flex;
  align-items: center;
  gap: 1px;
}

.meta__cta-text {
  font-size: 12px;
  color: var(--c-primary);
  font-weight: 600;
}

.meta__remark {
  font-size: 11px;
  color: var(--c-text-weak);
  max-width: 45%;
}

.chip {
  display: flex;
  align-items: center;
  gap: 5px;
  min-height: 34px;
  padding: 0 13px;
  border-radius: 999px;
  flex-shrink: 0;
}

.chip--accent {
  background-color: var(--c-accent);
}

.chip__text {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skeleton--row {
  height: 112px;
}

.footer-btn {
  flex: 1;
}

.footer-btn--main {
  flex: 1.6;
}

.btn__text {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.btn--gap {
  margin-left: 4px;
}

.btn__text-ghost {
  color: var(--c-primary);
  font-size: 14px;
  font-weight: 600;
}
</style>
