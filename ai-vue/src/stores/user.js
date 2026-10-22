import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import {login as loginApi, register as registerApi} from '@/api/admin'

// localStorage 里的 JSON 可能是脏数据（手工改过 / 旧版本残留），
// 解析失败不能让整个应用起不来。
const safeParse = (raw) => {
    if (!raw) return {}
    try {
        return JSON.parse(raw) || {}
    } catch {
        return {}
    }
}

export const useUserStore = defineStore('user', () => {
    // 从 localStorage 回填，刷新页面后保持登录态
    const token = ref(localStorage.getItem('token') || '')
    const userInfo = ref(safeParse(localStorage.getItem('userInfo')))

    const isLoggedIn = computed(() => !!token.value)
    const username = computed(() => userInfo.value.username || '')
    // status: 1=管理员，2=普通用户（与后端 UserInfo 一致）
    const isAdmin = computed(() => userInfo.value.status === 1)

    const setAuth = (newToken, newUserInfo) => {
        token.value = newToken
        userInfo.value = newUserInfo || {}
        localStorage.setItem('token', newToken)
        localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    }

    const clearAuth = () => {
        token.value = ''
        userInfo.value = {}
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
    }

    // 失败时不在这里弹提示：响应拦截器已经弹过了。
    // 这里只负责把异常继续往上抛，让调用方的 catch 能中断后续跳转。
    const login = async (payload) => {
        const res = await loginApi(payload)
        const newToken = res?.data?.token
        if (!newToken) {
            throw new Error(res?.msg || '登录失败')
        }
        setAuth(newToken, res.data.userInfo)
        return res
    }

    const register = (payload) => registerApi(payload)

    const logout = () => clearAuth()

    return {token, userInfo, isLoggedIn, username, isAdmin, setAuth, clearAuth, login, register, logout}
})
