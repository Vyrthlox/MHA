<template>
    <div class="navbar" :style="{ backgroundImage: `url(${navbarBg})` }">
        <div class="flex-box">
            <!-- 悬浮气泡样式：点击回首页，鼠标悬停提示 -->
            <el-tooltip content="Hi，来聊聊吗" placement="right" :show-after="100">
                <button class="chat-bubble" type="button" @click="goHome">
                    <img :src="logo" alt="AI 心理助手" />
                </button>
            </el-tooltip>
        </div>
        <div class="flex-box">
            <el-popover trigger="hover" placement="bottom-end" :width="280">
                <template #reference>
                    <div class="flex-box user-trigger">
                        <div class="avatar-wrap">
                            <el-avatar :src="avatar"/>
                            <!-- 悬停显示更换头像按钮 -->
                            <div class="avatar-mask" @click.stop="handleAvatarChange">
                                <span class="avatar-btn">更换<br>头像</span>
                            </div>
                        </div>
                        <p class="user-name">{{ userInfo.username }}</p>
                        <el-icon><ArrowDown></ArrowDown></el-icon>
                    </div>
                </template>
                <div class="user-card">
                    <!-- 头像 + 用户名 + 邮箱占一行 -->
                    <div class="user-card-header">
                        <el-avatar :src="avatar" :size="48"/>
                        <div class="user-card-info">
                            <p class="name">{{ userInfo.username }}</p>
                            <p class="email">{{ userInfo.email || '未绑定邮箱' }}</p>
                        </div>
                    </div>
                    <!-- 获赞 / 粉丝 / 关注，数字在上，文字在下 -->
                    <div class="user-card-stats">
                        <div class="stat" v-for="item in stats" :key="item.label">
                            <span class="num">{{ item.value }}</span>
                            <span class="label">{{ item.label }}</span>
                        </div>
                    </div>
                    <!-- 逐行显示的操作选项 -->
                    <div class="user-card-menu">
                        <div class="menu-item" @click="handleMenu('publish')">
                            <el-icon><Plus></Plus></el-icon>
                            <span>发布作品</span>
                        </div>
                        <div class="menu-item" @click="handleMenu('profile')">
                            <el-icon><User></User></el-icon>
                            <span>个人中心</span>
                        </div>
                        <div class="menu-item" @click="handleMenu('logout')">
                            <el-icon><SwitchButton></SwitchButton></el-icon>
                            <span>退出登录</span>
                        </div>
                    </div>
                </div>
            </el-popover>
        </div>
    </div>
</template>
<script setup>
    import { computed } from 'vue'
    import { useRouter } from 'vue-router'
    // ElMessage / ElMessageBox 必须显式 import：
    // main.js 里全局注册的只有 Element Plus 的组件，不含这些命令式函数。
    import { ElMessage, ElMessageBox } from 'element-plus'
    import { useUserStore } from '@/stores/user'
    // 图片通过 import 交给 Vite 处理，避免直接写静态路径导致 404
    import navbarBg from '@/img/MountainsAndSeas.jpg'
    import logo from '@/img/AILog.png'

    const router = useRouter()
    const userStore = useUserStore()

    // 用 computed 而不是在 setup 顶层读一次 localStorage：
    // 后者只在组件创建时求值一次，既不是响应式的，登录/退出后也不会更新。
    const userInfo = computed(() => userStore.userInfo)

    // 后端 UserInfo 目前没有头像字段，先给一个默认头像
    const avatar = computed(() => userInfo.value.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png')

    // 获赞 / 粉丝 / 关注：后端暂无对应字段，缺失时显示 0
    const stats = computed(() => [
        { label: '获赞', value: userInfo.value.likeCount ?? 0 },
        { label: '粉丝', value: userInfo.value.fansCount ?? 0 },
        { label: '关注', value: userInfo.value.followCount ?? 0 }
    ])

    // 后端暂无头像上传接口，先提示；接入后在这里打开图片选择/上传
    const handleAvatarChange = () => {
        ElMessage.info('更换头像功能开发中')
    }

    const handleMenu = async (command) => {
        if (command !== 'logout') {
            ElMessage.info('功能开发中')
            return
        }
        try {
            await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
                confirmButtonText: '退出',
                cancelButtonText: '取消',
                type: 'warning'
            })
        } catch {
            // 用户点了取消或按 Esc，什么都不做，保持登录态
            return
        }

        userStore.logout()
        ElMessage.success('已退出登录')
        // replace 而非 push：退出后按浏览器「后退」不应该再回到后台页面
        router.replace('/auth/login')
    }

    // 点击左上角气泡图标回到 AI 对话首页
    const goHome = () => {
        router.push('/back/home')
    }

</script>
<style lang="scss" scoped>
.navbar{
    height:100%;
    display:flex;
    align-items:center;
    justify-content: space-between;
    padding:0 15px;
    // 用 cover 等比缩放并裁剪，保证长条形导航栏里的图片不被拉伸变形
    background-size: cover;
    background-position: center;
    background-repeat: no-repeat;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
    border-bottom: 1px solid #e5e7eb;
    .flex-box{
    display: flex;
    align-items: center;
    justify-content: center;
    }
    .chat-bubble{
        width: 48px;
        height: 48px;
        padding: 0;
        border: none;
        border-radius: 50%;
        overflow: hidden;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(255, 255, 255, 0.9);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.18);
        cursor: pointer;
        transition: transform 0.2s, box-shadow 0.2s;
        img{
            width: 100%;
            height: 100%;
            object-fit: cover;
        }
        &:hover{
            transform: translateY(-2px) scale(1.05);
            box-shadow: 0 6px 16px rgba(0, 0, 0, 0.25);
        }
        &:active{
            transform: translateY(0) scale(0.98);
        }
    }
    .user-trigger{
        gap: 6px;
        cursor: pointer;
        padding: 4px 6px;
        border-radius: 6px;
        &:hover{
            background: rgba(255, 255, 255, 0.45);
        }
    }
    .avatar-wrap{
        position: relative;
        display: flex;
        border-radius: 50%;
        overflow: hidden;
        cursor: pointer;
    }
    .avatar-mask{
        position: absolute;
        inset: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(0, 0, 0, 0.45);
        opacity: 0;
        transition: opacity 0.2s;
    }
    .avatar-btn{
        font-size: 10px;
        line-height: 1.2;
        color: #fff;
        text-align: center;
        white-space: nowrap;
    }
    .avatar-wrap:hover .avatar-mask{
        opacity: 1;
    }
}

// 弹窗内容会被 teleport 到 body，不能用 .navbar 嵌套，这里放平级
.user-card{
    .user-card-header{
        display: flex;
        align-items: center;
        gap: 10px;
    }
    .user-card-info{
        flex: 1;
        min-width: 0;
    }
    .name{
        margin: 0;
        font-size: 15px;
        line-height: 1.3;
        font-weight: 600;
        color: #212529;
    }
    .email{
        margin: 2px 0 0;
        font-size: 12px;
        line-height: 1.3;
        color: #8b8b8b;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
    .user-card-stats{
        display: flex;
        margin-top: 12px;
        padding: 12px 0;
        border-top: 1px solid #ebeef5;
        border-bottom: 1px solid #ebeef5;
    }
    .stat{
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 2px;
    }
    .num{
        font-size: 16px;
        font-weight: 600;
        color: #212529;
    }
    .label{
        font-size: 12px;
        color: #8b8b8b;
    }
    .user-card-menu{
        margin-top: 6px;
    }
    .menu-item{
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 8px;
        border-radius: 6px;
        font-size: 14px;
        color: #333;
        cursor: pointer;
        &:hover{
            background: #f5f7fa;
            color: #409eff;
        }
    }
}

</style>
