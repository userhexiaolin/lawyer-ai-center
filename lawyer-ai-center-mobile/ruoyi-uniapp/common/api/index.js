import { request } from '../index'

// 与当前账号密码登录页面保持相同的客户端和租户配置。
const authDefaults = {
    clientId: '428a8310cd442757ae699df5d894f051',
    grantType: 'password',
    tenantId: '000000'
}

function upgradedMessageEntry() {
    const msg = '功能已升级，请使用新的法律咨询入口'
    uni.showToast({ title: msg, icon: 'none', duration: 3000 })
    // 保留旧方法签名兼容调用方，明确返回不可用状态，不再发送旧协议请求。
    return Promise.resolve({ code: 410, msg })
}

const api = {
    login(params = {}) {
        return request('/auth/login', 'POST', { ...authDefaults, ...params }, {
            'content-type': 'application/json'
        })
    },
    register(params = {}) {
        return request('/auth/register', 'POST', { ...authDefaults, userType: 'sys_user', ...params }, {
            'content-type': 'application/json'
        })
    },
    // 聊天已统一由新聊天页使用持久化 sessionId 和 WebSocket 处理。
    message() {
        return upgradedMessageEntry()
    },
    messagePage() {
        return upgradedMessageEntry()
    }
}

export default api
