import { createRouter, createWebHistory } from 'vue-router'
import BackendLayout from '@/components/BackendLayout.vue'
import AuthLayout from '@/components/AuthLayout.vue'
import { useUserStore } from '@/stores/user'

//路由配置
const backendRoutes = [
    {
        path: '/',
        redirect: '/back'
    },
    {
        path:'/back',
        component: BackendLayout,
        // 标在父级即可：Vue Router 4 会把匹配链上的 meta 合并进 to.meta，
        // 所有子路由自动继承，不用逐个写。
        meta:{requiresAuth:true},
        children:[
            {
                path:'dashboard',
                component:()=>import('@/views/dashboard.vue'),
                meta:{
                    title:'数据分析',
                    icon:'PieChart'
                }
            },
            {
                path:'knowledge',
                component:()=>import('@/views/knowledge.vue'),
                meta:{
                    title:'知识库',
                    icon:'ChatLineSquare'
                }
            },
            {
                path:'consultations',
                component:()=>import('@/views/consultations.vue'),
                meta:{
                    title:'咨询记录',
                    icon:'Message'
                }
            },
            {
                path:'emotional',
                component:()=>import('@/views/emotional.vue'),
                meta:{
                    title:'情绪日志',
                    icon:'User'
                }
            },
            {
                path:'home',
                component:()=>import('@/views/home.vue'),
                meta:{
                    title:'首页',
                    hidden:true
                }
            }
        ]
    },
    {
        path:'/auth',
        component:AuthLayout,
        meta:{guestOnly:true},
        children:[
            {
                path:'login',
                component:()=>import('@/views/login.vue'),
                meta:{
                    title:'登录',
                }
            },
            {
                path:'register',
                component:()=>import('@/views/register.vue'),
                meta:{
                    title:'注册',
                }
            }
        ]
    }
]
//创建路由实例
const router = createRouter({
    history:createWebHistory(),
    routes:backendRoutes
})

//全局前置守卫：未登录不能进后台，已登录不再进登录/注册页
router.beforeEach((to)=>{
    // 这里调用 useUserStore() 是安全的：main.js 里 app.use(pinia) 在 app.use(router) 之前，
    // 且守卫只在应用 mount 之后才会运行。改动 main.js 时请保持这个顺序。
    const userStore = useUserStore()

    if(to.meta.requiresAuth && !userStore.isLoggedIn){
        // /back 本身没有子路由视图，直接落到它只会渲染出一个空布局，
        // 所以统一归一化成 dashboard。
        const target = to.path === '/back' ? '/back/dashboard' : to.fullPath
        return {path:'/auth/login',query:{redirect:target}}
    }

    if(to.meta.guestOnly && userStore.isLoggedIn){
        return {path:'/back/dashboard'}
    }

    return true
})

//导出路由实例
export default router