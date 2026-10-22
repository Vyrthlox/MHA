<template>
    <div class="chat-page">
        <!-- 左侧：会话列表 -->
        <aside class="session-panel" :class="{ collapsed: isSessionCollapse }">
            <div class="session-header">
                <span class="session-heading">对话列表</span>
                <div class="header-actions">
                    <el-button type="primary" size="small" :icon="Plus" circle @click="handleNewSession" />
                    <el-button size="small" :icon="Fold" circle @click="isSessionCollapse = true" />
                </div>
            </div>
            <div class="session-list" v-loading="loadingSessions">
                <div
                    v-for="item in sessions"
                    :key="item.id"
                    class="session-item"
                    :class="{ active: item.id === activeSessionId }"
                    @click="handleSelectSession(item.id)"
                    @contextmenu.prevent="handleSessionContextMenu($event, item)"
                >
                    <div class="session-name">{{ item.title }}</div>
                    <div class="session-preview">{{ item.preview }}</div>
                </div>
                <div v-if="!sessions.length" class="session-empty">暂无会话</div>
            </div>
        </aside>

        <!-- 右侧：对话区 -->
        <section class="chat-panel">
            <header class="chat-header">
                <el-button v-if="isSessionCollapse" size="small" :icon="Expand" circle @click="isSessionCollapse = false" />
                <el-avatar :size="32" :src="aiAvatar" />
                <span class="chat-title">{{ activeSession?.title || 'AI 心理助手' }}</span>
            </header>

            <div ref="messageListRef" class="message-list" v-loading="loadingMessages">
                <div v-if="!messages.length" class="chat-empty">
                    <el-avatar :size="64" :src="aiAvatar" />
                    <p>你好，我是 AI 心理助手，有什么想聊的都可以告诉我～</p>
                </div>
                <div
                    v-for="msg in messages"
                    :key="msg.id"
                    class="message-row"
                    :class="msg.role"
                >
                    <el-avatar class="avatar" :size="36" :src="msg.role === 'user' ? userAvatar : aiAvatar" />
                    <div class="bubble">{{ msg.content }}</div>
                </div>
                <div v-if="sending" class="message-row assistant typing-row">
                    <el-avatar class="avatar" :size="36" :src="aiAvatar" />
                    <div class="bubble typing">正在思考…</div>
                </div>
            </div>

            <footer class="chat-input">
                <el-input
                    v-model="inputText"
                    type="textarea"
                    :rows="3"
                    resize="none"
                    maxlength="2000"
                    show-word-limit
                    placeholder="请输入你想说的话，Enter 发送，Shift + Enter 换行"
                    @keydown.enter.exact="handleSendOnEnter"
                />
                <div class="input-actions">
                    <el-button
                        type="primary"
                        :loading="sending"
                        :disabled="!inputText.trim() || sending"
                        @click="handleSend"
                    >发送</el-button>
                </div>
            </footer>
        </section>

        <!-- 消息右键菜单 -->
        <Teleport to="body">
            <div
                v-if="contextMenu.visible"
                class="context-menu"
                :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
            >
                <div class="context-menu-item" @click="handleDelete">删除</div>
            </div>
        </Teleport>
    </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { Plus, Fold, Expand } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import aiAvatar from '@/img/AILog.png'
import userAvatar from '@/img/userhead.png'
import {
    getChatSessions,
    createChatSession,
    getChatMessages,
    sendChatMessage,
    deleteChatSession
} from '@/api/chat'

const inputText = ref('')
const sessions = ref([])
const messages = ref([])
const activeSessionId = ref(null)
const messageListRef = ref()
// 会话列表是否收起
const isSessionCollapse = ref(false)
// 会话右键菜单状态
const contextMenu = ref({ visible: false, x: 0, y: 0, target: null })

const loadingSessions = ref(false)
const loadingMessages = ref(false)
// 等待 AI 回复中：输入框发送按钮显示 loading，并显示「正在思考」
const sending = ref(false)

// 每切换一次会话就自增。快速点击时旧请求可能后返回，
// 只有序号仍是最新的响应才允许写入界面，避免串会话。
let messageRequestSeq = 0

const activeSession = computed(() => sessions.value.find(s => s.id === activeSessionId.value))

// 后端列表项同时返回 title/preview 与 sessionTitle/lastMessageContent 两组别名
const toSessionItem = (data) => ({
    id: data.id,
    title: data.sessionTitle || data.title || '新的对话',
    preview: data.lastMessageContent ?? data.preview ?? ''
})

// 滚动到消息列表底部
const scrollToBottom = async () => {
    await nextTick()
    const el = messageListRef.value
    if (el) el.scrollTop = el.scrollHeight
}

// autoSelect：首次进入时自动打开最近一条会话
const loadSessions = async ({ autoSelect = false } = {}) => {
    loadingSessions.value = true
    try {
        // mine=true：即使是管理员，对话页也只显示本人的会话（咨询记录页另有入口）
        const res = await getChatSessions({ page: 1, size: 50, mine: true })
        sessions.value = (res.data?.records || []).map(toSessionItem)
        if (autoSelect && !activeSessionId.value && sessions.value.length) {
            await handleSelectSession(sessions.value[0].id)
        }
    } catch (e) {
        // 错误提示由 request.js 统一处理
    } finally {
        loadingSessions.value = false
    }
}

// 切换会话并加载历史消息
const handleSelectSession = async (id) => {
    if (id === activeSessionId.value && messages.value.length) return
    activeSessionId.value = id
    messages.value = []
    const seq = ++messageRequestSeq
    loadingMessages.value = true
    try {
        const res = await getChatMessages(id)
        if (seq !== messageRequestSeq) return
        messages.value = res.data || []
        await scrollToBottom()
    } catch (e) {
        // 已提示
    } finally {
        if (seq === messageRequestSeq) loadingMessages.value = false
    }
}

// 新建会话
const handleNewSession = async () => {
    try {
        const res = await createChatSession()
        const session = toSessionItem(res.data)
        sessions.value.unshift(session)
        // 旧会话可能还有历史请求在路上，作废掉
        messageRequestSeq++
        activeSessionId.value = session.id
        messages.value = []
        loadingMessages.value = false
        await scrollToBottom()
    } catch (e) {
        // 已提示
    }
}

// Enter 发送。中文输入法回车选词时 isComposing=true，此时不能拦截默认行为
const handleSendOnEnter = (event) => {
    if (event.isComposing) return
    event.preventDefault()
    handleSend()
}

// 发送消息
const handleSend = async () => {
    const content = inputText.value.trim()
    if (!content || sending.value || loadingMessages.value) return

    sending.value = true
    inputText.value = ''
    let sessionId = activeSessionId.value

    try {
        // 还没有会话时，第一条消息顺带创建会话
        if (!sessionId) {
            const created = await createChatSession()
            sessionId = created.data.id
            sessions.value.unshift(toSessionItem(created.data))
            activeSessionId.value = sessionId
            messages.value = []
        }

        // 先乐观显示用户消息；后端在调模型前也会把它写进历史
        messages.value.push({
            id: `local-${Date.now()}`,
            role: 'user',
            content
        })
        await scrollToBottom()

        const res = await sendChatMessage(sessionId, content)
        const reply = res.data

        // 等待期间用户可能切到了别的会话，这时不要把回复塞进当前列表
        if (activeSessionId.value === sessionId) {
            messages.value.push({
                id: reply.id || `local-reply-${Date.now()}`,
                role: reply.role || 'assistant',
                content: reply.content,
                createdAt: reply.createdAt
            })
            await scrollToBottom()
        }

        // 标题 / 预览 / 计数以服务端为准（首条消息会生成标题）
        await loadSessions()
    } catch (e) {
        // 失败时不盲目撤回：模型超时的情况下用户消息可能已经入库。
        // 重新拉一次历史，以服务端为准；拉取也失败才恢复输入框。
        if (sessionId && activeSessionId.value === sessionId) {
            try {
                const history = await getChatMessages(sessionId)
                messages.value = history.data || []
            } catch (_) {
                messages.value = messages.value.filter(m => !String(m.id).startsWith('local-'))
                inputText.value = content
            }
            await scrollToBottom()
        } else if (!sessionId) {
            inputText.value = content
        }
    } finally {
        sending.value = false
    }
}

// 右键会话记录：打开菜单
const handleSessionContextMenu = (event, session) => {
    contextMenu.value = { visible: true, x: event.clientX, y: event.clientY, target: session }
}

// 关闭右键菜单
const closeContextMenu = () => {
    contextMenu.value.visible = false
}

// 删除会话
const handleDelete = async () => {
    const target = contextMenu.value.target
    closeContextMenu()
    if (!target) return

    try {
        await ElMessageBox.confirm(
            `确定删除「${target.title}」吗？聊天记录将一并删除。`,
            '删除会话',
            { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
        )
    } catch {
        return
    }

    try {
        await deleteChatSession(target.id)
        ElMessage.success('会话已删除')
        const wasActive = activeSessionId.value === target.id
        if (wasActive) {
            activeSessionId.value = null
            messages.value = []
            messageRequestSeq++
        }
        await loadSessions()
        if (wasActive) {
            if (sessions.value.length) await handleSelectSession(sessions.value[0].id)
            else await scrollToBottom()
        }
    } catch (e) {
        // 已提示
    }
}

// 按 Esc 关闭菜单
const handleEsc = (event) => {
    if (event.key === 'Escape') closeContextMenu()
}

onMounted(() => {
    // 点击任意位置 / 滚动时关闭菜单；scroll 用捕获阶段以便监听到内部滚动容器
    window.addEventListener('click', closeContextMenu)
    window.addEventListener('scroll', closeContextMenu, true)
    window.addEventListener('keydown', handleEsc)
    loadSessions({ autoSelect: true })
})

onBeforeUnmount(() => {
    window.removeEventListener('click', closeContextMenu)
    window.removeEventListener('scroll', closeContextMenu, true)
    window.removeEventListener('keydown', handleEsc)
})
</script>

<style lang="scss" scoped>
.chat-page {
    display: flex;
    height: 100%;
    background-color: #f5f7fa;
}

// 左侧会话列表
.session-panel {
    display: flex;
    flex-direction: column;
    width: 280px;
    flex-shrink: 0;
    background-color: #fff;
    border-right: 1px solid #e5e7eb;
    overflow: hidden;
    transition: width 0.2s ease;

    &.collapsed {
        width: 0;
        border-right: none;
    }

    .session-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 16px;
        border-bottom: 1px solid #e5e7eb;

        .session-heading {
            font-size: 16px;
            font-weight: 600;
            color: #2d3748;
            white-space: nowrap;
        }

        .header-actions {
            display: flex;
            align-items: center;
            gap: 6px;
        }
    }

    .session-list {
        flex: 1;
        overflow-y: auto;
        padding: 8px;

        .session-item {
            padding: 10px 12px;
            margin-bottom: 6px;
            border-radius: 8px;
            cursor: pointer;
            transition: background-color 0.2s;

            &:hover {
                background-color: #f5f7fa;
            }

            &.active {
                background-color: #ecf5ff;
            }

            .session-name {
                font-size: 14px;
                color: #2d3748;
                margin-bottom: 4px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }

            .session-preview {
                font-size: 12px;
                color: #909399;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }
        }

        .session-empty {
            padding: 16px;
            text-align: center;
            font-size: 13px;
            color: #909399;
        }
    }
}

// 右侧对话区
.chat-panel {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;

    .chat-header {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 14px 20px;
        background-color: #fff;
        border-bottom: 1px solid #e5e7eb;

        .chat-title {
            font-size: 16px;
            font-weight: 600;
            color: #2d3748;
        }
    }

    .message-list {
        flex: 1;
        overflow-y: auto;
        padding: 20px;

        .chat-empty {
            height: 100%;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 12px;
            color: #909399;

            p {
                font-size: 14px;
            }
        }

        .message-row {
            display: flex;
            align-items: flex-start;
            gap: 10px;
            margin-bottom: 18px;

            &.user {
                flex-direction: row-reverse;

                .bubble {
                    background-color: #ffb26b;
                    color: #fff;
                }
            }

            &.assistant {
                .bubble {
                    background-color: #fff;
                    color: #2d3748;
                }
            }

            .bubble {
                max-width: 70%;
                padding: 10px 14px;
                border-radius: 10px;
                font-size: 14px;
                line-height: 1.6;
                word-break: break-word;
                /* 模型回复里的换行要保留，否则整段会挤成一行 */
                white-space: pre-wrap;
                box-shadow: 0 1px 3px rgba(0, 21, 41, 0.06);
            }

            &.typing-row .typing {
                color: #909399;
                font-style: italic;
            }
        }
    }

    .chat-input {
        padding: 12px 20px 16px;
        background-color: #fff;
        border-top: 1px solid #e5e7eb;

        .input-actions {
            display: flex;
            justify-content: flex-end;
            margin-top: 8px;
        }
    }
}

// 消息右键菜单
.context-menu {
    position: fixed;
    z-index: 3000;
    min-width: 110px;
    padding: 4px 0;
    background-color: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 6px;
    box-shadow: 0 4px 12px rgba(0, 21, 41, 0.12);

    .context-menu-item {
        padding: 8px 16px;
        font-size: 14px;
        color: #2d3748;
        cursor: pointer;

        &:hover {
            background-color: #f5f7fa;
            color: #f56c6c;
        }
    }
}
</style>
