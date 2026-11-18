import Vue from 'vue'
import App from './App.vue'

// 关闭生产提示（可选）
Vue.config.productionTip = false

const app = new Vue({
  render: h => h(App)
})

app.$mount('#app')