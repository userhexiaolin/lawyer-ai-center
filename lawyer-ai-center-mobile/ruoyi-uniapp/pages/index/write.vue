<template>
  <view class="upgrade-page">
    <view class="upgrade-card">
      <view class="upgrade-title">法律文书与咨询</view>
      <view class="upgrade-message">功能已升级，请使用新的法律咨询入口</view>
      <button type="primary" @click="openCurrentEntry">前往法律咨询</button>
    </view>
  </view>
</template>

<script>
// 保留历史路由兼容书签，停止旧页面的 HTTP、WebSocket 与心跳请求。
export default {
  data() { return {} },
  methods: {
    openCurrentEntry() {
      uni.navigateTo({ url: '/pages/index/home' })
    }
  }
}
</script>

<style scoped>
.upgrade-page { min-height: 100vh; padding: 80rpx 32rpx; box-sizing: border-box; background: #f6f7fb; }
.upgrade-card { padding: 48rpx 32rpx; background: #fff; border-radius: 24rpx; }
.upgrade-title { font-size:20px; font-weight: 600; margin-bottom: 24rpx; }
.upgrade-message { color: #666; font-size:16px; line-height: 1.7; margin-bottom: 40rpx; }
</style>
