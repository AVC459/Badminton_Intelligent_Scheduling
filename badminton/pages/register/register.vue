<template>
  <view class="container">
    <view class="head">
      <text class="head__title">创建账号</text>
      <text class="head__sub">系统无需鉴权分级：注册后即为「比赛发起者」，可直接创建赛事、导入选手并执行抽签编排。</text>
    </view>

    <view class="card">
      <view class="field">
        <text class="field__label field__label--required">用户名</text>
        <input
          class="field__input"
          v-model="form.username"
          placeholder="2-20 个字符，登录时使用"
          placeholder-class="ph"
          :maxlength="20"
        />
      </view>

      <view class="field">
        <text class="field__label field__label--required">密码</text>
        <input
          class="field__input"
          v-model="form.password"
          password
          placeholder="至少 6 位"
          placeholder-class="ph"
          :maxlength="20"
        />
        <text class="field__helper">密码长度不足 6 位时无法提交</text>
      </view>

      <view class="field">
        <text class="field__label field__label--required">确认密码</text>
        <input
          class="field__input"
          v-model="form.confirm"
          password
          placeholder="请再次输入密码"
          placeholder-class="ph"
          :maxlength="20"
        />
      </view>

      <view class="field">
        <text class="field__label">姓名 / 昵称</text>
        <input
          class="field__input"
          v-model="form.real_name"
          placeholder="用于展示（可选）"
          placeholder-class="ph"
          :maxlength="20"
        />
      </view>

      <view class="field">
        <text class="field__label">手机号</text>
        <input
          class="field__input"
          v-model="form.phone"
          type="number"
          placeholder="选填，用于赛前通知"
          placeholder-class="ph"
          :maxlength="15"
        />
      </view>

      <text v-if="error" class="field__error">{{ error }}</text>

      <view class="btn btn--primary btn--block" :class="{ 'btn--disabled': submitting }" @click="submit">
        <text class="btn__text">{{ submitting ? '提交中…' : '注册' }}</text>
      </view>
    </view>

    <view class="foot">
      <text class="t-sub">已有账号？</text>
      <text class="link" @click="goLogin">返回登录</text>
    </view>
  </view>
</template>

<script>
import { registerUser } from '../../utils/api';

export default {
  data() {
    return {
      form: {
        username: '',
        password: '',
        confirm: '',
        real_name: '',
        phone: ''
      },
      submitting: false,
      error: ''
    };
  },
  methods: {
    async submit() {
      if (this.submitting) return;
      this.error = '';
      const f = this.form;
      const username = (f.username || '').trim();
      if (username.length < 2) {
        this.error = '用户名至少 2 个字符';
        return;
      }
      if (!f.password || f.password.length < 6) {
        this.error = '密码至少 6 位';
        return;
      }
      if (f.password !== f.confirm) {
        this.error = '两次输入的密码不一致';
        return;
      }
      this.submitting = true;
      try {
        await registerUser({
          username,
          password: f.password,
          real_name: (f.real_name || '').trim(),
          phone: (f.phone || '').trim()
        });
        uni.showToast({ title: '注册成功，请登录', icon: 'success', duration: 1200 });
        setTimeout(() => this.goLogin(), 800);
      } catch (e) {
        this.error = (e && e.message) || '注册失败';
      } finally {
        this.submitting = false;
      }
    },
    goLogin() {
      const pages = getCurrentPages();
      if (pages.length > 1) {
        uni.navigateBack();
      } else {
        uni.reLaunch({ url: '/pages/login/login' });
      }
    }
  }
};
</script>

<style scoped>
.head {
  padding: 6px 4px 18px;
}

.head__title {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: var(--c-text);
}

.head__sub {
  display: block;
  font-size: 13px;
  color: var(--c-text-sub);
  margin-top: 7px;
  line-height: 1.6;
}

.ph {
  color: #b3bfd2;
  font-size: 15px;
}

.btn__text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.foot {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 5px;
  padding: 18px 0 8px;
}

.link {
  font-size: 13px;
  color: var(--c-primary);
  font-weight: 600;
}
</style>
