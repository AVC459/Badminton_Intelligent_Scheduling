<template>
  <view class="container container--with-footer">
    <!-- 概览 -->
    <view class="stats">
      <view class="stat">
        <text class="stat__v t-num">{{ players.length }}</text>
        <text class="stat__l">选手总数</text>
      </view>
      <view class="stat__sep" />
      <view class="stat">
        <text class="stat__v t-num">{{ seedCount }}</text>
        <text class="stat__l">种子选手</text>
      </view>
      <view class="stat__sep" />
      <view class="stat">
        <text class="stat__v t-num">{{ teams.length }}</text>
        <text class="stat__l">参赛单位</text>
      </view>
    </view>

    <!-- 搜索 -->
    <view class="search">
      <wb-icon name="search" :size="15" color="#8b98b0" />
      <input
        class="search__input"
        v-model="keyword"
        placeholder="搜索姓名、学号或单位"
        placeholder-class="ph"
        confirm-type="search"
        @confirm="load"
      />
      <view v-if="keyword" class="search__clear" @click="clearKeyword">
        <wb-icon name="close" :size="13" color="#8b98b0" />
      </view>
    </view>

    <!-- 项目分支：选手库按 男单/女单/男双/女双/混双 分开维护 -->
    <view class="cats">
      <view class="cat" :class="{ 'cat--active': !category }" @click="category = ''">
        <text class="cat__t">全部</text>
        <text class="cat__n t-num">{{ players.length }}</text>
      </view>
      <view
        v-for="c in categories"
        :key="c.value"
        class="cat"
        :class="{ 'cat--active': category === c.value }"
        @click="category = c.value"
      >
        <text class="cat__t">{{ c.label }}</text>
        <text class="cat__n t-num">{{ c.count }}</text>
      </view>
    </view>

    <!-- 过滤 -->
    <view class="filters">
      <view
        class="fchip"
        :class="{ 'fchip--active': !teamFilter && !seedOnly }"
        @click="resetFilter"
      >
        全部
        <text class="fchip__count">{{ players.length }}</text>
      </view>
      <view
        class="fchip"
        :class="{ 'fchip--active': seedOnly }"
        @click="toggleSeed"
      >
        仅种子
        <text class="fchip__count">{{ seedCount }}</text>
      </view>
      <view
        v-for="tm in teams"
        :key="tm"
        class="fchip"
        :class="{ 'fchip--active': teamFilter === tm }"
        @click="pickTeam(tm)"
      >
        {{ tm }}
      </view>
    </view>

    <!-- 骨架 -->
    <view v-if="loading" class="skeleton-list">
      <view v-for="i in 5" :key="i" class="skeleton skeleton--row" />
    </view>

    <!-- 空状态 -->
    <wb-empty
      v-else-if="!list.length"
      icon="users"
      :title="players.length ? '没有匹配的选手' : '选手库还是空的'"
      :desc="players.length ? '换个关键词或清除筛选条件再试' : '支持导入 Excel 名单（本地智能识别），或手动逐个录入选手'"
      :action-text="players.length ? '' : '导入选手'"
      @action="goBatch"
    />

    <!-- 列表 -->
    <view v-else class="list">
      <view
        v-for="p in list"
        :key="p.player_id"
        class="pcard"
        @click="openEdit(p)"
        @longpress="confirmDelete(p)"
      >
        <view class="pcard__avatar" :class="p.gender === 'F' ? 'pcard__avatar--f' : 'pcard__avatar--m'">
          <text class="pcard__initial">{{ initial(p.name) }}</text>
        </view>
        <view class="grow">
          <view class="pcard__line">
            <text class="pcard__name t-ellipsis">{{ p.name }}</text>
            <wb-tag :text="categoryLabel(p.category)" tone="info" size="sm" />
            <wb-tag v-if="p.is_seed" :text="'种子 ' + (p.seed_rank || '-')" tone="accent" size="sm" />
          </view>
          <text class="pcard__meta t-ellipsis">
            {{ p.student_id || '无学号' }} · {{ p.team || '未填单位' }}
            <text v-if="p.partner_name">
              · 搭档 {{ p.partner_name }}{{ p.partner_team ? '（' + p.partner_team + '）' : '' }}
            </text>
          </text>
          <text class="pcard__meta t-ellipsis">
            {{ p.gender === 'F' ? '女子' : '男子' }} · 水平分 {{ p.level || 0 }}{{ p.phone ? ' · ' + p.phone : '' }}
          </text>
        </view>
        <view class="pcard__actions">
          <view class="act" @click.stop="openEdit(p)">
            <wb-icon name="edit" :size="15" color="#1e40af" />
          </view>
          <view class="act act--danger" @click.stop="confirmDelete(p)">
            <wb-icon name="trash" :size="15" color="#dc2626" />
          </view>
        </view>
      </view>
    </view>

    <view class="footer-bar">
      <view class="btn btn--ghost btn--sm footer-btn" @click="goBatch">
        <wb-icon name="upload" :size="14" color="#1e40af" />
        <text class="btn__text-ghost btn--gap">Excel 智能导入</text>
      </view>
      <view class="btn btn--primary btn--sm footer-btn footer-btn--main" @click="goCreate">
        <wb-icon name="plus" :size="14" color="#ffffff" :stroke-width="2.4" />
        <text class="btn__text btn--gap">新增选手</text>
      </view>
    </view>
  </view>
</template>

<script>
import { getPlayers, getTeams, deletePlayer } from '../../utils/api';
import { requireLogin } from '../../utils/session';
import { categoryLabel, LIBRARY_CATEGORIES } from '../../utils/dict';

export default {
  data() {
    return {
      players: [],
      teams: [],
      keyword: '',
      teamFilter: '',
      seedOnly: false,
      /** 项目分支筛选：'' 表示全部 */
      category: '',
      loading: true
    };
  },
  computed: {
    seedCount() {
      return this.players.filter((p) => p.is_seed).length;
    },
    /** 五个项目分支及各自人数 */
    categories() {
      return LIBRARY_CATEGORIES.map((c) => {
        let n = 0;
        this.players.forEach((p) => {
          if (String(p.category || '').toUpperCase() === c.value) n++;
        });
        return { value: c.value, label: c.label, count: n };
      });
    },
    /** 前端二次筛选：避免每次输入都打服务端 */
    list() {
      const k = (this.keyword || '').trim().toLowerCase();
      return this.players.filter((p) => {
        if (this.category && String(p.category || '').toUpperCase() !== this.category) return false;
        if (this.seedOnly && !p.is_seed) return false;
        if (this.teamFilter && p.team !== this.teamFilter) return false;
        if (!k) return true;
        const hay = [p.name, p.student_id, p.team, p.phone, p.partner_name, p.partner_team]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        return hay.indexOf(k) > -1;
      });
    }
  },
  onLoad() {
    if (!requireLogin()) return;
  },
  onShow() {
    if (!requireLogin()) return;
    this.load();
  },
  methods: {
    categoryLabel,
    initial(name) {
      return name ? String(name).slice(0, 1) : '?';
    },
    clearKeyword() {
      this.keyword = '';
    },
    resetFilter() {
      this.teamFilter = '';
      this.seedOnly = false;
    },
    toggleSeed() {
      this.seedOnly = !this.seedOnly;
    },
    pickTeam(tm) {
      this.teamFilter = this.teamFilter === tm ? '' : tm;
    },
    async load() {
      this.loading = true;
      try {
        const [ps, ts] = await Promise.all([
          getPlayers(),
          getTeams().catch(() => [])
        ]);
        this.players = Array.isArray(ps) ? ps : [];
        this.teams = Array.isArray(ts) ? ts : [];
      } catch (e) {
        /* 已提示 */
      } finally {
        this.loading = false;
      }
    },
    goCreate() {
      uni.navigateTo({ url: '/pages/player/edit?category=' + (this.category || 'MS') });
    },
    goBatch() {
      // 把当前所在分支带过去，导入时默认落到该分支
      uni.navigateTo({
        url: '/pages/player/edit?mode=batch&category=' + (this.category || 'MS')
      });
    },
    openEdit(p) {
      uni.navigateTo({ url: '/pages/player/edit?id=' + p.player_id });
    },
    confirmDelete(p) {
      uni.showModal({
        title: '删除选手',
        content:
          '确认删除「' + p.name + '」？若该选手仍在未结束赛事的参赛名单中，将无法删除。',
        confirmText: '删除',
        confirmColor: '#dc2626',
        success: async (r) => {
          if (!r.confirm) return;
          try {
            await deletePlayer(p.player_id);
            uni.showToast({ title: '已删除', icon: 'success' });
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
    return { title: '我的选手库', path: '/pages/player/list' };
  }
};
</script>

<style scoped>
.ph {
  color: #b3bfd2;
  font-size: 14px;
}

/* ---------- 概览 ---------- */
.stats {
  display: flex;
  align-items: center;
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 14px 0;
  box-shadow: var(--sh-1);
  margin-bottom: var(--sp-3);
}

.stat {
  flex: 1;
  text-align: center;
}

.stat__v {
  display: block;
  font-size: 21px;
  color: var(--c-text);
}

.stat__l {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 2px;
}

.stat__sep {
  width: 1px;
  height: 24px;
  background-color: var(--c-muted);
}

/* ---------- 搜索 ---------- */
.search {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 12px;
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  margin-bottom: var(--sp-2);
}

.search__input {
  flex: 1;
  min-width: 0;
  min-height: 42px;
  font-size: 14px;
  color: var(--c-text);
}

.search__clear {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background-color: var(--c-muted);
  flex-shrink: 0;
}

/* ---------- 项目分支 ---------- */
.cats {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-bottom: var(--sp-2);
}

.cat {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--c-border);
  background-color: var(--c-surface);
}

.cat--active {
  border-color: var(--c-primary);
  background-color: var(--c-primary-soft);
}

.cat__t {
  font-size: 13px;
  color: var(--c-text-sub);
}

.cat--active .cat__t {
  color: var(--c-primary);
  font-weight: 600;
}

.cat__n {
  font-size: 11px;
  color: var(--c-text-weak);
}

.cat--active .cat__n {
  color: var(--c-primary);
}

/* ---------- 过滤 ---------- */
.filters {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
  margin-bottom: var(--sp-3);
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

/* ---------- 列表 ---------- */
.list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skeleton--row {
  height: 74px;
}

.pcard {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 12px;
  box-shadow: var(--sh-1);
}

.pcard__avatar {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.pcard__avatar--m {
  background-color: var(--c-primary-soft);
}

.pcard__avatar--f {
  background-color: #fdf0e6;
}

.pcard__initial {
  font-size: 17px;
  font-weight: 700;
  color: var(--c-primary);
}

.pcard__avatar--f .pcard__initial {
  color: #b45309;
}

.pcard__line {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pcard__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
  max-width: 60%;
}

.pcard__meta {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 3px;
}

.pcard__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.act {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background-color: var(--c-primary-soft);
}

.act--danger {
  background-color: var(--c-danger-soft);
}

/* ---------- 底部 ---------- */
.footer-btn {
  flex: 1;
}

.footer-btn--main {
  flex: 1.4;
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

.btn--gap {
  margin-left: 5px;
}
</style>
