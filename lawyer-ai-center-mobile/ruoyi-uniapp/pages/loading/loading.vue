<template>
	<view>
		<view class="spinner-container">
			<view class="spinner spinner--rotate-square-2">
			</view>
			<view class="site-title">
				{{ 'loading...' }}
			</view>
		</view>

	</view>
</template>

<script>
	import {
		mapState,
		mapMutations
	} from 'vuex'
	export default {
		data() {
			return {

			}
		},
		onLoad() {
			// 小程序与 H5 统一走账号密码登录：有 token 校验有效性，无 token 跳登录页
			let token = uni.getStorageSync('token')
			if (token) {
				// Token 存在，验证其有效性
				this.verifyToken(token)
			} else {
				// 没有 Token，跳转到登录页
				uni.reLaunch({
					url: '../h5/login'
				});
			}
		},
		computed: {

		},
		watch: {

		},
		methods: {
			/**
			 * 验证 Token 有效性
			 * @param {String} token - 要验证的 token
			 */
			async verifyToken(token) {
				try {
					// 调用任意一个需要认证的 API 来验证 token
					// 这里以获取用户信息为例
					const res = await this.$request('/system/user/profile', 'GET', {}, {
						'content-type': 'application/json'
					})

					// Token 有效，跳转到首页
					console.log('Token 验证成功')
					uni.reLaunch({
						url: '../index/assistant'
					})
				} catch (error) {
					console.log('Token 验证失败:', error)
					// Token 无效或过期，清除缓存并跳转到登录页
					this.clearAuthAndRedirectToLogin('Token 已过期，请重新登录')
				}
			},

			/**
			 * 清除认证信息并跳转到登录页
			 * @param {String} msg - 提示信息
			 */
			clearAuthAndRedirectToLogin(msg = '认证信息已过期，请重新登录') {
				// 清除本地存储
				uni.removeStorageSync('token')
				uni.removeStorageSync('userInfo')

				// 清除 Vuex 状态
				if (this.$store && this.$store.commit) {
					this.$store.commit('setToken', '')
					this.$store.commit('setUserInfo', {})
				}

				// 显示提示并跳转到登录页
				uni.showToast({
					title: msg,
					icon: 'none',
					duration: 2000,
					success: () => {
						setTimeout(() => {
							uni.reLaunch({
								url: '../h5/login'
							})
						}, 1000)
					}
				})
			}
		}
	}
</script>

<style lang="scss">
	.site-title {
		padding-top: 40rpx;
		font-size: 16px;
		color: #41b883;
	}

	.spinner-container {
		flex-direction: column;
		height: 100vh;
		width: 100vw;
		overflow: hidden;
		display: flex;
		justify-content: center;
		align-items: center;
	}

	.spinner {
		position: relative;
		width: 60px;
		height: 60px;
	}

	.spinner::before {
		content: "";
		width: 100%;
		height: 20%;
		min-width: 5px;
		background: #000;
		opacity: .1;
		position: absolute;
		bottom: 0;
		left: 0;
		border-radius: 50%;
		animation: rotate-square-2-shadow .5s linear infinite;
	}

	.spinner::after {
		content: "";
		width: 100%;
		height: 100%;
		background: #41b883;
		animation: rotate-square-2-animate .5s linear infinite;
		position: absolute;
		bottom: 40%;
		left: 0;
		border-radius: 3px;
	}

	@keyframes rotate-square-2-animate {
		17% {
			border-bottom-right-radius: 3px;
		}

		25% {
			transform: translateY(20%) rotate(22.5deg);
		}

		50% {
			transform: translateY(40%) scale(1, .9) rotate(45deg);
			border-bottom-right-radius: 50%;
		}

		75% {
			transform: translateY(20%) rotate(67.5deg);
		}

		100% {
			transform: translateY(0) rotate(90deg);
		}
	}

	@keyframes rotate-square-2-shadow {

		0%,
		100% {
			transform: scale(1, 1);
		}

		50% {
			transform: scale(1.2, 1);
		}
	}
</style>