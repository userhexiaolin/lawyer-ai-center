import setting from './config'
import { handleAuthExpired } from './request'

function uploadError(message, code) {
    const error = new Error(message)
    error.code = code
    return error
}

// 与 SecurityConfig / LoginHelper.CLIENT_KEY 一致，必须使用登录时的 clientId。
export function getUploadHeaders(extra = {}) {
    const token = uni.getStorageSync('token')
    const clientId = uni.getStorageSync('clientId')
    if (!token) throw uploadError('请先登录后再上传文件', 401)
    if (!clientId) throw uploadError('缺少客户端登录信息，请重新登录', 401)
    const headers = {}
    Object.keys(extra).forEach(key => {
        // 鉴权头以当前登录信息为准；multipart 的 boundary 由上传实现生成。
        if (!['authorization', 'clientid', 'content-type'].includes(key.toLowerCase())) headers[key] = extra[key]
    })
    return { ...headers, Authorization: 'Bearer ' + token, clientid: String(clientId) }
}

function parseUploadResponse(raw, statusCode = 200, resultType = 'file') {
    let result = raw
    if (typeof raw === 'string') {
        try { result = JSON.parse(raw) }
        catch (error) { result = null }
    }
    if (Number(statusCode) === 401 || (result && Number(result.code) === 401)) {
        if (typeof handleAuthExpired === 'function') handleAuthExpired('登录已失效，请重新登录')
        throw uploadError('登录已失效或客户端信息不匹配，请重新登录', 401)
    }
    if (Number(statusCode) < 200 || Number(statusCode) >= 300) {
        throw uploadError((result && result.msg) || '文件上传失败，请稍后重试', Number(statusCode))
    }
    if (!result || typeof result !== 'object') throw uploadError('上传响应格式异常，请重试', 500)
    if (Number(result.code) !== 200 || !result.data) {
        throw uploadError(result.msg || '文件上传失败，请稍后重试', Number(result.code) || 500)
    }
    if (resultType === 'text') {
        if (typeof result.data.text !== 'string' || !result.data.text.trim()) throw uploadError('未识别到合同文字，请手动补充正文', 500)
        return result
    }
    const fileUrl = result.data.url || result.data.fileUrl
    if (typeof fileUrl !== 'string' || !fileUrl.trim()) throw uploadError('上传未返回有效文件地址，请重试', 500)
    return { ...result, data: { ...result.data, url: fileUrl, fileUrl } }
}

/**
 * 统一上传到后端 OSS：路径用于 H5 图片、App/小程序图片和录音；Blob 用于 H5 录音。
 * 返回与普通接口一致的 R 对象，data.url/data.fileUrl 均为后端返回的地址。
 */
export async function uploadFile({ filePath, blob, fileName = 'upload', baseUrl = setting.base_url,
    url, formData = {}, headers = {}, resultType = 'file', timeout = resultType === 'text' ? 180000 : 65000 } = {}) {
    const header = getUploadHeaders(headers)
    const uploadUrl = url || baseUrl.replace(/\/+$/, '') + '/resource/oss/upload'
    if (blob != null) {
        if (blob.size > 10 * 1024 * 1024) throw uploadError('文件过大，请选择不超过10MB的文件', 400)
        // #ifdef H5
        const body = new FormData()
        body.append('file', blob, fileName)
        Object.keys(formData).forEach(key => {
            if (key !== 'file') body.append(key, formData[key])
        })
        const controller = typeof AbortController === 'function' ? new AbortController() : null
        const timer = controller ? setTimeout(() => controller.abort(), timeout) : null
        try {
            const response = await fetch(uploadUrl, { method: 'POST', headers: header, body, signal: controller ? controller.signal : undefined })
            return parseUploadResponse(await response.text(), response.status, resultType)
        } catch (error) {
            if (error.code) throw error
            if (error.name === 'AbortError') throw uploadError('上传超时，请检查网络后重试', 'UPLOAD_TIMEOUT')
            throw uploadError('文件上传失败，请检查网络后重试', 'UPLOAD_NETWORK_ERROR')
        } finally { if (timer) clearTimeout(timer) }
        // #endif
        // #ifndef H5
        throw uploadError('当前平台请使用文件路径上传', 400)
        // #endif
    }
    if (typeof filePath !== 'string' || !filePath.trim()) throw uploadError('请选择需要上传的文件', 400)
    // 使用回调封装，兼容不同端及 Vue 版本的 uni.uploadFile Promise 返回形式。
    return new Promise((resolve, reject) => {
        uni.uploadFile({
            url: uploadUrl, filePath, name: 'file', header, formData, timeout,
            success(response) {
                try { resolve(parseUploadResponse(response.data, response.statusCode, resultType)) }
                catch (error) { reject(error) }
            },
            fail() { reject(uploadError('文件上传失败，请检查网络后重试', 'UPLOAD_NETWORK_ERROR')) }
        })
    })
}
