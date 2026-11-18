<template>
  <view class="tag" :class="'tag--' + tone + (size === 'sm' ? ' tag--sm' : '')">
    <wb-icon v-if="icon" :name="icon" :size="size === 'sm' ? 11 : 12" :color="iconColor" />
    <text class="tag__text">{{ text }}</text>
  </view>
</template>

<script>
/**
 * wb-tag — 语义标签
 *
 * 状态色遵循「不能只靠颜色传达信息」，因此标签始终带文字（可选图标）。
 * tone 取值对应设计令牌：success / warning / danger / info / neutral / accent
 */

const TONE_COLORS = {
  success: '#15803d',
  warning: '#b45309',
  danger: '#dc2626',
  info: '#1e40af',
  accent: '#d97706',
  neutral: '#5b6b88'
};

export default {
  name: 'WbTag',
  props: {
    text: { type: String, default: '' },
    tone: { type: String, default: 'neutral' },
    icon: { type: String, default: '' },
    size: { type: String, default: 'md' }
  },
  computed: {
    iconColor() {
      return TONE_COLORS[this.tone] || TONE_COLORS.neutral;
    }
  }
};
</script>

<style scoped>
.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  white-space: nowrap;
}

.tag--sm {
  padding: 1px 7px;
  font-size: 11px;
}

.tag--success {
  background-color: var(--c-success-soft);
  color: var(--c-success);
}

.tag--warning {
  background-color: var(--c-warning-soft);
  color: var(--c-warning);
}

.tag--danger {
  background-color: var(--c-danger-soft);
  color: var(--c-danger);
}

.tag--info {
  background-color: var(--c-info-soft);
  color: var(--c-info);
}

.tag--accent {
  background-color: var(--c-accent-soft);
  color: var(--c-accent);
}

.tag--neutral {
  background-color: var(--c-neutral-soft);
  color: var(--c-neutral);
}

.tag__text {
  font-size: inherit;
  color: inherit;
}
</style>
