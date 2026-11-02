import App from './App'

// #ifndef VUE3
import Vue from 'vue'
import ReviewMessage from './components/review-message.vue'
Vue.component('review-message', ReviewMessage)
import LawyerOnboarding from './components/lawyer-onboarding.vue'
Vue.component('lawyer-onboarding', LawyerOnboarding)
import WorkTimer from './components/work-timer.vue'
Vue.component('work-timer', WorkTimer)
import CaseArchive from './components/case-archive.vue'
Vue.component('case-archive', CaseArchive)
import CaseAssociation from './components/case-association.vue'
Vue.component('case-association', CaseAssociation)
import UsageSummary from './components/usage-summary.vue'
Vue.component('usage-summary', UsageSummary)
import store from './store'
//挂载vuex
Vue.prototype.$store = store
Vue.config.productionTip = false
App.mpType = 'app'
const app = new Vue({
    ...App
})
import uView from '@/uni_modules/uview-ui'
import api from "@/common/api"
import {
  request,
  checkAuth,
  handleAuthExpired,
} from './common/index';
Vue.use(uView)
// uView's JS icon/loading defaults accompany the Sass theme.
Object.assign(uni.$u.color, {primary:'#F7B51C', success:'#67C23A', warning:'#E6A23C', error:'#F56C6C', mainColor:'#303133', contentColor:'#606266', borderColor:'#E4E7ED'})
Vue.prototype.$request = request;
Vue.prototype.$checkAuth = checkAuth;
Vue.prototype.$handleAuthExpired = handleAuthExpired;
Vue.prototype.$api = api;

Vue.prototype.$failToast = function (tip = '请求失败,请稍后再试~', icon = 'none') {
  uni.showToast({
    title: tip,
    icon
  })
}
uni.getSystemInfo({
	success(e) {
		Vue.prototype.statusBarHeight = e.statusBarHeight;
		Vue.prototype.platform = e.platform;
	}
})
app.$mount()
// #endif

// #ifdef VUE3
import { createSSRApp } from 'vue'
export function createApp() {
  const app = createSSRApp(App)
  return {
    app
  }
}
// #endif
