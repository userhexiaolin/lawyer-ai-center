<template>
  <view class="upgrade-page">
    <view class="upgrade-card">
      <view class="upgrade-title">注册入口已升级</view>
      <view class="upgrade-message">请前往新的注册页面完成账号注册</view>
      <button type="primary" @click="openCurrentEntry">前往注册</button>
    </view>
  </view>
</template>

<script>
// 保留历史路由兼容书签，停止旧页面的 HTTP、WebSocket 与心跳请求。
export default {
  data() { return {} },
  methods: {
    openCurrentEntry() {
      uni.redirectTo({ url: '/pages/h5/register' })
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
