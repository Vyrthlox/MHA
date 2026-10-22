import service from '@/utils/request'

// 会话分页列表（后端同时接受 page/size 与 currentPage/pageSize）
export function getChatSessions(params) {
    return service.get('/psychological-chat/sessions', { params })
}

// 新建会话
export function createChatSession() {
    return service.post('/psychological-chat/sessions')
}

// 某个会话的完整消息历史
export function getChatMessages(sessionId) {
    return service.get(`/psychological-chat/sessions/${sessionId}/messages`)
}

// 发送消息并同步等待 AI 回复。
// 必须单独放宽超时：request.js 默认 5 秒，而大模型生成一段回复通常要 5～30 秒。
export function sendChatMessage(sessionId, content) {
    return service.post(
        `/psychological-chat/sessions/${sessionId}/messages`,
        { content },
        { timeout: 120000 }
    )
}

// 删除会话（连带消息）
export function deleteChatSession(sessionId) {
    return service.delete(`/psychological-chat/sessions/${sessionId}`)
}
