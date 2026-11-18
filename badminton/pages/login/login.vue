<template>
  <view class="login">
    <!-- 品牌区 -->
    <view class="hero">
      <view class="hero__mark">
        <wb-icon name="target" :size="30" color="#ffffff" :stroke-width="1.7" />
      </view>
      <text class="hero__title">羽毛球比赛智能编排</text>
      <text class="hero__sub">抽签 · 分组 · 记分 · 公示</text>
    </view>

    <!-- 表单 -->
    <view class="panel">
      <view class="field">
        <text class="field__label field__label--required">用户名</text>
        <input
          class="field__input"
          v-model="form.username"
          placeholder="请输入用户名"
          placeholder-class="ph"
          :maxlength="30"
          @confirm="submit"
        />
      </view>

      <view class="field">
        <text class="field__label field__label--required">密码</text>
        <view class="pwd">
          <input
            class="pwd__input"
            v-model="form.password"
            :password="!showPwd"
            placeholder="请输入密码"
            placeholder-class="ph"
            :maxlength="30"
            @confirm="submit"
          />
          <view class="pwd__toggle" @click="showPwd = !showPwd">
            <!-- 密码明文时用开锁图标，避免"显示中却仍是闭合锁"的语义矛盾 -->
            <wb-icon
              :name="showPwd ? 'unlock' : 'lock'"
              :size="16"
              :color="showPwd ? '#1e40af' : '#8b98b0'"
            />
            <text class="pwd__toggle-text" :class="{ 'pwd__toggle-text--on': showPwd }">
              {{ showPwd ? '隐藏' : '显示' }}
            </text>
          </view>
        </view>
      </view>

      <text v-if="error" class="field__error">{{ error }}</text>

      <view
        class="btn btn--primary btn--block"
        :class="{ 'btn--pressed': submitting, 'btn--disabled': submitting }"
        @click="submit"
      >
        <text class="btn__text">{{ submitting ? '登录中…' : '登录' }}</text>
      </view>

      <view class="panel__foot">
        <text class="t-sub">还没有账号？</text>
        <text class="link" @click="goRegister">立即注册</text>
      </view>
    </view>

    <!-- 演示账号：降低评审与试用的上手成本 -->
    <view class="demo">
      <view class="demo__head">
        <wb-icon name="info" :size="13" color="#8b98b0" />
        <text class="demo__title">演示账号（点击填入）</text>
      </view>
      <view class="demo__list">
        <view v-for="acc in demoAccounts" :key="acc.username" class="demo__item" @click="fill(acc)">
          <text class="demo__role">{{ acc.role }}</text>
          <text class="demo__cred">{{ acc.username }} / {{ acc.password }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { login } from '../../utils/api';
import { setCurrentUser } from '../../utils/session';

export default {
  data() {
    return {
      form: { username: '', password: '' },
      showPwd: false,
      submitting: false,
      error: '',
      demoAccounts: [
        { role: '比赛发起者', username: 'wenchenyao', password: '123456' },
        { role: '比赛发起者', username: 'liuyangyang', password: '123456' },
        { role: '比赛发起者', username: 'huangyixuan', password: '123456' }
      ]
    };
  },
  methods: {
    fill(acc) {
      this.form.username = acc.username;
      this.form.password = acc.password;
      this.error = '';
    },
    async submit() {
      if (this.submitting) return;
      this.error = '';
      const username = (this.form.username || '').trim();
      const password = this.form.password || '';
      // 就近校验：错误紧贴字段，而不是只在顶部提示
      if (!username) {
        this.error = '请输入用户名';
        return;
      }
      if (!password) {
        this.error = '请输入密码';
        return;
      }
      this.submitting = true;
      try {
        const res = await login(username, password);
        if (res && res.user) {
          setCurrentUser(res.user);
        }
        uni.showToast({ title: '登录成功', icon: 'success', duration: 900 });
        setTimeout(() => {
          uni.switchTab({ url: '/pages/index/index' });
        }, 500);
      } catch (e) {
        this.error = (e && e.message) || '登录失败，请检查用户名与密码';
      } finally {
        this.submitting = false;
      }
    },
    goRegister() {
      uni.navigateTo({ url: '/pages/register/register' });
    }
  }
};
</script>

<style scoped>
.login {
  min-height: 100vh;
  background: linear-gradient(170deg, #1e40af 0%, #2563eb 42%, #f1f5fb 42.2%, #f1f5fb 100%);
  padding: 0 var(--sp-4) var(--sp-5);
  box-sizing: border-box;
}

.hero {
  padding: 80px 4px 34px;
}

.hero__mark {
  width: 54px;
  height: 54px;
  border-radius: 16px;
  background-color: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.hero__title {
  display: block;
  font-size: 25px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.5px;
}

.hero__sub {
  display: block;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.78);
  margin-top: 8px;
  letter-spacing: 1px;
}

.panel {
  background-color: var(--c-surface);
  border-radius: var(--r-lg);
  padding: 22px 18px 16px;
  box-shadow: var(--sh-3);
}

.ph {
  color: #b3bfd2;
  font-size: 15px;
}

.pwd {
  display: flex;
  align-items: center;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background-color: var(--c-surface);
  min-height: 44px;
  padding-right: 4px;
}

.pwd__input {
  flex: 1;
  min-width: 0;
  min-height: 42px;
  padding: 0 var(--sp-3);
  font-size: 15px;
  color: var(--c-text);
}

.pwd__toggle {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 0 10px;
  min-height: 40px;
}

.pwd__toggle-text {
  font-size: 12px;
  color: var(--c-text-weak);
}

.pwd__toggle-text--on {
  color: var(--c-primary);
  font-weight: 600;
}

.btn__text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.panel__foot {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 5px;
  margin-top: 16px;
}

.link {
  font-size: 13px;
  color: var(--c-primary);
  font-weight: 600;
}

.demo {
  margin-top: 22px;
  background-color: rgba(255, 255, 255, 0.72);
  border: 1px dashed #c3d2ea;
  border-radius: var(--r-md);
  padding: 12px 14px;
}

.demo__head {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 8px;
}

.demo__title {
  font-size: 12px;
  color: var(--c-text-weak);
}

.demo__list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.demo__item {
  display: flex;
  align-items: center;
  gap: 6px;
  background-color: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 999px;
  padding: 6px 12px;
  min-height: 32px;
}

.demo__role {
  font-size: 11px;
  color: var(--c-primary);
  font-weight: 600;
}

.demo__cred {
  font-size: 12px;
  color: var(--c-text-sub);
  font-variant-numeric: tabular-nums;
}
</style>
