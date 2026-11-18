<template>
  <view class="stat" :class="{ 'stat--pressed': pressed }" @click="onTap">
    <view class="stat__top">
      <view class="stat__icon" :style="{ backgroundColor: softColor }">
        <wb-icon :name="icon" :size="16" :color="color" />
      </view>
      <wb-icon
        v-if="clickable"
        name="chevronRight"
        :size="14"
        color="#a9b6cc"
        class="stat__chevron"
      />
    </view>
    <text class="stat__value t-num" :style="{ color: valueColor }">{{ value }}</text>
    <text class="stat__label">{{ label }}</text>
    <text v-if="hint" class="stat__hint">{{ hint }}</text>
  </view>
</template>

<script>
/**
 * wb-stat — KPI 指标卡
 *
 * 数据看板的基础单元：图标 + 主数值 + 说明。
 * 数值使用 tabular-nums，避免数字变化时列宽跳动。
 */
export default {
  name: 'WbStat',
  props: {
    icon: { type: String, default: 'chart' },
    value: { type: [String, Number], default: 0 },
    label: { type: String, default: '' },
    hint: { type: String, default: '' },
    color: { type: String, default: '#1e40af' },
    softColor: { type: String, default: '#e8effc' },
    valueColor: { type: String, default: '#16233f' },
    clickable: { type: Boolean, default: false }
  },
  data() {
    return { pressed: false };
  },
  methods: {
    onTap() {
      if (!this.clickable) return;
      this.pressed = true;
      setTimeout(() => {
        this.pressed = false;
      }, 140);
      this.$emit('click');
    }
  }
};
</script>

<style scoped>
.stat {
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 12px;
  box-shadow: var(--sh-1);
  transition: opacity 0.16s ease-out;
  box-sizing: border-box;
}

.stat--pressed {
  opacity: 0.7;
}

.stat__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.stat__icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat__value {
  display: block;
  font-size: 24px;
  line-height: 1.15;
}

.stat__label {
  display: block;
  font-size: 12px;
  color: var(--c-text-sub);
  margin-top: 2px;
}

.stat__hint {
  display: block;
  font-size: 11px;
  color: var(--c-text-weak);
  margin-top: 3px;
}
</style>
