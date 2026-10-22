import axios from 'axios'
import {ElMessage} from 'element-plus'

// 创建axios实例
const service = axios.create({
    baseURL: '/api',
    timeout: 5000
})

// 并发请求同时失败时，只跳转一次
let redirecting = false

// 判断当前页面是不是登录/注册页。
// 不能用 config.url 判断：请求地址是 /user/login，而登录页路由是 /auth/login，
// 两者永远不相等，原来的判断从来没命中过。
const isOnAuthPage = () => window.location.pathname.startsWith('/auth/')

// 清理登录态并跳到登录页
const toLogin = (msg) => {
    if (redirecting) return
    redirecting = true
    ElMessage.error(msg || '登录已过期，请重新登录')
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    // 用 replace 而不是 href：不在历史栈里留下受保护页面，
    // 否则退出/过期后按浏览器「后退」还能回到那个页面。
    const from = window.location.pathname + window.location.search
    const query = from && !isOnAuthPage() ? `?redirect=${encodeURIComponent(from)}` : ''
    window.location.replace(`/auth/login${query}`)
    // 故意不重置 redirecting：本页面马上就卸载了，
    // 重置反而会给并发的第二个失败请求再放行一次跳转。
}

//请求拦截器
service.interceptors.request.use(
    (config)=>{
        // 在发送请求之前做些什么
        const  token=localStorage.getItem('token')
        if(token){
            config.headers['Authorization']='Bearer '+token
        }
        return config
    }
)
// 响应拦截器
service.interceptors.response.use(
    (response)=>{
        // 对响应数据做点什么
        const  {data} = response

        if(Number(data.code)===200){
            return data
        }

        const msg = data.msg

        // 后端约定的「登录过期」哨兵值
        if(Number(data.code)===-1){
            if(!isOnAuthPage()){
                toLogin(msg)
            }
            // 必须 reject：原来这里既不 return 也不 reject，
            // promise 会 resolve 成 undefined，调用方读 res.data.token 时
            // 抛的是 TypeError，真实原因（登录过期）会被掩盖。
            return Promise.reject(new Error(msg||'登录已过期，请重新登录'))
        }

        ElMessage.error(msg||'请求失败')
        // 同理必须 return：原来 reject 被直接丢弃，
        // 调用方的 catch / 后续 await 拿不到任何信号，会继续往下走到跳转逻辑。
        return Promise.reject(new Error(msg||'请求失败'))
    },
    (error)=>{
        // 后端刻意把业务错误也放在 HTTP 200 的 body 里，走到这里的是
        // 真正的传输层 / 服务器层故障，需要单独给文案。
        let msg
        if(error.code==='ECONNABORTED'||/timeout/i.test(error.message||'')){
            msg='请求超时，请稍后重试'
        }else if(!error.response){
            msg='网络异常，请检查网络连接'
        }else{
            const status=error.response.status
            if(status===401||status===403){
                if(!isOnAuthPage()){
                    toLogin('登录已过期，请重新登录')
                }
                return Promise.reject(error)
            }
            const statusText={
                400:'请求参数有误',
                404:'请求的资源不存在',
                500:'服务器内部错误',
                502:'网关错误',
                503:'服务暂不可用',
                504:'网关超时'
            }
            msg=statusText[status]||`请求失败（HTTP ${status}）`
        }
        ElMessage.error(msg)
        return Promise.reject(error)
    }
)

export default service
