import { normalizeSessionId } from './session'

// Keep only public selector fields; never persist provider credentials or endpoints.
export function normalizeConsultationModel(model) {
    if (!model || typeof model !== 'object' || model.category !== 'chat') return null
    const id = normalizeSessionId(model.id), modelName = String(model.modelName || '').trim()
    if (!id || !modelName) return null
    return { id, modelName, modelDescribe: String(model.modelDescribe || '').trim(),
        providerCode: String(model.providerCode || ''), category: 'chat' }
}

export function consultationModelLabel(model) {
    return model && (model.modelDescribe || model.modelName) || '选择模型'
}

export async function getConsultationModels(request) {
    const response = await request('/system/model/modelList', 'GET', { category: 'chat' }, { 'content-type': 'application/json' })
    if (!response || Number(response.code) !== 200 || !Array.isArray(response.data)) {
        throw new Error(response && response.msg || '模型列表加载失败，请重试')
    }
    const models = new Map()
    response.data.forEach(item => {
        const model = normalizeConsultationModel(item)
        if (model) models.set(model.id, model)
    })
    return Array.from(models.values())
}

export async function requireConsultationModel(request, model) {
    if (!model) return null // An explicit default choice retains the assistant's server configuration.
    const selected = normalizeConsultationModel(model)
    if (!selected) throw new Error('模型信息无效，请重新选择模型')
    const models = await getConsultationModels(request)
    const current = models.find(item => item.id === selected.id)
    if (!current) throw new Error('所选模型已不可用，请重新选择模型')
    return current
}
