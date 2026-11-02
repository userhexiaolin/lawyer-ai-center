import setting from './config'
import { getUploadHeaders } from './upload'
import { handleAuthExpired } from './request'
const maxBytes = 10 * 1024 * 1024
export function validateTemplateFile(file) {
  if (!file || !/\.(docx|doc|rtf|odt|pdf|txt|md)$/i.test(file.name || '')) throw new Error('请选择Word、PDF、TXT或Markdown模板')
  if (Number(file.size) > maxBytes) throw new Error('模板文件不能超过10MB')
}
export function parseTemplateUpload(body, status, owner) {
  if (uni.getStorageSync('token') !== owner) throw new Error('登录用户已变化，请重新选择文件')
  let value = body
  if (typeof value === 'string') { try { value = JSON.parse(value) } catch (_) { value = null } }
  if (Number(status) === 401 || value && Number(value.code) === 401) { handleAuthExpired(); throw new Error('登录已失效，请重新登录') }
  if (!value || Number(status) < 200 || Number(status) >= 300 || Number(value.code) !== 200 || !value.data || !value.data.sourceVersion) throw new Error(value && value.msg || '上传或转换失败，原模板与编辑已保留')
  return value.data
}
export function uploadTemplateFile(file, type, onTask) {
  validateTemplateFile(file)
  const owner = uni.getStorageSync('token')
  return new Promise((resolve, reject) => {
    const task = uni.uploadFile({
      url: setting.base_url.replace(/\/+$/, '') + '/legal/doc-template/upload',
      filePath: file.path || file.tempFilePath, name: 'file', header: getUploadHeaders(),
      formData: { type, parse: 'true' }, timeout: 660000,
      success(response) { try { resolve(parseTemplateUpload(response.data, response.statusCode, owner)) } catch (error) { reject(error) } },
      fail() { reject(new Error('上传未完成，当前编辑已保留，请重试')) }
    })
    if (onTask) onTask(task)
  })
}
export function nativeUploadOptions(type) {
  return { url: setting.base_url.replace(/\/+$/, '') + '/legal/doc-template/upload', header: getUploadHeaders(), type, maxBytes }
}
function readError(buffer) {
  try {
    const bytes = new Uint8Array(buffer)
    if (bytes.length > 65536) return null
    return JSON.parse(decodeURIComponent(Array.from(bytes).map(value => '%' + value.toString(16).padStart(2, '0')).join('')))
  } catch (_) { return null }
}
export function validBinary(buffer, format) {
  const bytes = new Uint8Array(buffer || [])
  return format === 'pdf' ? String.fromCharCode.apply(null, Array.from(bytes.slice(0, 5))) === '%PDF-' : bytes[0] === 80 && bytes[1] === 75 && bytes[2] === 3 && bytes[3] === 4
}
export function requestDocumentFile(path, data, format, method = 'POST') {
  const owner = uni.getStorageSync('token'), headers = getUploadHeaders({ 'content-type': 'application/json' })
  return new Promise((resolve, reject) => uni.request({
    url: setting.base_url.replace(/\/+$/, '') + path, method, data, header: headers, responseType: 'arraybuffer', timeout: 660000,
    success(response) {
      if (owner !== uni.getStorageSync('token')) { reject(new Error('登录用户已变化')); return }
      if (response.statusCode >= 200 && response.statusCode < 300 && validBinary(response.data, format)) { resolve(response.data); return }
      const error = readError(response.data)
      if (response.statusCode === 401 || error && Number(error.code) === 401) handleAuthExpired()
      reject(new Error(error && error.msg || '未收到有效文档，请核对当前模板和草稿后重试'))
    },
    fail() { reject(new Error('文档下载失败，请检查网络后重试')) }
  }))
}
export function openDocumentFile(buffer, format, title) {
  const name = String(title || '文书草稿').replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').slice(0, 60) + '-' + Date.now() + '.' + format
  return new Promise((resolve, reject) => {
    // #ifdef H5
    if (typeof document !== 'undefined') {
      const url = URL.createObjectURL(new Blob([buffer], { type: format === 'pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }))
      const link = document.createElement('a'); link.href = url; link.download = name; document.body.appendChild(link); link.click(); link.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000); resolve(); return
    }
    // #endif
    // #ifdef MP-WEIXIN
    if (typeof wx !== 'undefined' && wx.getFileSystemManager) {
      const filePath = wx.env.USER_DATA_PATH + '/' + name
      wx.getFileSystemManager().writeFile({ filePath, data: buffer, success() { uni.openDocument({ filePath, fileType: format, showMenu: true, success: resolve, fail: () => reject(new Error('文件已生成，暂时无法打开')) }) }, fail: () => reject(new Error('文件保存失败，请检查设备空间')) }); return
    }
    // #endif
    // #ifdef APP-PLUS
    if (typeof plus !== 'undefined') {
      try {
        const target = plus.io.convertLocalFileSystemURL('_doc/' + name), base64 = uni.arrayBufferToBase64(buffer)
        if (plus.os.name === 'Android') {
          const Base64 = plus.android.importClass('android.util.Base64'), FileOutputStream = plus.android.importClass('java.io.FileOutputStream')
          const stream = new FileOutputStream(target)
          try { stream.write(Base64.decode(base64, 0)) } finally { stream.close() }
        } else if (plus.os.name === 'iOS') {
          const NSData = plus.ios.importClass('NSData'), instance = plus.ios.invoke(NSData, 'alloc')
          const data = plus.ios.invoke(instance, 'initWithBase64EncodedString:options:', base64, 0)
          const saved = plus.ios.invoke(data, 'writeToFile:atomically:', target, true); plus.ios.deleteObject(data)
          if (!saved) throw new Error('设备文件保存失败')
        } else { throw new Error('当前设备请使用复制正文') }
        plus.runtime.openFile(target, {}, () => uni.showModal({title:'文档已保存',content:'当前设备暂时无法打开此格式，请使用系统文件应用查看：'+name,showCancel:false})); resolve({saved:true,openRequested:true}); return
      } catch (error) { reject(new Error(error.message || '设备文档打开失败')); return }
    }
    // #endif
    reject(new Error('当前平台暂不能打开文档，可复制正文继续处理'))
  })
}
