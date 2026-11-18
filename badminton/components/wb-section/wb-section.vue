<template>
  <view class="section">
    <view v-if="title" class="section__head">
      <view class="section__title-wrap">
        <wb-icon v-if="icon" :name="icon" :size="15" :color="iconColor" />
        <text class="section__title">{{ title }}</text>
        <wb-tag v-if="badge" :text="badge" :tone="badgeTone" size="sm" />
      </view>
      <view v-if="actionText" class="section__action" @click="$emit('action')">
        <text class="section__action-text">{{ actionText }}</text>
        <wb-icon name="chevronRight" :size="13" color="#1e40af" />
      </view>
    </view>
    <text v-if="desc" class="section__desc">{{ desc }}</text>
    <view class="section__body">
      <slot />
    </view>
  </view>
</template>

<script>
/**
 * wb-section — 内容分区块
 *
 * 统一卡片标题层级（图标 + 标题 + 可选计数徽标 + 右侧操作），
 * 避免各页面自行发明标题样式造成视觉节奏不一致。
 */
export default {
  name: 'WbSection',
  props: {
    title: { type: String, default: '' },
    desc: { type: String, default: '' },
    icon: { type: String, default: '' },
    iconColor: { type: String, default: '#1e40af' },
    badge: { type: [String, Number], default: '' },
    badgeTone: { type: String, default: 'neutral' },
    actionText: { type: String, default: '' },
    flush: { type: Boolean, default: false }
  }
};
</script>

<style scoped>
.section {
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  box-shadow: var(--sh-1);
  padding: var(--sp-4);
  box-sizing: border-box;
}

.section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.section__title-wrap {
  display: flex;
  align-items: center;
  gap: 7px;
  flex: 1;
  min-width: 0;
}

.section__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.section__action {
  display: flex;
  align-items: center;
  gap: 2px;
  min-height: 32px;
  padding-left: var(--sp-2);
}

.section__action-text {
  font-size: 13px;
  color: var(--c-primary);
  font-weight: 500;
}

.section__desc {
  display: block;
  font-size: 12px;
  color: var(--c-text-weak);
  line-height: 1.6;
  margin: -6px 0 var(--sp-3);
}

.section__body {
  min-width: 0;
}
</style>
