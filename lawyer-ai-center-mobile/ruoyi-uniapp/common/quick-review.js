import setting from './config'
import { createSession, normalizeSessionId, getSessionMessages } from './api/session'
import { rememberLegalContext } from './api/legal-entry'
import { uploadFile } from './upload'
import { parseReviewResult } from './review-result'

// Review messages are persisted by the same backend used by PC; no local-only reports.
export function createQuickReview(state, request, api) {
    let socket, timer, closed = false, generation = 0
    const owner = String((api.getStorageSync('userInfo') || {}).userId || '')
    const token = api.getStorageSync('token')
    const current = id => !closed && generation === id && token === api.getStorageSync('token') &&
        owner === String((api.getStorageSync('userInfo') || {}).userId || '')
    const stop = () => { clearTimeout(timer); const previous = socket; socket = null; if (previous) previous.close() }
    function fail(message, id) {
        if (!current(id)) return
        state.stage = 'error'; state.error = message; stop()
    }
    async function recognize() {
        if (closed || ['uploading','reading','creating','reviewing'].includes(state.stage)) return
        const id=++generation
        if (!owner || !token) { api.navigateTo({url:'/pages/h5/login'}); return }
        state.error='';state.raw='';state.result=null
        try {
            if (state.filePath && !state.fileUrl) {
                state.stage = 'uploading'
                const uploaded = await uploadFile({ filePath: state.filePath })
                if (!current(id)) return
                state.fileUrl = uploaded.data.fileUrl
            }
            if (state.filePath && !state.contractText.trim()) {
                state.stage = 'reading'
                const extracted = await uploadFile({ filePath: state.filePath,
                    url: setting.base_url.replace(/\/+$/, '') + '/system/contract-review/content', resultType: 'text' })
                if (!current(id)) return
                state.contractText = extracted.data.text
            }
            if (!state.contractText.trim()) throw new Error('请拍照、选择合同图片或填写合同正文')
            if (current(id)) state.stage='ready'
        } catch(error) { fail(error.message || '识别失败，请粘贴正文或重试',id) }
    }
    async function review() {
        if (closed || ['uploading', 'reading', 'creating', 'reviewing'].includes(state.stage)) return
        const id = ++generation
        if (!owner || !token) { api.navigateTo({ url: '/pages/h5/login' }); return }
        state.error = ''; state.raw = ''; state.result = null
        try {
            if (!state.contractText.trim()) throw new Error('请先识别并核对合同正文，再点击开始审查')
            const response = await request('/agent/agent/agentOptions', 'GET', {}, { 'content-type': 'application/json' })
            if (!current(id)) return
            const agent = response && response.code === 200 && Array.isArray(response.data) &&
                response.data.find(a => a.agentName === '合同审查官' && String(a.status) === '0')
            const agentId = normalizeSessionId(agent && agent.id)
            if (!agentId) throw new Error('未找到启用的合同审查官，请联系管理员')
            if (!state.sessionId) {
                state.stage = 'creating'
                const sessionId = await createSession(request, { reviewSession: true, sessionTitle: '合同快审 - ' + new Date().toLocaleDateString(), ...(state.caseId ? {caseId: state.caseId} : {}) })
                if (!current(id)) return
                state.sessionId = sessionId
                rememberLegalContext(api, owner, sessionId, { agentId, appName: '合同审查官' })
            }
            state.stage = 'reviewing'
            timer = setTimeout(() => fail('审查超时，请从历史检查已保存的结果，或重试', id), 180000)
            const activeSocket = api.connectSocket({ url: setting.ws + '?Authorization=Bearer%20' + encodeURIComponent(token), complete() {} })
            socket = activeSocket
            const live = () => current(id) && socket === activeSocket
            activeSocket.onOpen(() => {
                if (!live()) return
                activeSocket.send({ data: JSON.stringify({ sessionId: state.sessionId, agentId,
                    fileUrl: state.fileUrl, content: '请审查以下合同，严格输出已配置的JSON结构。审查重点：' +
                        (state.notes.trim() || '全面审查') + '\n文件地址：' + state.fileUrl + '\n---合同正文开始---\n' + state.contractText + '\n---合同正文结束---' }),
                    fail: () => fail('发送失败，请检查网络后重试', id) })
            })
            activeSocket.onMessage(event => {
                if (!live()) return
                if (event.data === '[DONE]') {
                    if (!state.raw.trim()) { fail('模型未返回审查内容，请重试', id); return }
                    state.result = parseReviewResult(state.raw)
                    state.stage = 'complete'; stop(); return
                }
                if (typeof event.data === 'string' && event.data.startsWith('错误')) { fail(event.data, id); return }
                try {
                    const value = JSON.parse(event.data)
                    if (value.error || (value.data && String(value.data).startsWith('错误'))) { fail(value.error || value.data, id); return }
                    if (typeof value.content === 'string') state.raw += value.content
                } catch (_) { fail('收到无法识别的审查消息，请重试', id) }
            })
            activeSocket.onError(() => { if (live()) fail('网络连接失败，请重试', id) })
            activeSocket.onClose(() => { if (live()) fail('连接中断，请从历史检查结果或重试', id) })
        } catch (error) { fail(error.message || '审查失败，请重试', id) }
    }
    async function restore(sessionId) {
        if (!normalizeSessionId(sessionId)) throw new Error('会话ID无效')
        const id = ++generation; stop(); state.stage = 'reading'; state.error = ''
        try {
            const page = await getSessionMessages(request, owner, sessionId, { pageSize: 100 })
            if (!current(id)) return
            const last = page.rows.filter(m => m.role === 'assistant').pop()
            if (!last) throw new Error('该会话尚无审查结果，可进入对话继续审查')
            state.sessionId = sessionId; state.raw = last.content
            state.result = parseReviewResult(last.content); state.stage = 'complete'
        } catch (error) { fail(error.message || '历史加载失败，请重试', id) }
    }
    return { recognize, review, restore, dispose() { closed = true; generation++; stop() } }
}
