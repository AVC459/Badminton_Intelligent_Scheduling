<template>
  <view class="container container--with-footer">
    <!-- 对阵头 -->
    <view class="versus">
      <view class="versus__side">
        <view class="avatar" :class="{ 'avatar--win': winnerId && winnerId === m.player_a_id }">
          <text class="avatar__text">{{ initial(m.player_a_name) }}</text>
        </view>
        <text class="versus__name t-ellipsis">{{ m.player_a_name || '待定' }}</text>
        <text class="versus__team t-ellipsis">{{ m.player_a_team || '—' }}</text>
      </view>

      <view class="versus__mid">
        <text class="versus__score t-num">{{ previewA }} : {{ previewB }}</text>
        <text class="versus__hint">{{ bestOf }} 局 {{ need }} 胜</text>
      </view>

      <view class="versus__side versus__side--right">
        <view class="avatar" :class="{ 'avatar--win': winnerId && winnerId === m.player_b_id }">
          <text class="avatar__text">{{ initial(m.player_b_name) }}</text>
        </view>
        <text class="versus__name t-ellipsis">{{ m.player_b_name || '待定' }}</text>
        <text class="versus__team t-ellipsis">{{ m.player_b_team || '—' }}</text>
      </view>
    </view>

    <!-- 场次信息 -->
    <view class="metacard">
      <view class="metacard__row">
        <wb-tag :text="m.stage_label || stageLabel(m.stage)" tone="neutral" size="sm" />
        <wb-tag v-if="m.group_name" :text="m.group_name + ' 组'" tone="info" size="sm" />
        <wb-tag v-if="m.round_name" :text="m.round_name" tone="neutral" size="sm" />
        <wb-tag :text="matchStatusLabel(m.status)" :tone="matchStatusTone(m.status)" size="sm" />
      </view>
    </view>

    <!-- 已有结果（只读提示） -->
    <view v-if="isFinished" class="done-banner">
      <wb-icon name="check" :size="14" color="#15803d" :stroke-width="2.6" />
      <text class="done-banner__text">
        本场已结束，{{ m.winner_name }} 获胜。重新保存将覆盖原比分并重算晋级。
      </text>
    </view>

    <!-- ============ 弃权模式 ============ -->
    <wb-section
      v-if="walkover"
      class="block"
      title="弃权处理"
      icon="alert"
      desc="指定退赛方后，系统直接判对方获胜，无需录入局分"
    >
      <view class="wo">
        <view
          v-for="o in walkoverOptions"
          :key="o.value"
          class="wo__opt"
          :class="{ 'wo__opt--active': retiredPlayerId === o.value }"
          @click="retiredPlayerId = o.value"
        >
          <view class="wo__radio">
            <view v-if="retiredPlayerId === o.value" class="wo__dot" />
          </view>
          <view class="grow">
            <text class="wo__name t-ellipsis">{{ o.player }}</text>
            <text class="wo__result">{{ o.result }}</text>
          </view>
        </view>
      </view>
      <text v-if="!retiredPlayerId" class="wo__hint">
        未选择时按规则默认判 A 方（{{ m.player_a_name || '待定' }}）获胜
      </text>
    </wb-section>

    <!-- ============ 逐局录入 ============ -->
    <wb-section
      v-else
      class="block"
      title="逐局录入"
      icon="score"
      :desc="'每局比分不能相同；胜方需赢下 ' + need + ' 局'"
    >
      <view v-for="(g, gi) in games" :key="gi" class="game">
        <view class="game__head">
          <text class="game__label">第 {{ gi + 1 }} 局</text>
          <wb-tag
            v-if="gameWinner(g) === 'A'"
            :text="shortName(m.player_a_name) + ' 胜'"
            tone="success"
            size="sm"
          />
          <wb-tag
            v-else-if="gameWinner(g) === 'B'"
            :text="shortName(m.player_b_name) + ' 胜'"
            tone="success"
            size="sm"
          />
          <wb-tag v-else-if="sameScore(g)" text="比分相同" tone="danger" size="sm" />
          <view v-if="games.length > 1" class="game__del" @click="removeGame(gi)">
            <wb-icon name="trash" :size="14" color="#dc2626" />
          </view>
        </view>

        <view class="game__inputs">
          <view class="num">
            <view class="num__btn" @click="bump(gi, 'a', -1)">
              <wb-icon name="minus" :size="14" color="#1e40af" :stroke-width="2.4" />
            </view>
            <input
              class="num__input t-num"
              type="number"
              :value="g.a"
              placeholder="0"
              placeholder-class="ph"
              @input="onInput(gi, 'a', $event)"
            />
            <view class="num__btn" @click="bump(gi, 'a', 1)">
              <wb-icon name="plus" :size="14" color="#1e40af" :stroke-width="2.4" />
            </view>
          </view>

          <text class="game__sep">:</text>

          <view class="num">
            <view class="num__btn" @click="bump(gi, 'b', -1)">
              <wb-icon name="minus" :size="14" color="#1e40af" :stroke-width="2.4" />
            </view>
            <input
              class="num__input t-num"
              type="number"
              :value="g.b"
              placeholder="0"
              placeholder-class="ph"
              @input="onInput(gi, 'b', $event)"
            />
            <view class="num__btn" @click="bump(gi, 'b', 1)">
              <wb-icon name="plus" :size="14" color="#1e40af" :stroke-width="2.4" />
            </view>
          </view>
        </view>
      </view>

      <view
        v-if="games.length < bestOf"
        class="add-game"
        @click="addGame"
      >
        <wb-icon name="plus" :size="14" color="#1e40af" :stroke-width="2.4" />
        <text class="add-game__text">添加第 {{ games.length + 1 }} 局</text>
      </view>

      <view class="quick">
        <text class="quick__title">常用整场比分</text>
        <view class="quick__list">
          <view
            v-for="q in quickScores"
            :key="q.text"
            class="quick__chip"
            @click="applyQuick(q)"
          >
            <text class="quick__chip-text">{{ q.text }}</text>
          </view>
        </view>
      </view>
    </wb-section>

    <!-- ============ 结果预览 ============ -->
    <view class="result" :class="resultClass">
      <view class="result__icon">
        <wb-icon :name="resultIcon" :size="17" color="#ffffff" :stroke-width="2.2" />
      </view>
      <view class="grow">
        <text class="result__title">{{ resultTitle }}</text>
        <text class="result__desc">{{ resultDesc }}</text>
      </view>
    </view>

    <!-- ============ 备注 ============ -->
    <wb-section class="block" title="备注" icon="edit" desc="可选，例如「第二局因伤暂停 5 分钟」">
      <textarea
        class="field__input field__textarea"
        v-model="remark"
        placeholder="填写本场说明，将显示在赛程与报告中"
        placeholder-class="ph"
        :maxlength="80"
      />
      <text class="counter">{{ (remark || '').length }} / 80</text>
    </wb-section>

    <view class="spacer" />

    <!-- 底部操作 -->
    <view class="footer-bar">
      <view class="btn btn--ghost btn--sm footer-btn" @click="toggleWalkover">
        <text class="btn__text-ghost">{{ walkover ? '改为比分录入' : '对方弃权' }}</text>
      </view>
      <view
        class="btn btn--primary btn--sm footer-btn footer-btn--main"
        :class="{ 'btn--disabled': submitting || !canSubmit }"
        @click="submit"
      >
        <text class="btn__text">{{ submitting ? '保存中…' : submitText }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { getMatchById, getTournamentById, saveScore } from '../../utils/api';
import { requireLogin, requireParam } from '../../utils/session';
import {
  matchStatusLabel,
  matchStatusTone,
  stageLabel
} from '../../utils/dict';

export default {
  data() {
    return {
      id: '',
      matchId: '',
      m: {},
      t: null,
      games: [{ a: '', b: '' }],
      walkover: false,
      retiredPlayerId: '',
      remark: '',
      submitting: false
    };
  },
  computed: {
    /**
     * 赛制局数：1 局定胜负 / 3 局 2 胜。
     *
     * 必须读赛事真实配置。之前写死成「5 就 5、否则 3」，
     * 1 局赛制被判成 3 局 2 胜 → need 变成 2，
     * 而 1 局的比赛最多只能录 1 局，比分永远无法提交。
     */
    bestOf() {
      const v = Number(this.t && this.t.best_of);
      return v === 1 ? 1 : 3;
    },
    need() {
      return Math.floor(this.bestOf / 2) + 1;
    },
    isFinished() {
      return this.m.status === 'DONE' || this.m.status === 'WALKOVER';
    },
    submitText() {
      return this.isFinished ? '保存更正' : '保存比分';
    },
    /** 已录入局胜数 */
    tally() {
      let a = 0;
      let b = 0;
      this.games.forEach((g) => {
        const w = this.gameWinner(g);
        if (w === 'A') a++;
        else if (w === 'B') b++;
      });
      return { a, b };
    },
    previewA() {
      if (!this.walkover) return this.tally.a;
      return this.winnerId === this.m.player_a_id ? this.need : 0;
    },
    previewB() {
      if (!this.walkover) return this.tally.b;
      return this.winnerId === this.m.player_b_id ? this.need : 0;
    },
    winnerId() {
      if (this.walkover) {
        if (!this.retiredPlayerId) return this.m.player_a_id;
        if (this.retiredPlayerId === this.m.player_a_id) return this.m.player_b_id;
        if (this.retiredPlayerId === this.m.player_b_id) return this.m.player_a_id;
        return this.m.player_a_id;
      }
      const t = this.tally;
      if (t.a >= this.need && t.a > t.b) return this.m.player_a_id;
      if (t.b >= this.need && t.b > t.a) return this.m.player_b_id;
      return '';
    },
    validGames() {
      return this.games.filter((g) => this.num(g.a) !== null && this.num(g.b) !== null);
    },
    resultState() {
      if (this.walkover) {
        return {
          tone: 'info',
          icon: 'alert',
          title: '弃权判定',
          desc:
            (this.retiredPlayerId
              ? this.retiredPlayerId === this.m.player_a_id
                ? (this.m.player_a_name || 'A 方') + ' 弃权，'
                : (this.m.player_b_name || 'B 方') + ' 弃权，'
              : '未指定弃权方，按规则判 ' + (this.m.player_a_name || 'A 方') + ' 获胜，') +
            '将直接按 ' + this.need + ' : 0 记录局分'
        };
      }
      const t = this.tally;
      const unpaired = this.games.length - this.validGames.length;
      if (!this.validGames.length) {
        return { tone: 'neutral', icon: 'info', title: '等待录入比分', desc: '填写第一局比分后自动判定' };
      }
      if (unpaired > 0) {
        return {
          tone: 'warning',
          icon: 'alert',
          title: '有 ' + unpaired + ' 局未填完整',
          desc: '未填完整的局不会计入，请补齐或删除后再保存'
        };
      }
      const hasSame = this.games.some((g) => this.sameScore(g));
      if (hasSame) {
        return { tone: 'danger', icon: 'close', title: '存在平分局', desc: '同一局的双方比分不能相同' };
      }
      if (t.a === t.b) {
        return { tone: 'warning', icon: 'alert', title: '局分打平 ' + t.a + ' : ' + t.b, desc: '需要分出胜负才能保存' };
      }
      if (t.a < this.need && t.b < this.need) {
        return {
          tone: 'info',
          icon: 'score',
          title: '总比分 ' + t.a + ' : ' + t.b,
          desc: '还需 ' + (this.need - Math.max(t.a, t.b)) + ' 局才能判定胜负'
        };
      }
      const name = t.a > t.b ? this.m.player_a_name : this.m.player_b_name;
      return {
        tone: 'success',
        icon: 'check',
        title: name + ' 获胜 ' + t.a + ' : ' + t.b,
        desc: '保存后自动完成晋级，并更新赛事进度与排名'
      };
    },
    resultClass() {
      return 'result--' + this.resultState.tone;
    },
    resultIcon() {
      return this.resultState.icon;
    },
    resultTitle() {
      return this.resultState.title;
    },
    resultDesc() {
      return this.resultState.desc;
    },
    walkoverOptions() {
      const a = this.m.player_a_name || 'A 方';
      const b = this.m.player_b_name || 'B 方';
      return [
        {
          value: this.m.player_b_id,
          player: b,
          result: 'B 方弃权 → ' + a + ' 获胜'
        },
        {
          value: this.m.player_a_id,
          player: a,
          result: 'A 方弃权 → ' + b + ' 获胜'
        }
      ];
    },
    quickScores() {
      if (this.bestOf === 1) {
        return [
          { text: '21:15', games: [{ a: 21, b: 15 }] },
          { text: '21:19', games: [{ a: 21, b: 19 }] },
          { text: '19:21', games: [{ a: 19, b: 21 }] }
        ];
      }
      return [
        { text: '21:15 / 21:18', games: [{ a: 21, b: 15 }, { a: 21, b: 18 }] },
        { text: '21:19 / 19:21 / 21:17', games: [{ a: 21, b: 19 }, { a: 19, b: 21 }, { a: 21, b: 17 }] },
        { text: '先失一局 18:21 / 21:15 / 21:19', games: [{ a: 18, b: 21 }, { a: 21, b: 15 }, { a: 21, b: 19 }] }
      ];
    },
    canSubmit() {
      if (!this.m.match_id) return false;
      if (this.walkover) return true;
      const t = this.tally;
      if (this.games.length !== this.validGames.length) return false;
      if (this.games.some((g) => this.sameScore(g))) return false;
      if (t.a === t.b) return false;
      return (t.a >= this.need || t.b >= this.need) && Math.max(t.a, t.b) > Math.min(t.a, t.b);
    }
  },
  onShareAppMessage() {
    return {
      title: '羽毛球比分录入',
      path: '/pages/match/score?id=' + this.id + '&matchId=' + this.matchId
    };
  },
  onLoad(options) {
    if (!requireLogin()) return;
    this.id = (options && options.id) || '';
    this.matchId = (options && options.matchId) || '';
    if (!requireParam(this.matchId, '场次ID')) return;
    this.load();
  },
  methods: {
    matchStatusLabel,
    matchStatusTone,
    stageLabel,
    initial(name) {
      return name ? String(name).slice(0, 1) : '?';
    },
    /**
     * 选手简称（用于「XX 胜」这类紧凑标签）。
     *
     * 双打组合名形如「张三/李四」，按字数硬截会得到「张三/」这种半截名字，
     * 所以先按分隔符取主选手，再对超长名字截断。
     */
    shortName(name) {
      if (!name) return '待定';
      const s = String(name);
      const main = s.split(/[/、]/)[0].trim() || s;
      return main.length > 4 ? main.slice(0, 4) : main;
    },
    /** 空串返回 null，便于区分「未填」与「0 分」 */
    num(v) {
      if (v === '' || v === null || v === undefined) return null;
      const n = Number(v);
      return isNaN(n) ? null : n;
    },
    gameWinner(g) {
      const a = this.num(g.a);
      const b = this.num(g.b);
      if (a === null || b === null || a === b) return '';
      return a > b ? 'A' : 'B';
    },
    sameScore(g) {
      const a = this.num(g.a);
      const b = this.num(g.b);
      return a !== null && b !== null && a === b;
    },
    onInput(gi, side, e) {
      const raw = (e && e.detail ? e.detail.value : '') || '';
      // 只保留数字，避免 type=number 在部分机型上仍可输入符号
      const clean = String(raw).replace(/[^0-9]/g, '').slice(0, 3);
      this.$set(this.games[gi], side, clean);
      this.maybeAppendRow();
    },
    bump(gi, side, delta) {
      const cur = this.num(this.games[gi][side]);
      const next = Math.max(0, Math.min(99, (cur === null ? 0 : cur) + delta));
      this.$set(this.games[gi], side, String(next));
      this.maybeAppendRow();
    },
    /** 当前行填完后自动追加下一行，减少一次点击 */
    maybeAppendRow() {
      const last = this.games[this.games.length - 1];
      if (this.games.length >= this.bestOf) return;
      if (this.num(last.a) === null || this.num(last.b) === null) return;
      const t = this.tally;
      if (t.a >= this.need || t.b >= this.need) return;
      this.games.push({ a: '', b: '' });
    },
    addGame() {
      if (this.games.length >= this.bestOf) return;
      this.games.push({ a: '', b: '' });
    },
    removeGame(gi) {
      if (this.games.length <= 1) return;
      this.games.splice(gi, 1);
    },
    applyQuick(q) {
      this.games = q.games.map((g) => ({ a: String(g.a), b: String(g.b) }));
    },
    toggleWalkover() {
      this.walkover = !this.walkover;
      if (this.walkover) this.retiredPlayerId = '';
    },
    async load() {
      try {
        const [match, t] = await Promise.all([
          getMatchById(this.matchId),
          this.id ? getTournamentById(this.id) : Promise.resolve(null)
        ]);
        this.m = match || {};
        this.t = t || this.t;
        if (t) uni.setNavigationBarTitle({ title: (t.title || '') + ' · 记分' });

        // 回填已有比分，便于更正
        if (this.m.games && this.m.games.length) {
          this.games = this.m.games.map((g) => ({
            a: g.a === null || g.a === undefined ? '' : String(g.a),
            b: g.b === null || g.b === undefined ? '' : String(g.b)
          }));
        }
        if (this.m.status === 'WALKOVER') {
          this.walkover = true;
          this.retiredPlayerId = '';
        }
        if (this.m.remark) this.remark = this.m.remark;
      } catch (e) {
        /* 已提示 */
      }
    },
    submit() {
      if (this.submitting) return;
      if (!this.canSubmit) {
        uni.showToast({ title: '请先完成比分录入', icon: 'none' });
        return;
      }
      const payload = {
        matchId: this.matchId,
        walkover: this.walkover,
        retiredPlayerId: this.walkover ? this.retiredPlayerId || null : null,
        remark: this.remark || '',
        games: this.walkover
          ? []
          : this.games.map((g) => ({ a: this.num(g.a), b: this.num(g.b) }))
      };

      const doSave = () => {
        this.submitting = true;
        saveScore(payload)
          .then((res) => {
            const name = (res && res.winner_name) || '';
            uni.showToast({ title: name ? name + ' 获胜' : '保存成功', icon: 'success', duration: 1200 });
            setTimeout(() => {
              const pages = getCurrentPages();
              if (pages.length > 1) uni.navigateBack();
              else uni.redirectTo({ url: '/pages/match/list?id=' + this.id });
            }, 900);
          })
          .catch(() => {
            /* 已提示 */
          })
          .finally(() => {
            this.submitting = false;
          });
      };

      if (this.isFinished) {
        uni.showModal({
          title: '覆盖已有比分',
          content: '本场已有比分记录，保存后将重新计算结果并重算晋级关系。确认继续？',
          confirmText: '覆盖保存',
          success: (r) => {
            if (r.confirm) doSave();
          }
        });
        return;
      }
      doSave();
    }
  }
};
</script>

<style scoped>
.ph {
  color: #b3bfd2;
}

/* ---------- 对阵头 ---------- */
.versus {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
  background: linear-gradient(150deg, #1e40af, #2563eb);
  border-radius: var(--r-md);
  padding: 18px 14px;
  box-shadow: var(--sh-2);
  margin-bottom: var(--sp-3);
}

.versus__side {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.versus__side--right {
  text-align: center;
}

.avatar {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.32);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}

.avatar--win {
  background-color: #d97706;
  border-color: #f0b866;
}

.avatar__text {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.versus__name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  max-width: 100%;
}

.versus__team {
  display: block;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.72);
  margin-top: 2px;
  max-width: 100%;
}

.versus__mid {
  width: 92px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 12px;
  flex-shrink: 0;
}

.versus__score {
  font-size: 21px;
  color: #fff;
}

.versus__hint {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 4px;
}

/* ---------- 场次信息 ---------- */
.metacard {
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 11px var(--sp-3);
  box-shadow: var(--sh-1);
  margin-bottom: var(--sp-3);
}

.metacard__row {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}

.metacard__row--meta {
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px solid var(--c-muted);
}

.metacard__meta {
  font-size: 12px;
  color: var(--c-text-weak);
  margin-right: 6px;
}

/* ---------- 已完成提示 ---------- */
.done-banner {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  background-color: var(--c-success-soft);
  border-radius: var(--r-sm);
  margin-bottom: var(--sp-3);
}

.done-banner__text {
  flex: 1;
  font-size: 12px;
  color: var(--c-success);
  line-height: 1.55;
}

.block {
  margin-bottom: var(--sp-3);
}

/* ---------- 逐局录入 ---------- */
.game {
  background-color: #f7f9fd;
  border-radius: var(--r-sm);
  padding: 10px 12px;
  margin-bottom: 9px;
}

.game__head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 9px;
}

.game__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-sub);
  flex: 1;
}

.game__del {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background-color: #fdeaea;
}

.game__inputs {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.num {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 4px;
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  padding: 3px;
  box-sizing: border-box;
}

.num__btn {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background-color: var(--c-muted);
  flex-shrink: 0;
}

.num__input {
  flex: 1;
  min-width: 0;
  height: 38px;
  text-align: center;
  font-size: 18px;
  color: var(--c-text);
}

.game__sep {
  font-size: 17px;
  color: var(--c-text-weak);
  flex-shrink: 0;
}

.add-game {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 44px;
  border: 1px dashed var(--c-border);
  border-radius: var(--r-sm);
  background-color: #fbfcfe;
}

.add-game__text {
  font-size: 13px;
  color: var(--c-primary);
  font-weight: 600;
}

.quick {
  margin-top: var(--sp-4);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-muted);
}

.quick__title {
  display: block;
  font-size: 12px;
  color: var(--c-text-weak);
  margin-bottom: 8px;
}

.quick__list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.quick__chip {
  min-height: 40px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  background-color: #f2f6fd;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
}

.quick__chip-text {
  font-size: 12px;
  color: var(--c-text-sub);
  font-variant-numeric: tabular-nums;
}

/* ---------- 结果预览 ---------- */
.result {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 13px var(--sp-3);
  border-radius: var(--r-md);
  box-shadow: var(--sh-1);
  margin-bottom: var(--sp-3);
}

.result--neutral {
  background-color: #f2f4f8;
}

.result--info {
  background-color: var(--c-info-soft);
}

.result--warning {
  background-color: var(--c-warning-soft);
}

.result--danger {
  background-color: var(--c-danger-soft);
}

.result--success {
  background-color: var(--c-success-soft);
}

.result__icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background-color: #1e40af;
}

.result--neutral .result__icon {
  background-color: #8b98b0;
}

.result--warning .result__icon {
  background-color: #b45309;
}

.result--danger .result__icon {
  background-color: #dc2626;
}

.result--success .result__icon {
  background-color: #15803d;
}

.result__title {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: var(--c-text);
}

.result__desc {
  display: block;
  font-size: 12px;
  color: var(--c-text-sub);
  margin-top: 3px;
  line-height: 1.5;
}

/* ---------- 弃权 ---------- */
.wo {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.wo__opt {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  min-height: 56px;
  padding: 10px 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background-color: #fbfcfe;
}

.wo__opt--active {
  border-color: var(--c-primary);
  background-color: var(--c-primary-soft);
}

.wo__radio {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.5px solid #c3d2ea;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background-color: #fff;
}

.wo__opt--active .wo__radio {
  border-color: var(--c-primary);
}

.wo__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: var(--c-primary);
}

.wo__name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.wo__result {
  display: block;
  font-size: 12px;
  color: var(--c-text-weak);
  margin-top: 2px;
}

.wo__hint {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 10px;
  line-height: 1.55;
}

/* ---------- 备注 ---------- */
.counter {
  display: block;
  text-align: right;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 5px;
  font-variant-numeric: tabular-nums;
}

.spacer {
  height: 8px;
}

.footer-btn {
  flex: 1;
}

.footer-btn--main {
  flex: 1.5;
}

.btn__text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.btn__text-ghost {
  color: var(--c-primary);
  font-size: 13px;
  font-weight: 600;
}
</style>
