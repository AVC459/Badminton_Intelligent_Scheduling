<template>
  <view class="container container--with-footer">
    <!-- 编辑态提示：底部 tab 也叫「创建赛事」，不说清楚会让人以为在新建 -->
    <view v-if="isEdit" class="editbar">
      <wb-icon name="edit" :size="13" color="#1e40af" />
      <text class="editbar__text t-ellipsis">正在编辑：{{ form.title || '未命名赛事' }}</text>
      <view class="editbar__btn" @click="resetForm">
        <text class="editbar__btn-text">改为新建</text>
      </view>
    </view>

    <!-- 基本信息 -->
    <wb-section class="block" title="基本信息" icon="trophy">
      <view class="field">
        <text class="field__label field__label--required">赛事名称</text>
        <input
          class="field__input"
          v-model="form.title"
          placeholder="例如：2026 校园羽毛球公开赛 · 男单"
          placeholder-class="ph"
          :maxlength="40"
        />
      </view>

      <view class="field">
        <text class="field__label">项目类型</text>
        <view class="chips">
          <view
            v-for="e in eventTypes"
            :key="e.value"
            class="chip"
            :class="{ 'chip--active': form.event_type === e.value }"
            @click="form.event_type = e.value"
          >
            {{ e.label }}
          </view>
        </view>
      </view>

      <view class="field" style="margin-bottom: 0">
        <text class="field__label">备注</text>
        <textarea
          class="field__input field__textarea"
          v-model="form.remark"
          placeholder="赛制说明、注意事项等（可选）"
          placeholder-class="ph"
          :maxlength="200"
        />
      </view>
    </wb-section>

    <!-- 赛制 -->
    <wb-section class="block" title="竞赛办法" icon="layers" desc="赛制决定编排算法与场次总量">
      <view class="formats">
        <view
          v-for="f in formats"
          :key="f.value"
          class="format"
          :class="{ 'format--active': form.format === f.value }"
          @click="form.format = f.value"
        >
          <view class="format__head">
            <text class="format__label">{{ f.label }}</text>
            <view v-if="form.format === f.value" class="format__check">
              <wb-icon name="check" :size="12" color="#ffffff" :stroke-width="2.6" />
            </view>
          </view>
          <text class="format__desc">{{ f.desc }}</text>
        </view>
      </view>

      <!-- 分组参数：渐进式展开，非分组赛制时不打扰 -->
      <view v-if="form.format === 'GROUP_KNOCKOUT'" class="sub">
        <view class="divider" />
        <view class="stepper-row">
          <view class="grow">
            <text class="switch-row__title">小组数量</text>
            <text class="switch-row__desc">0 表示按人数自动建议（每组至少 3 人）</text>
          </view>
          <view class="stepper">
            <view class="stepper__btn" @click="step('group_count', -1, 0, 16)">
              <wb-icon name="minus" :size="16" color="#1e40af" :stroke-width="2.4" />
            </view>
            <text class="stepper__value">{{ form.group_count || '自动' }}</text>
            <view class="stepper__btn" @click="step('group_count', 1, 0, 16)">
              <wb-icon name="plus" :size="16" color="#1e40af" :stroke-width="2.4" />
            </view>
          </view>
        </view>

        <view class="stepper-row">
          <view class="grow">
            <text class="switch-row__title">每组出线人数</text>
            <text class="switch-row__desc">出线者交叉配对进入淘汰赛；同一小组的出线选手尽可能晚相遇</text>
          </view>
          <view class="stepper">
            <view class="stepper__btn" @click="step('group_advance', -1, 1, 4)">
              <wb-icon name="minus" :size="16" color="#1e40af" :stroke-width="2.4" />
            </view>
            <text class="stepper__value">{{ form.group_advance }}</text>
            <view class="stepper__btn" @click="step('group_advance', 1, 1, 4)">
              <wb-icon name="plus" :size="16" color="#1e40af" :stroke-width="2.4" />
            </view>
          </view>
        </view>
      </view>
    </wb-section>

    <!-- 抽签参数 -->
    <wb-section class="block" title="抽签与编排" icon="shuffle" desc="影响签位分布与同单位选手的相遇时机">
      <view class="stepper-row">
        <view class="grow">
          <text class="switch-row__title">种子数量</text>
          <text class="switch-row__desc">种子按蛇形落位均匀分布到各 1/4 区</text>
        </view>
        <view class="stepper">
          <view class="stepper__btn" @click="step('seed_count', -1, 0, 16)">
            <wb-icon name="minus" :size="16" color="#1e40af" :stroke-width="2.4" />
          </view>
          <text class="stepper__value">{{ form.seed_count }}</text>
          <view class="stepper__btn" @click="step('seed_count', 1, 0, 16)">
            <wb-icon name="plus" :size="16" color="#1e40af" :stroke-width="2.4" />
          </view>
        </view>
      </view>

      <view class="divider" />

      <view class="switch-row">
        <view class="switch-row__text">
          <text class="switch-row__title">同单位规避</text>
          <text class="switch-row__desc">尽量避免同一学院/部门的选手首轮相遇（死签检测 + 换位修正）</text>
        </view>
        <switch
          :checked="form.avoid_same_team"
          color="#1e40af"
          @change="onAvoidChange"
        />
      </view>
    </wb-section>

    <!-- 局分规则 -->
    <wb-section class="block" title="局分规则" icon="target">
      <view class="field">
        <text class="field__label">赛制局数</text>
        <view class="segment">
          <view
            v-for="b in [1, 3]"
            :key="b"
            class="segment__item"
            :class="{ 'segment__item--active': form.best_of === b }"
            @click="form.best_of = b"
          >
            {{ b }} 局{{ b === 1 ? '定胜负' : '两胜' }}
          </view>
        </view>
      </view>

      <view class="stepper-row">
        <view class="grow">
          <text class="switch-row__title">每局分数</text>
          <text class="switch-row__desc">标准羽毛球单局 21 分</text>
        </view>
        <view class="stepper">
          <view class="stepper__btn" @click="step('points_per_game', -1, 1, 31)">
            <wb-icon name="minus" :size="16" color="#1e40af" :stroke-width="2.4" />
          </view>
          <text class="stepper__value">{{ form.points_per_game }}</text>
          <view class="stepper__btn" @click="step('points_per_game', 1, 1, 31)">
            <wb-icon name="plus" :size="16" color="#1e40af" :stroke-width="2.4" />
          </view>
        </view>
      </view>

      <view class="stepper-row">
        <view class="grow">
          <text class="switch-row__title">封顶分</text>
          <text class="switch-row__desc">20 平后需净胜 2 分，达到封顶分即胜</text>
        </view>
        <view class="stepper">
          <view class="stepper__btn" @click="step('points_cap', -1, 1, 60)">
            <wb-icon name="minus" :size="16" color="#1e40af" :stroke-width="2.4" />
          </view>
          <text class="stepper__value">{{ form.points_cap }}</text>
          <view class="stepper__btn" @click="step('points_cap', 1, 1, 60)">
            <wb-icon name="plus" :size="16" color="#1e40af" :stroke-width="2.4" />
          </view>
        </view>
      </view>
    </wb-section>

    <text v-if="error" class="field__error err-box">{{ error }}</text>

    <view class="footer-bar">
      <view
        class="btn btn--primary btn--block"
        :class="{ 'btn--disabled': submitting }"
        @click="submit"
      >
        <text class="btn__text">{{ submitting ? '保存中…' : isEdit ? '保存修改' : '创建赛事' }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { addTournament, updateTournament, getTournamentById } from '../../utils/api';
import { requireLogin } from '../../utils/session';
import { FORMATS, EVENT_TYPES } from '../../utils/dict';

function emptyForm() {
  return {
    title: '',
    event_type: 'MS',
    format: 'SINGLE_ELIM',
    seed_count: 4,
    avoid_same_team: true,
    group_count: 0,
    group_advance: 2,
    best_of: 3,
    points_per_game: 21,
    points_cap: 30,
    remark: ''
  };
}

export default {
  data() {
    return {
      id: '',
      isEdit: false,
      formats: FORMATS,
      eventTypes: EVENT_TYPES,
      submitting: false,
      error: '',
      form: emptyForm()
    };
  },
  onLoad(options) {
    if (!requireLogin()) return;
    if (options && options.id) {
      this.enterEdit(options.id);
    }
  },
  onShow() {
    if (!requireLogin()) return;
    // 从赛事详情「编辑赛事信息」跳转过来：switchTab 无法带参数，改用本地存储传参
    const pendingId = uni.getStorageSync('edit_tournament_id');
    if (pendingId) {
      uni.removeStorageSync('edit_tournament_id');
      this.enterEdit(pendingId);
    }
    // 刻意不再在这里 resetForm()：
    // 之前每次切回本 tab 都会清空编辑态，用户填到一半的表单会被静默丢掉。
    // 现在只有「保存成功」或用户主动点「改为新建」才会重置。
  },
  methods: {
    enterEdit(id) {
      this.id = id;
      this.isEdit = true;
      uni.setNavigationBarTitle({ title: '编辑赛事' });
      this.load();
    },
    resetForm() {
      this.id = '';
      this.isEdit = false;
      this.error = '';
      this.form = emptyForm();
      uni.setNavigationBarTitle({ title: '创建赛事' });
    },
    async load() {
      try {
        const t = await getTournamentById(this.id);
        if (!t) return;
        this.form = {
          title: t.title || '',
          event_type: t.event_type || 'MS',
          format: t.format || 'SINGLE_ELIM',
          seed_count: t.seed_count == null ? 4 : t.seed_count,
          avoid_same_team: t.avoid_same_team !== false,
          group_count: t.group_count || 0,
          group_advance: t.group_advance || 2,
          best_of: t.best_of || 3,
          points_per_game: t.points_per_game || 21,
          points_cap: t.points_cap || 30,
          remark: t.remark || ''
        };
      } catch (e) {
        /* 已由请求层提示 */
      }
    },
    step(field, delta, min, max) {
      const cur = Number(this.form[field]) || 0;
      let next = cur + delta;
      if (next < min) next = min;
      if (next > max) next = max;
      this.form[field] = next;
    },
    onAvoidChange(e) {
      this.form.avoid_same_team = e.detail.value;
    },
    async submit() {
      if (this.submitting) return;
      this.error = '';
      const title = (this.form.title || '').trim();
      if (!title) {
        this.error = '请填写赛事名称';
        return;
      }
      const payload = Object.assign({}, this.form, { title });
      this.submitting = true;
      try {
        if (this.isEdit) {
          payload.tournament_id = this.id;
          await updateTournament(payload);
          uni.showToast({ title: '已保存', icon: 'success' });
        } else {
          const created = await addTournament(payload);
          uni.showToast({ title: '赛事已创建', icon: 'success' });
          if (created && created.tournament_id) {
            this.id = created.tournament_id;
          }
        }
        setTimeout(() => {
          if (!this.id) {
            uni.navigateBack();
            return;
          }
          if (this.isEdit) {
            // 保存成功后回到「新建」态：否则下次切到本 tab 还停在编辑态，
            // 底部 tab 又叫「创建赛事」，很容易被误解成新建表单
            const editedId = this.id;
            this.resetForm();
            const pages = getCurrentPages();
            if (pages.length > 1) uni.navigateBack();
            else uni.redirectTo({ url: '/pages/tournament/detail?id=' + editedId });
          } else {
            // 新建完成后直接进入参赛名单，从选手库选人 → 抽签编排。
            // 注意：必须先取出 id —— resetForm() 会把 this.id 清空，
            // 之前先 resetForm 再拼 URL，跳过去的就是 ?id= 空值，
            // 参赛名单页随即弹「缺少赛事ID」。
            const newId = this.id;
            this.resetForm();
            uni.redirectTo({ url: '/pages/entry/list?id=' + newId });
          }
        }, 700);
      } catch (e) {
        this.error = (e && e.message) || '保存失败';
      } finally {
        this.submitting = false;
      }
    }
  }
};
</script>

<style scoped>
/* 编辑态提示条 */
.editbar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--sp-3);
  padding: 9px 12px;
  border-radius: var(--r-sm);
  background-color: #e8eefb;
}

.editbar__text {
  flex: 1;
  font-size: 12px;
  color: var(--c-primary);
}

.editbar__btn {
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--c-primary);
  background-color: #ffffff;
}

.editbar__btn-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-primary);
}

.block {
  margin-bottom: var(--sp-3);
}

.ph {
  color: #b3bfd2;
  font-size: 15px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  min-height: 38px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid var(--c-border);
  background-color: var(--c-surface);
  font-size: 13px;
  color: var(--c-text-sub);
}

.chip--active {
  border-color: var(--c-primary);
  background-color: var(--c-primary-soft);
  color: var(--c-primary);
  font-weight: 600;
}

.formats {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.format {
  border: 1.5px solid var(--c-border);
  border-radius: var(--r-sm);
  padding: 12px 14px;
  background-color: var(--c-surface);
}

.format--active {
  border-color: var(--c-primary);
  background-color: #f6f9ff;
}

.format__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.format__label {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.format__check {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background-color: var(--c-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.format__desc {
  display: block;
  font-size: 12px;
  color: var(--c-text-sub);
  line-height: 1.55;
  margin-top: 5px;
}

.sub {
  margin-top: var(--sp-3);
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

.err-box {
  display: block;
  padding: 0 4px 12px;
}

.btn__text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}
</style>
