import Vue from 'vue'
import Vuex from 'vuex'


Vue.use(Vuex)

const store = new Vuex.Store({
  state: {
    userInfo: {},
    token: '',
    config: {
      site_title: '与律行'
    },
    num: 0,
    main_model: {},
    models: []
  },
  mutations: {
    login(state, payload) {
      // 兼容两种调用方式：
      // 1. login(state, userInfo) - 原始方式
      // 2. login(state, { userInfo, token }) - 新方式
      if (payload.userInfo && payload.token) {
        state.userInfo = payload.userInfo;
        state.token = payload.token;
        state.num = payload.userInfo.num || 0;
      } else {
        // 原始方式
        state.userInfo = payload;
        state.num = payload.num;
        state.token = payload.token;
      }
    },
    syncUserInfo(state, userInfo) {
      state.userInfo = userInfo;
      state.num = userInfo.num;
    },
    syncUserNum(state, num) {
      state.num = num;
    },
    useNum(state, num) {
      state.num = state.num - num;
    },
    logout(state) {
      state.userInfo = {}
      state.token = ''
      state.num = 0
    },
    setConfig(state, config) {
      state.config = config.data
      state.main_model = config.main_model
      state.models = config.models
    },
    wxH5SetToken(state, token) {
      state.token = token
    }
  }
})

export default store
