<template>
  <view class="container container--with-footer">
    <!-- 赛事概要 -->
    <view class="hero">
      <text class="hero__title t-ellipsis">{{ tournament.title || '赛事' }}</text>
      <view class="hero__stats">
        <view class="hero__stat">
          <text class="hero__value t-num">{{ entries.length }}</text>
          <text class="hero__label">参赛选手</text>
        </view>
        <view class="hero__stat">
          <text class="hero__value t-num">{{ seedCount }}</text>
          <text class="hero__label">种子选手</text>
        </view>
        <view class="hero__stat">
          <text class="hero__value t-num">{{ pickableRaw.length }}</text>
          <text class="hero__label">可选选手</text>
        </view>
      </view>
    </view>

    <!-- 已选名单 -->
    <wb-section
      class="block"
      title="参赛名单"
      icon="users"
      :badge="entries.length"
      desc="从我的选手库直接选人，无需报名与审核"
    >
      <wb-empty
        v-if="!entries.length"
        icon="users"
        title="还没有选手"
        desc="点击下方「添加选手」，从选手库中选择参赛人员"
        action-text="添加选手"
        @action="openPicker"
      />
      <view v-else>
        <view v-for="e in entries" :key="e.entry_id" class="row">
          <view class="row__seed" :class="{ 'row__seed--on': e.seed_flag }">
            <text class="row__seed-text">{{ e.seed_flag ? e.seed_rank : '—' }}</text>
          </view>
          <view class="grow">
            <text class="row__name t-ellipsis">{{ e.name || '（选手已删除）' }}</text>
            <text class="row__meta t-ellipsis">
              {{ e.student_id }} · {{ genderLabel(e.gender) }} · {{ e.team || '未填单位' }}
              <text v-if="e.partner_team"> / 搭档 {{ e.partner_team }}</text>
            </text>
          </view>
          <view class="row__ops">
            <view class="row__op" @click="toggleSeed(e)">
              <wb-icon
                name="star"
                :size="15"
                :color="e.seed_flag ? '#d97706' : '#8b98b0'"
              />
            </view>
            <view class="row__op" @click="confirmRemove(e)">
              <wb-icon name="close" :size="15" color="#dc2626" />
            </view>
          </view>
        </view>
      </view>
    </wb-section>

    <!-- 选手库选择面板 -->
    <wb-section
      v-if="picking"
      class="block"
      title="从选手库选择"
      icon="plus"
      :badge="picked.length + ' 已选'"
      :desc="pickerDesc"
    >
      <view class="picker__tools">
        <input
          class="picker__search"
          v-model="keyword"
          placeholder="搜索姓名 / 学号 / 单位"
          placeholder-class="ph"
        />
        <view class="picker__all" @click="toggleAll">
          <text class="picker__all-text">{{ allPicked ? '取消全选' : '全选' }}</text>
        </view>
      </view>

      <!-- 项目分支快捷筛选：默认只看与本赛事项目一致的分支 -->
      <view class="gfilters">
        <view
          v-for="c in categoryTabs"
          :key="c.value + ''"
          class="gchip"
          :class="{ 'gchip--active': category === c.value }"
          @click="category = c.value"
        >
          <text class="gchip__text">{{ c.label }} {{ c.count }}</text>
        </view>
      </view>

      <!-- 性别筛选：男子单打默认只看男生，避免把女生选进男单 -->
      <view v-if="needGenderFilter" class="gfilters gfilters--sub">
        <view
          v-for="g in genderTabs"
          :key="'g' + g.value"
          class="gchip"
          :class="{ 'gchip--active': gender === g.value }"
          @click="gender = g.value"
        >
          <text class="gchip__text">{{ g.label }} {{ g.count }}</text>
        </view>
      </view>

      <!-- 一键添加当前筛选出的全部选手 -->
      <view
        v-if="filteredPickable.length"
        class="quickadd"
        :class="{ 'quickadd--disabled': submitting }"
        @click="quickAddAll"
      >
        <wb-icon name="plus" :size="14" color="#ffffff" :stroke-width="2.6" />
        <text class="quickadd__text">
          一键添加全部{{ scopeLabel }}（{{ filteredPickable.length }} 人）
        </text>
      </view>

      <wb-empty
        v-if="!filteredPickable.length"
        icon="search"
        title="没有可添加的选手"
        :desc="emptyPickDesc"
        action-text="去选手库"
        @action="goPlayers"
      />
      <view v-else>
        <view
          v-for="p in filteredPickable"
          :key="p.player_id"
          class="row row--pick"
          @click="togglePick(p.player_id)"
        >
          <view class="row__check" :class="{ 'row__check--on': picked.indexOf(p.player_id) > -1 }">
            <wb-icon
              v-if="picked.indexOf(p.player_id) > -1"
              name="check"
              :size="11"
              color="#ffffff"
              :stroke-width="3"
            />
          </view>
          <view class="grow">
            <text class="row__name t-ellipsis">{{ p.name }}</text>
            <text class="row__meta t-ellipsis">
              {{ p.student_id }} · {{ genderLabel(p.gender) }} · {{ p.team || '未填单位' }} ·
              水平分 {{ p.level || 0 }}
            </text>
          </view>
          <wb-tag v-if="p.is_seed" text="种子" tone="accent" size="sm" />
        </view>
      </view>
    </wb-section>

    <view class="footer-bar">
      <view class="btn btn--ghost btn--sm footer-btn" @click="openPicker">
        <text class="btn__text-ghost">{{ picking ? '收起选择' : '添加选手' }}</text>
      </view>
      <view
        v-if="picking"
        class="btn btn--primary btn--sm footer-btn"
        :class="{ 'btn--disabled': submitting || !picked.length }"
        @click="confirmAdd"
      >
        <text class="btn__text">{{ submitting ? '添加中…' : '选入 ' + picked.length + ' 人' }}</text>
      </view>
      <view
        v-else
        class="btn btn--accent btn--sm footer-btn"
        :class="{ 'btn--disabled': entries.length < 2 }"
        @click="goDraw"
      >
        <text class="btn__text">抽签编排</text>
      </view>
    </view>
  </view>
</template>

<script>
import {
  getTournamentById,
  getEntries,
  getPlayers,
  addEntries,
  removeEntry,
  setEntrySeed
} from '../../utils/api';
import { requireLogin, requireParam } from '../../utils/session';
import {
  genderLabel,
  categoryLabel,
  eventTypeLabel,
  genderOfCategory,
  LIBRARY_CATEGORIES
} from '../../utils/dict';

export default {
  data() {
    return {
      id: '',
      tournament: {},
      entries: [],
      library: [],
      picking: false,
      picked: [],
      keyword: '',
      /** 项目分支筛选：'' 表示全部项目 */
      category: '',
      /** 性别筛选：'' 表示不限 */
      gender: '',
      /** 只在首次加载时按赛事项目预置筛选，避免刷新名单后把用户的手动切换重置 */
      categoryInited: false,
      genderInited: false,
      submitting: false
    };
  },
  computed: {
    seedCount() {
      return this.entries.filter((e) => e.seed_flag).length;
    },
    /** 选手库中尚未进入名单的选手（未按性别过滤） */
    pickableRaw() {
      const used = {};
      this.entries.forEach((e) => {
        used[e.player_id] = true;
      });
      return this.library.filter((p) => !used[p.player_id]);
    },
    /** 本赛事对应的选手库分支（与 event_type 取值一致） */
    eventCategory() {
      const e = String((this.tournament && this.tournament.event_type) || 'MS').toUpperCase();
      return LIBRARY_CATEGORIES.some((c) => c.value === e) ? e : 'MS';
    },
    /** 先按项目分支筛选：男子单打默认只显示「男单」分支，避免跨项目误选 */
    pickableByCategory() {
      if (!this.category) return this.pickableRaw;
      return this.pickableRaw.filter(
        (p) => String(p.category || '').toUpperCase() === this.category
      );
    },
    /** 本赛事项目隐含的性别（混双为不限） */
    eventGender() {
      return genderOfCategory(this.eventCategory);
    },
    /** 再按性别筛选：男单赛事默认只列男生，从源头避免把女生选进男单 */
    pickable() {
      if (!this.gender) return this.pickableByCategory;
      return this.pickableByCategory.filter(
        (p) => (p.gender === 'F' ? 'F' : 'M') === this.gender
      );
    },
    /** 性别筛选按钮（含各自人数） */
    genderTabs() {
      const all = this.pickableByCategory;
      const males = all.filter((p) => p.gender !== 'F').length;
      return [
        { value: '', label: '不限', count: all.length },
        { value: 'M', label: '男生', count: males },
        { value: 'F', label: '女生', count: all.length - males }
      ];
    },
    /** 只有当前分支里男女混杂时才需要性别筛选 */
    needGenderFilter() {
      const all = this.pickableByCategory;
      const males = all.filter((p) => p.gender !== 'F').length;
      return all.length > 0 && males > 0 && males !== all.length;
    },
    filteredPickable() {
      const k = (this.keyword || '').trim().toLowerCase();
      if (!k) return this.pickable;
      return this.pickable.filter((p) => {
        const hay = [p.name, p.student_id, p.team].join(' ').toLowerCase();
        return hay.indexOf(k) > -1;
      });
    },
    /** 项目分支标签：本赛事分支 + 全部项目 */
    categoryTabs() {
      const all = this.pickableRaw;
      const own = all.filter(
        (p) => String(p.category || '').toUpperCase() === this.eventCategory
      ).length;
      return [
        { value: this.eventCategory, label: categoryLabel(this.eventCategory), count: own },
        { value: '', label: '全部项目', count: all.length }
      ];
    },
    /** 「一键添加全部 X」里的 X */
    scopeLabel() {
      const c = this.category;
      if (!this.gender) return c ? categoryLabel(c) : '选手';
      // 分支本身已限定了同一性别时不再重复（男单 == 男生）
      if (genderOfCategory(c) === this.gender) return categoryLabel(c);
      return this.gender === 'F' ? '女生' : '男生';
    },
    /** 说明默认筛选口径 */
    pickerDesc() {
      const g = this.gender ? (this.gender === 'F' ? '女生' : '男生') : '';
      return '本赛事为' + eventTypeLabel(this.tournament && this.tournament.event_type)
        + '，默认只显示「' + categoryLabel(this.eventCategory) + '」分支'
        + (g ? '的' + g : '的选手');
    },
    /**
     * 当前筛选出的选手是否已全部勾选。
     *
     * 不能用 picked.length 直接比：picked 里可能还留着在别的筛选下勾选的人
     * （这是允许的，选人本来就是跨筛选累积的），那样会误判成「已全选」。
     */
    allPicked() {
      if (!this.filteredPickable.length) return false;
      const set = {};
      this.picked.forEach((id) => {
        set[id] = true;
      });
      return this.filteredPickable.every((p) => set[p.player_id]);
    },
    /** 空态说明：区分「库是空的」和「被项目分支挡掉了」 */
    emptyPickDesc() {
      if (this.pickableRaw.length && !this.pickable.length) {
        return '当前筛选（' + this.scopeLabel + '）下没有可选的选手，'
          + '可切换「全部项目」或调整性别筛选再看看';
      }
      return '选手库中的人员已全部在名单内，或还没有录入选手';
    }
  },
  onShareAppMessage() {
    const t = this.tournament || {};
    return {
      title: (t.title || '羽毛球赛事') + ' · 参赛名单',
      path: '/pages/entry/list?id=' + this.id
    };
  },
  onLoad(options) {
    if (!requireLogin()) return;
    this.id = (options && options.id) || '';
    if (!requireParam(this.id, '赛事ID')) return;
    this.load();
  },
  methods: {
    genderLabel,
    async load() {
      try {
        const [t, es, lib] = await Promise.all([
          getTournamentById(this.id),
          getEntries(this.id),
          getPlayers()
        ]);
        this.tournament = t || {};
        this.entries = Array.isArray(es) ? es : [];
        this.library = Array.isArray(lib) ? lib : [];
        // 按赛事项目预置分支筛选：男子单打只列「男单」分支，混双只列「混双」分支
        if (!this.categoryInited) {
          this.category = this.eventCategory;
          this.categoryInited = true;
        }
        // 同时预置性别筛选：男单默认「男生」，混双不限（男女都要）
        if (!this.genderInited) {
          this.gender = this.eventGender;
          this.genderInited = true;
        }
        if (this.tournament.title) {
          uni.setNavigationBarTitle({ title: '参赛名单 · ' + this.tournament.title });
        }
      } catch (e) {
        /* 已由请求层提示 */
      }
    },
    openPicker() {
      this.picking = !this.picking;
      if (!this.picking) this.picked = [];
    },
    togglePick(id) {
      const i = this.picked.indexOf(id);
      if (i > -1) this.picked.splice(i, 1);
      else this.picked.push(id);
    },
    /**
     * 全选 / 取消全选，只作用于当前筛选出的选手。
     *
     * 之前是整体替换 picked：切到别的筛选（或换了性别）再点全选，
     * 之前在其它筛选下勾好的人会被静默丢掉。现在改成在 picked 上做增删。
     */
    toggleAll() {
      if (this.allPicked) {
        const drop = {};
        this.filteredPickable.forEach((p) => {
          drop[p.player_id] = true;
        });
        this.picked = this.picked.filter((id) => !drop[id]);
        return;
      }
      const next = this.picked.slice();
      this.filteredPickable.forEach((p) => {
        if (next.indexOf(p.player_id) < 0) next.push(p.player_id);
      });
      this.picked = next;
    },
    confirmAdd() {
      if (this.submitting || !this.picked.length) return;
      this.submitting = true;
      addEntries(this.id, this.picked)
        .then((n) => {
          uni.showToast({ title: '已选入 ' + (n || this.picked.length) + ' 人', icon: 'success' });
          this.picked = [];
          return this.load();
        })
        .catch(() => {})
        .finally(() => {
          this.submitting = false;
        });
    },
    /**
     * 一键添加：把当前筛选出的选手（性别 + 关键字）一次性全部选入名单。
     * 男子单打场景下点一下即可把选手库里所有男生加进来。
     */
    quickAddAll() {
      if (this.submitting) return;
      const list = this.filteredPickable;
      if (!list.length) {
        uni.showToast({ title: '没有符合条件的选手', icon: 'none' });
        return;
      }
      uni.showModal({
        title: '一键添加',
        content: '将把当前筛选出的 ' + list.length + ' 名' + this.scopeLabel + '全部加入参赛名单？',
        confirmText: '添加',
        success: (r) => {
          if (!r.confirm) return;
          this.submitting = true;
          addEntries(this.id, list.map((p) => p.player_id))
            .then((n) => {
              uni.showToast({ title: '已添加 ' + (n || list.length) + ' 人', icon: 'success' });
              this.picked = [];
              return this.load();
            })
            .catch(() => {})
            .finally(() => {
              this.submitting = false;
            });
        }
      });
    },
    toggleSeed(e) {
      const next = !e.seed_flag;
      setEntrySeed(e.entry_id, next, next ? this.nextSeedRank() : 0)
        .then(() => this.load())
        .catch(() => {});
    },
    nextSeedRank() {
      let max = 0;
      this.entries.forEach((x) => {
        if (x.seed_flag && x.seed_rank > max) max = x.seed_rank;
      });
      return max + 1;
    },
    confirmRemove(e) {
      uni.showModal({
        title: '移出名单',
        content: '将「' + (e.name || '该选手') + '」移出本赛事参赛名单？',
        confirmText: '移出',
        confirmColor: '#dc2626',
        success: async (r) => {
          if (!r.confirm) return;
          try {
            await removeEntry(e.entry_id);
            uni.showToast({ title: '已移出', icon: 'success' });
            this.load();
          } catch (err) {
            /* 已提示 */
          }
        }
      });
    },
    goDraw() {
      if (this.entries.length < 2) return;
      uni.navigateTo({ url: '/pages/draw/config?id=' + this.id });
    },
    goPlayers() {
      this.picking = false;
      uni.navigateTo({ url: '/pages/player/list' });
    }
  }
};
</script>

<style scoped>
.hero {
  background: linear-gradient(150deg, #1e40af, #2563eb);
  border-radius: var(--r-md);
  padding: 16px;
  margin-bottom: var(--sp-3);
  box-shadow: var(--sh-2);
}

.hero__title {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: #fff;
}

.hero__stats {
  display: flex;
  margin-top: 14px;
}

.hero__stat {
  flex: 1;
  text-align: center;
}

.hero__value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}

.hero__label {
  display: block;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.76);
  margin-top: 2px;
}

.block {
  margin-bottom: var(--sp-3);
}

.row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-height: 56px;
  border-bottom: 1px solid var(--c-muted);
}

.row:last-child {
  border-bottom: none;
}

.row--pick {
  min-height: 52px;
}

.row__seed {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background-color: var(--c-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.row__seed--on {
  background-color: #fdf3e3;
}

.row__seed-text {
  font-size: 12px;
  font-weight: 700;
  color: var(--c-text-sub);
}

.row__seed--on .row__seed-text {
  color: #d97706;
}

.row__name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.row__meta {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 3px;
}

.row__ops {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.row__op {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background-color: #f7f9fd;
  display: flex;
  align-items: center;
  justify-content: center;
}

.row__check {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  border: 1.5px solid var(--c-border);
  background-color: var(--c-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.row__check--on {
  background-color: var(--c-primary);
  border-color: var(--c-primary);
}

/* ---------- 选择面板 ---------- */
.picker__tools {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: 8px;
}

.picker__search {
  flex: 1;
  min-height: 40px;
  padding: 0 12px;
  font-size: 14px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background-color: var(--c-surface);
  box-sizing: border-box;
}

.ph {
  color: #b3bfd2;
  font-size: 14px;
}

.picker__all {
  min-height: 40px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  border-radius: var(--r-sm);
  background-color: var(--c-primary-soft);
}

.picker__all-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-primary);
}

/* ---------- 性别快捷筛选 ---------- */
.gfilters {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}

/* 第二行（性别）比第一行（项目分支）更轻 */
.gfilters--sub {
  margin-top: -4px;
}

.gfilters--sub .gchip {
  min-height: 32px;
}

.gchip {
  flex: 1;
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-sm);
  border: 1px solid var(--c-border);
  background-color: var(--c-surface);
}

.gchip--active {
  border-color: var(--c-primary);
  background-color: var(--c-primary-soft);
}

.gchip__text {
  font-size: 13px;
  color: var(--c-text-sub);
}

.gchip--active .gchip__text {
  color: var(--c-primary);
  font-weight: 600;
}

/* ---------- 一键添加 ---------- */
.quickadd {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 46px;
  border-radius: var(--r-sm);
  background: linear-gradient(135deg, #2563eb, #1e40af);
  margin-bottom: 12px;
}

.quickadd--disabled {
  opacity: 0.5;
  pointer-events: none;
}

.quickadd__text {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
}

.footer-btn {
  flex: 1;
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
