import store from '../store'
import setting from './config'

let authDialogShown = false
function failure(message, code) { const error = new Error(message); error.code = code; error.msg = message; return error }
export function handleAuthExpired(message = '登录已过期，请重新登录') {
    if (authDialogShown) return
    authDialogShown = true
    const keys = typeof uni.getStorageInfoSync === 'function' ? uni.getStorageInfoSync().keys || [] : []
    keys.filter(key => /^(mobileChatSession:|mobileLegalContexts:|mobileLegalFirstMessage:)/.test(key)).concat(['token','userInfo','clientId']).forEach(key => uni.removeStorageSync(key))
    if (store && store.commit) store.commit('logout')
    const go = () => uni.reLaunch({ url: '/pages/h5/login', complete() { authDialogShown = false } })
    uni.showModal({ title: '请重新登录', content: message, showCancel: false, success: go,
        fail() { authDialogShown = false; go() } })
}

/** Callback wrapping works on H5, App and mini programs without Vue-version Promise assumptions. */
export function request(service, method = 'GET', data = {}, header = { 'content-type': 'application/x-www-form-urlencoded;charset=utf-8' }, relogin = true) {
    const token = uni.getStorageSync('token'), clientId = uni.getStorageSync('clientId')
    const headers = { ...header }
    Object.keys(headers).filter(key => ['authorization','clientid'].includes(key.toLowerCase())).forEach(key => delete headers[key])
    if (token) headers.Authorization = 'Bearer ' + token
    if (clientId) headers.clientid = clientId
    return new Promise((resolve,reject) => uni.request({
        url: setting.base_url.replace(/\/+$/,'') + service, method, data, header: headers, timeout: 65000,
        success(response) {
            if (uni.getStorageSync('token') !== token) { reject(failure('登录用户已变化，请重试',401)); return }
            let body = response.data
            if (typeof body === 'string') { try { body = JSON.parse(body) } catch (_) { body = null } }
            const code = Number(body && body.code)
            if (response.statusCode === 401 || code === 401 || body && [101,102].includes(Number(body.status))) {
                const message = body && body.msg || '登录已过期，请重新登录'
                if (relogin && !/^\/auth\/(login|register)/.test(service)) handleAuthExpired(message)
                reject(failure(message,401)); return
            }
            if (response.statusCode < 200 || response.statusCode >= 300) { reject(failure(body && body.msg || '服务器暂时不可用，请稍后重试',response.statusCode)); return }
            if (!body || typeof body !== 'object') { reject(failure('服务器返回了无法识别的数据，请重试',500)); return }
            if (body.code != null && code !== 200 || Number(body.status) === 501) { reject(failure(body.msg || '操作失败，请稍后重试',code || 500)); return }
            resolve(body)
        },
        fail(error) { reject(failure(/timeout/i.test(error && error.errMsg || '') ? '请求超时，请检查网络后重试' : '网络连接失败，请稍后重试','NETWORK_ERROR')) }
    }))
}
