<template>
  <view class="empty">
    <view class="empty__icon">
      <wb-icon :name="icon" :size="30" color="#a9b6cc" :stroke-width="1.6" />
    </view>
    <text class="empty__title">{{ title }}</text>
    <text v-if="desc" class="empty__desc">{{ desc }}</text>
    <view v-if="actionText" class="empty__action" @click="$emit('action')">
      <wb-icon name="plus" :size="14" color="#ffffff" :stroke-width="2.4" />
      <text class="empty__action-text">{{ actionText }}</text>
    </view>
  </view>
</template>

<script>
/**
 * wb-empty — 空状态
 *
 * 规范要求：空状态必须给出「为什么空 + 下一步做什么」，
 * 而不是只显示一句「暂无数据」。
 */
export default {
  name: 'WbEmpty',
  props: {
    icon: { type: String, default: 'inbox' },
    title: { type: String, default: '暂无数据' },
    desc: { type: String, default: '' },
    actionText: { type: String, default: '' }
  }
};
</script>

<style scoped>
.empty {
  padding: 52px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty__icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background-color: var(--c-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}

.empty__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text-sub);
}

.empty__desc {
  font-size: 12px;
  color: var(--c-text-weak);
  margin-top: 6px;
  text-align: center;
  line-height: 1.6;
}

.empty__action {
  margin-top: 18px;
  display: flex;
  align-items: center;
  gap: 5px;
  min-height: 40px;
  padding: 0 18px;
  border-radius: 999px;
  background-color: var(--c-primary);
}

.empty__action-text {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}
</style>
