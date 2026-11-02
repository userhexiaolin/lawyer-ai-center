<template>
	<view class="register">
		<view class="container">
			<!-- logo -->
			<view class="logo">
				<u-avatar src="/static/logo.png" shape="square" size="100"></u-avatar>
			</view>
			<!-- title -->
			<view class="title">
				<h2>{{title}}</h2>
			</view>
			<!-- 用户名密码表单 -->
			<view class="form">
				<u--form labelPosition="left" :model="model" :rules="rules" ref="registerForm" labelWidth="80">
					<u-form-item label="账户名称" prop="user.username" borderBottom
						customStyle="margin-bottom: 10px">
						<u--input placeholder="请输入账户名称" v-model="model.user.username" border="none" placeholder-style="color:#606266;font-size:16px;" fontSize="16" customStyle="min-height:48px;box-sizing:border-box;padding:0 8px;"></u--input>
					</u-form-item>
					<u-form-item label="密码" prop="user.password" borderBottom customStyle="margin: 10px 0">
						<u--input placeholder="请输入密码" v-model="model.user.password" border="none" password clearable placeholder-style="color:#606266;font-size:16px;" fontSize="16" customStyle="min-height:48px;box-sizing:border-box;padding:0 8px;">
						</u--input>
					</u-form-item>
					<u-form-item label="确认密码" prop="user.passwordVerify" borderBottom customStyle="margin: 10px 0">
						<u--input placeholder="请确认密码" v-model="model.user.passwordVerify" border="none" password clearable placeholder-style="color:#606266;font-size:16px;" fontSize="16" customStyle="min-height:48px;box-sizing:border-box;padding:0 8px;">
						</u--input>
					</u-form-item>
				</u--form>
				<u-button type="primary" text="注册" customStyle="margin-top:32px;height:44px;font-size:16px;" shape="circle" @click="submit">
				</u-button>
			</view>
			<!-- 脚注功能 -->
			<view class="footer">
				<text class="footer-text" @click="hasAccount">已有账号</text>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		data() {
			let _this = this
			return {
				title: '与律行',
				submitting: false,
				model: {
					user: {
						username: '',
						password: '',
						passwordVerify: '',
					},
				},
				rules: {
					'user.username': [{
							required: true,
							message: '请输入手机号',
							trigger: ['change', 'blur'],
						},
						{
							validator: (rule, value, callback) => {
								return uni.$u.test.mobile(value);
							},
							message: '手机号码不正确',
							trigger: ['blur'],
						}
					],
					'user.password': [{
						type: 'string',
						min: 6,
						max: 16,
						required: true,
						message: '请输入6-16位密码',
						trigger: ['blur']
					}],
					'user.passwordVerify': [{
						type: 'string',
						min: 6,
						max: 16,
						required: true,
						message: '请输入6-16位确认密码',
						trigger: ['blur']
					},
					{
						validator: (rule, value, callback) => {
							return value === _this.model.user.password;
						},
						message: '两次密码不一致',
						trigger: ['blur'],
					}]
				}
			}
		},
		onLoad() {

		},
		methods: {
			// 注册提交
			async submit() {
				if (this.submitting) return;
				this.submitting = true;
				try {
					await this.$refs.registerForm.validate();
					const user = this.model.user;
					if (user.password !== user.passwordVerify) {
						uni.$u.toast('两次密码不一致');
						return;
					}
					const res = await this.$api.register({ username: user.username.trim(), password: user.password });
					if (Number(res.code) === 200) {
						uni.$u.toast('注册成功');
						uni.$u.route('/pages/h5/login');
					} else {
						uni.$u.toast(res.msg || '注册失败，请稍后重试');
					}
				} catch (error) {
					// 表单校验失败由组件展示；请求失败给出可读提示。
					if (!Array.isArray(error)) uni.$u.toast((error && (error.msg || error.message)) || '注册失败，请稍后重试');
				} finally {
					this.submitting = false;
				}
			},
			// 已有账号
			hasAccount(){
        uni.$u.route('/pages/h5/login');
			}
		}
	}
</script>

<style lang="scss">
	.register {
		.container {
			background: #fff;
			width: 100vw;
			padding-top: 10vh;
      /* #ifndef H5 */
      height: 90vh;
      /* #endif */
      .logo {
				display: flex;
				justify-content: center;
				align-items: flex-end;
				width: 100vw;
			}

			.title {
				text-align: center;
				margin-top: 20px;
			}

			.form {
				padding: 0 30px;
				margin-top: 40px;
			}

			.footer {
				flex-direction: row;
				justify-content: center;
				text-align: center;
				align-items: center;
				margin-top: 40px;

				.footer-text {
					font-size: 14px;
					color: #296db5;
					padding: 15px;
				}
			}
		}
	}
</style>
