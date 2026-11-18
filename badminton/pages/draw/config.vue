<template>
  <view class="container container--with-footer">
    <!-- 参赛规模 -->
    <wb-section class="block" title="参赛规模" icon="users">
      <view class="roster">
        <view class="roster__main">
          <text class="roster__num t-num">{{ players.length }}</text>
          <text class="roster__unit">人已入选参赛</text>
        </view>
        <wb-tag :text="formatLabel(format)" tone="info" />
      </view>

      <view v-if="players.length < 2" class="notice notice--danger">
        <wb-icon name="alert" :size="14" color="#dc2626" />
        <text class="notice__text notice__text--danger">
          参赛人数不足 2 人，请先到「参赛名单」中从选手库选入至少 2 名选手
        </text>
      </view>

      <!-- 试算结果：实时反馈将要生成的规模 -->
      <view v-else-if="preview" class="preview">
        <view class="preview__item">
          <text class="preview__value t-num">{{ preview.bracketSize || '—' }}</text>
          <text class="preview__label">签位</text>
        </view>
        <view class="preview__item">
          <text class="preview__value t-num">{{ preview.byeCount || 0 }}</text>
          <text class="preview__label">轮空</text>
        </view>
        <view class="preview__item" v-if="preview.groupCount">
          <text class="preview__value t-num">{{ preview.groupCount }}</text>
          <text class="preview__label">小组</text>
        </view>
        <view class="preview__item">
          <text class="preview__value t-num">{{ preview.matchCount || 0 }}</text>
          <text class="preview__label">预计场次</text>
        </view>
      </view>
    </wb-section>

    <!-- 抽签参数 -->
    <wb-section class="block" title="抽签参数" icon="shuffle">
      <view class="stepper-row">
        <view class="grow">
          <text class="switch-row__title">种子数量</text>
          <text class="switch-row__desc">种子按蛇形落位，均匀分布到各 1/4 区</text>
        </view>
        <view class="stepper">
          <view class="stepper__btn" @click="step('seedCount', -1, 0, 16)">
            <wb-icon name="minus" :size="16" color="#1e40af" :stroke-width="2.4" />
          </view>
          <text class="stepper__value">{{ cfg.seedCount }}</text>
          <view class="stepper__btn" @click="step('seedCount', 1, 0, 16)">
            <wb-icon name="plus" :size="16" color="#1e40af" :stroke-width="2.4" />
          </view>
        </view>
      </view>

      <view class="divider" />

      <view class="switch-row">
        <view class="switch-row__text">
          <text class="switch-row__title">同单位规避</text>
          <text class="switch-row__desc">发生死签时自动换位修正，尽可能让同单位选手晚相遇</text>
        </view>
        <switch
          :checked="cfg.avoidSameTeam"
          color="#1e40af"
          data-key="avoidSameTeam"
          @change="onCfgSwitch"
        />
      </view>

      <view class="switch-row">
        <view class="switch-row__text">
          <text class="switch-row__title">设置季军赛</text>
          <text class="switch-row__desc">半决赛负方再赛一场决出第三名</text>
        </view>
        <switch
          :checked="cfg.thirdPlace"
          color="#1e40af"
          data-key="thirdPlace"
          @change="onCfgSwitch"
        />
      </view>

      <block v-if="format === 'GROUP_KNOCKOUT'">
        <view class="divider" />
        <view class="stepper-row">
          <view class="grow">
            <text class="switch-row__title">小组数量</text>
            <text class="switch-row__desc">0 为自动（按人数建议，每组至少 3 人）</text>
          </view>
          <view class="stepper">
            <view class="stepper__btn" @click="step('groupCount', -1, 0, 16)">
              <wb-icon name="minus" :size="16" color="#1e40af" :stroke-width="2.4" />
            </view>
            <text class="stepper__value">{{ cfg.groupCount || '自动' }}</text>
            <view class="stepper__btn" @click="step('groupCount', 1, 0, 16)">
              <wb-icon name="plus" :size="16" color="#1e40af" :stroke-width="2.4" />
            </view>
          </view>
        </view>
        <view class="stepper-row">
          <view class="grow">
            <text class="switch-row__title">每组出线人数</text>
            <text class="switch-row__desc">出线者按交叉对阵进入淘汰赛</text>
          </view>
          <view class="stepper">
            <view class="stepper__btn" @click="step('groupAdvance', -1, 1, 4)">
              <wb-icon name="minus" :size="16" color="#1e40af" :stroke-width="2.4" />
            </view>
            <text class="stepper__value">{{ cfg.groupAdvance }}</text>
            <view class="stepper__btn" @click="step('groupAdvance', 1, 1, 4)">
              <wb-icon name="plus" :size="16" color="#1e40af" :stroke-width="2.4" />
            </view>
          </view>
        </view>
      </block>

      <view class="divider" />

      <view class="stepper-row" style="min-height: 48px">
        <view class="grow">
          <text class="switch-row__title">随机种子</text>
          <text class="switch-row__desc">固定后可复现同一次抽签结果，便于审计</text>
        </view>
        <input
          class="seed-input"
          type="number"
          v-model="randomSeedText"
          placeholder="留空随机"
          placeholder-class="ph"
        />
      </view>
    </wb-section>

    <!-- 局分 -->
    <wb-section class="block" title="局分规则" icon="target">
      <view class="field">
        <text class="field__label">赛制局数</text>
        <view class="segment">
          <view
            v-for="b in [1, 3]"
            :key="b"
            class="segment__item"
            :class="{ 'segment__item--active': cfg.bestOf === b }"
            @click="cfg.bestOf = b"
          >
            {{ b }} 局
          </view>
        </view>
      </view>
      <view class="stepper-row" style="min-height: 48px">
        <view class="grow">
          <text class="switch-row__title">每局分数 / 封顶分</text>
          <text class="switch-row__desc">标准为 21 分，20 平后需净胜 2 分，30 分封顶</text>
        </view>
        <view class="dual">
          <input class="dual__input" type="number" v-model.number="cfg.pointsPerGame" />
          <text class="dual__sep">/</text>
          <input class="dual__input" type="number" v-model.number="cfg.pointsCap" />
        </view>
      </view>
    </wb-section>

    <view class="tip">
      <wb-icon name="info" :size="13" color="#8b98b0" />
      <text class="tip__text">
        执行抽签后将生成正式赛程并落库。如需调整，可在赛事详情中「撤销抽签」后重排。
      </text>
    </view>

    <view class="footer-bar">
      <view class="btn btn--ghost btn--sm footer-btn" @click="goResult">
        <text class="btn__text-ghost">查看结果</text>
      </view>
      <view
        class="btn btn--primary btn--sm footer-btn footer-btn--main"
        :class="{ 'btn--disabled': submitting || players.length < 2 }"
        @click="submit"
      >
        <text class="btn__text">{{ submitting ? '编排中…' : '执行抽签编排' }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import {
  getEntryPlayers,
  getTournamentById,
  previewDraw,
  executeDraw
} from '../../utils/api';
import { requireLogin, requireParam } from '../../utils/session';
import { formatLabel } from '../../utils/dict';

export default {
  data() {
    return {
      id: '',
      format: 'SINGLE_ELIM',
      players: [],
      preview: null,
      submitting: false,
      randomSeedText: '',
      cfg: {
        seedCount: 4,
        avoidSameTeam: true,
        thirdPlace: true,
        groupCount: 0,
        groupAdvance: 2,
        bestOf: 3,
        pointsPerGame: 21,
        pointsCap: 30
      }
    };
  },
  onShareAppMessage() {
    return { title: '羽毛球赛事 · 抽签编排', path: '/pages/draw/config?id=' + this.id };
  },
  onLoad(options) {
    if (!requireLogin()) return;
    this.id = (options && options.id) || '';
    if (!requireParam(this.id, '赛事ID')) return;
    this.load();
  },
  methods: {
    formatLabel,
    async load() {
      try {
        const [t, ps] = await Promise.all([
          getTournamentById(this.id),
          getEntryPlayers(this.id)
        ]);
        this.players = Array.isArray(ps) ? ps : [];

        if (t) {
          this.format = t.format || 'SINGLE_ELIM';
          this.cfg.seedCount = t.seed_count == null ? 4 : t.seed_count;
          this.cfg.avoidSameTeam = t.avoid_same_team !== false;
          this.cfg.groupCount = t.group_count || 0;
          this.cfg.groupAdvance = t.group_advance || 2;
          this.cfg.bestOf = t.best_of || 3;
          this.cfg.pointsPerGame = t.points_per_game || 21;
          this.cfg.pointsCap = t.points_cap || 30;
          uni.setNavigationBarTitle({ title: '抽签编排 · ' + (t.title || '') });
        }
        await this.refreshPreview();
      } catch (e) {
        /* 已提示 */
      }
    },
    buildConfig() {
      const seed = this.randomSeedText ? Number(this.randomSeedText) : null;
      const cfg = {
        seedCount: Number(this.cfg.seedCount) || 0,
        avoidSameTeam: !!this.cfg.avoidSameTeam,
        thirdPlace: !!this.cfg.thirdPlace,
        groupCount: Number(this.cfg.groupCount) || 0,
        groupAdvance: Number(this.cfg.groupAdvance) || 2,
        bestOf: this.cfg.bestOf,
        pointsPerGame: Number(this.cfg.pointsPerGame) || 21,
        pointsCap: Number(this.cfg.pointsCap) || 30
      };
      if (seed) cfg.randomSeed = seed;
      return cfg;
    },
    async refreshPreview() {
      if (this.players.length < 2) {
        this.preview = null;
        return;
      }
      try {
        this.preview = await previewDraw(this.id, this.buildConfig());
      } catch (e) {
        this.preview = null;
      }
    },
    step(field, delta, min, max) {
      const cur = Number(this.cfg[field]) || 0;
      let next = cur + delta;
      if (next < min) next = min;
      if (next > max) next = max;
      this.cfg[field] = next;
      this.refreshPreview();
    },
    /**
     * 开关类配置的统一处理：改完必须刷新预览。
     *
     * 之前只有步进器会刷新，「设置季军赛」这类开关改完，
     * 上面的「预计场次」不会重新算（少 1 场季军赛）。
     */
    onCfgSwitch(e) {
      const key = (e.currentTarget.dataset || {}).key;
      if (!key) return;
      this.cfg[key] = e.detail.value;
      this.refreshPreview();
    },
    goResult() {
      uni.navigateTo({ url: '/pages/draw/result?id=' + this.id });
    },
    submit() {
      if (this.submitting) return;
      if (this.players.length < 2) {
        uni.showToast({ title: '参赛人数不足 2 人', icon: 'none' });
        return;
      }
      uni.showModal({
        title: '执行抽签编排',
        content:
          '将为 ' + this.players.length + ' 名选手生成正式赛程' +
          (this.preview && this.preview.matchCount ? '（约 ' + this.preview.matchCount + ' 场）' : '') +
          '。已有编排结果会被覆盖。',
        success: async (r) => {
          if (!r.confirm) return;
          this.submitting = true;
          try {
            const outcome = await executeDraw(this.id, this.buildConfig());
            uni.showToast({ title: '编排完成', icon: 'success' });
            const q =
              '/pages/draw/result?id=' + this.id +
              '&fresh=1' +
              (outcome && outcome.group_count ? '&groups=' + outcome.group_count : '');
            setTimeout(() => {
              uni.redirectTo({ url: q });
            }, 650);
          } catch (e) {
            /* 已提示 */
          } finally {
            this.submitting = false;
          }
        }
      });
    }
  }
};
</script>

<style scoped>
.block {
  margin-bottom: var(--sp-3);
}

.roster {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
}

.roster__main {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.roster__num {
  font-size: 28px;
  color: var(--c-primary);
}

.roster__unit {
  font-size: 13px;
  color: var(--c-text-sub);
}

.preview {
  display: flex;
  margin-top: var(--sp-3);
  background-color: #f7f9fd;
  border-radius: var(--r-sm);
  padding: 10px 4px;
}

.preview__item {
  flex: 1;
  text-align: center;
}

.preview__value {
  display: block;
  font-size: 17px;
  color: var(--c-primary);
}

.preview__label {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 2px;
}

.notice {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: var(--sp-3);
  padding: 9px 11px;
  border-radius: var(--r-sm);
}

.notice--danger {
  background-color: var(--c-danger-soft);
}

.notice--warn {
  background-color: var(--c-warning-soft);
}

.notice__text {
  font-size: 12px;
  flex: 1;
  line-height: 1.5;
}

.notice__text--danger {
  color: var(--c-danger);
}

.notice__text--warn {
  color: var(--c-warning);
}

.stepper-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  min-height: 56px;
}

.stepper {
  width: 132px;
  flex-shrink: 0;
}

.stepper__value {
  font-size: 15px;
}

.seed-input {
  width: 108px;
  min-height: 40px;
  padding: 0 10px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  font-size: 14px;
  text-align: center;
  color: var(--c-text);
}

.ph {
  color: #b3bfd2;
  font-size: 13px;
}

.dual {
  display: flex;
  align-items: center;
  gap: 4px;
}

.dual__input {
  width: 52px;
  min-height: 40px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  text-align: center;
  font-size: 14px;
  color: var(--c-text);
}

.dual__sep {
  font-size: 13px;
  color: var(--c-text-weak);
}

.picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 var(--sp-3);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background-color: var(--c-surface);
}

.picker__value {
  font-size: 15px;
  color: var(--c-text);
}

.picker__ph {
  font-size: 15px;
  color: #b3bfd2;
}

.tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 0 4px 8px;
}

.tip__text {
  font-size: 12px;
  color: var(--c-text-weak);
  line-height: 1.6;
  flex: 1;
}

.footer-btn {
  flex: 1;
}

.footer-btn--main {
  flex: 1.8;
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
