import { createSession, normalizeSessionId, compareMessageIds } from './session'
import { normalizeConsultationModel, requireConsultationModel } from './consultation-model'

export const LEGAL_QUESTIONS = [
    { category: '合同履行', question: '合同对方违约了怎么办' },
    { category: '劳动权益', question: '劳动仲裁时效是多久' },
    { category: '借款纠纷', question: '借款纠纷需要哪些证据' },
    { category: '房屋租赁', question: '房屋租赁合同注意什么' },
    { category: '婚姻家庭', question: '离婚时共同财产如何分割' },
    { category: '消费维权', question: '商家拒绝退款该如何维权' }
]
const contextsKey = userId => 'mobileLegalContexts:' + userId
const firstMessageKey = (userId, sessionId) => 'mobileLegalFirstMessage:' + userId + ':' + sessionId

export function getLegalContext(storage, userId, sessionId) {
    const id = normalizeSessionId(sessionId)
    if (!id || !userId) return null
    const contexts = storage.getStorageSync(contextsKey(userId)) || {}
    const context = contexts[id]
    if (!context || !normalizeSessionId(context.agentId)) return null
    return { agentId: normalizeSessionId(context.agentId), appName: String(context.appName || ''),
        ...(context.appName === '法律咨询助手' && normalizeConsultationModel(context.consultationModel) ? { consultationModel: normalizeConsultationModel(context.consultationModel) } : {}) }
}

export function rememberLegalContext(storage, userId, sessionId, context) {
    const id = normalizeSessionId(sessionId), agentId = normalizeSessionId(context.agentId)
    if (!userId || !id || !agentId) return
    const previous = storage.getStorageSync(contextsKey(userId)) || {}
    const entries = Object.keys(previous).filter(key => normalizeSessionId(key))
        .map(key => ({ id: key, context: previous[key] })).filter(item => item.id !== id)
    const model = context.appName === '法律咨询助手' ? normalizeConsultationModel(context.consultationModel) : null
    entries.push({ id, context: { agentId, appName: String(context.appName || ''), ...(model ? { consultationModel: model } : {}) } })
    entries.sort((a, b) => compareMessageIds(b, a))
    const contexts = {}
    entries.slice(0, 100).forEach(item => { contexts[item.id] = item.context })
    storage.setStorageSync(contextsKey(userId), contexts)
}

export function consumeLegalFirstMessage(storage, userId, sessionId, agentId) {
    const id = normalizeSessionId(sessionId)
    if (!userId || !id) return ''
    const key = firstMessageKey(userId, id)
    const pending = storage.getStorageSync(key)
    if (!pending || pending.agentId !== normalizeSessionId(agentId) || typeof pending.content !== 'string') return ''
    storage.removeStorageSync(key)
    return pending.content
}

export async function createLegalSession(request, storage, { agentName = '法律咨询助手', question = '', caseId, sessionTitle, consultationModel = null } = {}) {
    const user = storage.getStorageSync('userInfo') || {}
    const owner = String(user.userId || ''), token = storage.getStorageSync('token')
    if (!normalizeSessionId(owner) || !token) throw new Error('请先登录后开始法律咨询')
    const assertOwner = () => {
        const current = storage.getStorageSync('userInfo') || {}
        if (String(current.userId || '') !== owner || storage.getStorageSync('token') !== token) {
            throw new Error('登录用户已变化，请重新开始咨询')
        }
    }
    const response = await request('/agent/agent/agentOptions', 'GET', {}, { 'content-type': 'application/json' })
    assertOwner()
    if (!response || Number(response.code) !== 200 || !Array.isArray(response.data)) {
        throw new Error('法律助手加载失败，请稍后重试')
    }
    const agent = response.data.find(item => item && item.agentName === agentName && String(item.status) === '0')
    const agentId = normalizeSessionId(agent && agent.id)
    if (!agentId) throw new Error('未找到启用的' + agentName + '，请联系管理员')
    const content = String(question || '').trim()
    if (content.length > 20000) throw new Error('内容过长，请分段咨询')
    if (consultationModel && agentName !== '法律咨询助手') throw new Error('该助手使用固定模型，请从法律咨询切换模型')
    const selectedModel = await requireConsultationModel(request, consultationModel)
    assertOwner()
    const prefix = agentName === '合同审查官' ? '合同快审' : '法律咨询'
    const sessionId = await createSession(request, {
        reviewSession: agentName === '合同审查官',
        sessionTitle: (sessionTitle || prefix + ' - ' + (content || new Date().toLocaleDateString())).slice(0, 60), sessionContent: '', ...(caseId ? {caseId: normalizeSessionId(caseId)} : {})
    })
    assertOwner()
    rememberLegalContext(storage, owner, sessionId, { agentId, appName: agentName, consultationModel: selectedModel })
    let url = '/pages/index/index?sessionId=' + encodeURIComponent(sessionId) + '&agentId=' + encodeURIComponent(agentId) + '&appName=' + encodeURIComponent(agentName)
    if (content) {
        storage.setStorageSync(firstMessageKey(owner, sessionId), { agentId, content })
        url += '&firstMessage=1'
    }
    return { sessionId, agentId, url }
}


export function legalPageTop(api) {
    let top = 16;
    try {
        const info = typeof api.getWindowInfo === 'function' ? api.getWindowInfo() : typeof api.getSystemInfoSync === 'function' ? api.getSystemInfoSync() : {};
        top += Number(info.statusBarHeight) || 0;
        if (typeof api.getMenuButtonBoundingClientRect === 'function') {
            const menu = api.getMenuButtonBoundingClientRect();
            if (menu && Number(menu.bottom) > 0) top = Math.max(top, Number(menu.bottom) + 12);
        }
    } catch (error) { /* Unsupported window APIs retain the base spacing. */ }
    return top;
}
