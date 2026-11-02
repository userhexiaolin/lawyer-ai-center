<template>
  <view class="login">
    <view class="container">
      <!-- logo -->
      <view class="logo">
        <u-avatar src="/static/logo.png" shape="square" size="100"></u-avatar>
      </view>
      <!-- title -->
      <view class="title">
        <h2>{{ title }}</h2>
      </view>
      <!-- 用户名密码表单 -->
      <view class="form">
        <u--form labelPosition="left" :model="model" ref="loginForm" labelWidth="80">
          <u-form-item label="用户名称" prop="user.username" borderBottom customStyle="margin-bottom: 10px">
            <u--input placeholder="请输入用户名称" v-model="model.user.username" border="none" placeholder-style="color:#606266;font-size:16px;" fontSize="16" customStyle="min-height:48px;box-sizing:border-box;padding:0 8px;"></u--input>
          </u-form-item>
          <u-form-item label="密码" prop="user.password" borderBottom customStyle="margin: 10px 0">
            <u--input placeholder="请输入密码" v-model="model.user.password" border="none" password clearable placeholder-style="color:#606266;font-size:16px;" fontSize="16" customStyle="min-height:48px;box-sizing:border-box;padding:0 8px;">
            </u--input>
          </u-form-item>
        </u--form>
        <u-button type="primary" text="登录" customStyle="margin-top:32px;height:44px;font-size:16px;" shape="circle" @click="submit">
        </u-button>
      </view>
      <!-- 脚注功能 -->
<!--      <view class="footer">
        <text class="footer-text" @click="register">账号注册</text>
      </view> -->
    </view>
  </view>
</template>

<script>

export default {
  data() {
    return {
      // 标题
      title: '与律行',
      // 找回密码modal显示与否
      modalShow: false,
      // 客户端配置：对应后端 sys_client 表
      // app 客户端 clientId=428a8310cd442757ae699df5d894f051，授权类型 password,sms,social
      clientId: '428a8310cd442757ae699df5d894f051',
      grantType: 'password',
      // 默认租户编号（后端开启多租户时必填，000000 为系统默认租户）
      tenantId: '000000',
      // 找回密码
      retrievePwd:{
        // 用户名
        username:'',
        // 邮箱
        email:''
      },
      model: {
        // 用户对象
        user: {
          username: '',
          password: '',
        },
      },
    }
  },
  onLoad() {

  },
  methods: {
    // 登录提交
	async submit() {
		const username = this.model.user.username.trim();
		const password = this.model.user.password;
		if (!username || !password) {
			uni.$u.toast('请输入用户名和密码');
			return;
		}
		// 后端 /auth/login 要求 LoginBody 携带 clientId + grantType
		this.$request('/auth/login', 'Post', {
			clientId: this.clientId,
			grantType: this.grantType,
			tenantId: this.tenantId,
			username,
			password
		}, {
			'content-type': 'application/json'
		}).then(async (res) => {
			// res 为后端 R<LoginVo>，token 在 res.data.access_token
			const token = res.data && res.data.access_token;
			if (!token) {
				uni.$u.toast(res.msg || '登录失败');
				return;
			}
			uni.setStorageSync('token', token);
			// 记录本次登录的 clientId，供后续请求头 clientid 与 token 内 clientid 一致性校验
			uni.setStorageSync('clientId', this.clientId);
			// 拉取用户资料，写入缓存与 store，便于后续页面使用
			let userInfo = {};
			try {
				const profile = await this.$request('/system/user/profile', 'GET');
				if (profile.data && profile.data.user) {
					userInfo = profile.data.user;
					uni.setStorageSync('userInfo', userInfo);
				}
			} catch (e) {
				console.log('获取用户信息失败:', e);
			}
			this.$store.commit('login', { userInfo, token });
			console.log('登录成功!', res);
			uni.reLaunch({
				url: '../index/assistant'
			});
		}).catch((err) => {
			console.error('登录失败:', err);
			const msg = (err && err.msg) || err || '登录失败';
			uni.$u.toast(msg);
		});
	},
	
	
    // submit() {
    //   this.$refs.loginForm.validate().then(async res => {
    //     let param = this.model.user
    //     const result = await this.$api.login(param)
    //     if (result.success) {
    //       // 将token存入
    //       const {data} = result
    //       uni.setStorageSync("Access-Token", data.accessToken);
    //       uni.$u.route('/pages/message/message');
    //     }else{
    //       uni.$u.toast(result.message);
    //     }
    //   })
    // },
    // 账号注册
    register() {
      uni.$u.route('/pages/h5/register');
    },
  }
}
</script>

<style lang="scss">
.login {
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
