// Inject the existing authenticated request function so token/clientid handling stays shared.
export function normalizeSessionId(value) {
    if (typeof value === 'number' && !Number.isSafeInteger(value)) return ''
    if (typeof value !== 'string' && typeof value !== 'number') return ''
    const id = String(value).trim()
    const maxId = '9223372036854775807'
    if (!/^[1-9]\d*$/.test(id) || id.length > maxId.length ||
        (id.length === maxId.length && id > maxId)) return ''
    // Keep Java Long IDs as decimal strings to avoid JavaScript precision loss.
    return id
}

export async function createSession(request, data) {
    const res = await request('/system/session', 'POST', data, {
        'content-type': 'application/json'
    })
    if (!res || Number(res.code) !== 200) {
        throw new Error((res && res.msg) || '创建会话失败，请重试')
    }
    // ChatSessionController.add returns R<Long>: { code: 200, data: id }.
    const sessionId = normalizeSessionId(res.data)
    if (!sessionId) throw new Error('后端未返回有效的会话 ID')
    return sessionId
}

// IDs may be Java Long strings: never sort them through Number().
export function compareMessageIds(a, b) {
    const left = String(a.id), right = String(b.id)
    return left.length - right.length || (left < right ? -1 : left > right ? 1 : 0)
}

export function normalizeHistoryMessage(message) {
    if (!message || typeof message !== 'object') return null
    const id = normalizeSessionId(message.id || message.messageId || message.message_id)
    const role = String(message.role || '').toLowerCase()
    if (!id || !['user', 'assistant'].includes(role)) return null
    const feedback = Number(message.feedbackType || message.feedback_type)
    return { ...message, id, role, message_id: 'history-' + id,
        content: message.content == null ? '' : String(message.content),
        createTime: message.createTime || message.create_time,
        liked: feedback === 1, disliked: feedback === 2 }
}

function readPage(res, pageNum, pageSize) {
    if (!res || Number(res.code) !== 200 || !Array.isArray(res.rows)) {
        throw new Error((res && res.msg) || '列表加载失败')
    }
    const count = res.total == null ? NaN : Number(res.total)
    const total = Number.isFinite(count) && count >= 0 ? count : null
    return { rows: res.rows, pageNum, total,
        hasMore: res.rows.length >= pageSize && (total === null || pageNum * pageSize < total) }
}

// 当前后端没有 /system/message/listForUniapp，统一接口支持会话过滤和分页。
export async function getSessionMessages(request, userId, sessionId, { pageNum = 1, pageSize = 20, isAsc = 'desc' } = {}) {
    const id = normalizeSessionId(sessionId)
    if (!id || !userId) throw new Error('会话 ID 或用户信息无效')
    const res = await request('/system/message/list', 'GET', {
        userId, sessionId: id, pageNum, pageSize,
        orderByColumn: 'id', isAsc
    }, { 'content-type': 'application/json' })
    const page = readPage(res, pageNum, pageSize)
    const ids = page.rows.map(item => ({ id: normalizeSessionId(item && item.id) })).filter(item => item.id).sort(compareMessageIds)
    return { ...page, oldestId: ids.length ? ids[0].id : '', rows: page.rows.map(normalizeHistoryMessage).filter(Boolean).sort(compareMessageIds) }
}

export async function getSessionListPage(request, { pageNum = 1, pageSize = 10 } = {}) {
    const res = await request('/system/session/list', 'GET', {
        pageNum, pageSize, orderByColumn: 'id', isAsc: 'desc'
    }, { 'content-type': 'application/json' })
    const page = readPage(res, pageNum, pageSize)
    return { ...page, rows: page.rows.filter(item => item && normalizeSessionId(item.id))
        .map(item => ({ ...item, id: normalizeSessionId(item.id) })) }
}
