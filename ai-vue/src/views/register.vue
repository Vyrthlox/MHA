<template>
    <div class="register-container">
        <div class="title">
            <div class="back-home">
                <span><router-link to="/back"><el-icon><Back/></el-icon>返回首页</router-link></span>
            </div>
            <div class="title-text">
                <h2 style="text-align: center;">创建您的账户</h2>
                <p style="text-align: center;">填写注册信息</p>
            </div>
        </div>
        <div class="form-container">
            <el-form ref="ruleFormRef"
            :model="formData"
            :rules="rules"
            label-position="top">
            <el-form-item label="邮箱" prop="email">
                <el-input v-model="formData.email" size="large" placeholder="请输入邮箱"></el-input>
            </el-form-item>
            <el-form-item label="验证码" prop="code">
                <el-input v-model="formData.code" size="large" placeholder="请输入验证码" maxlength="6">
                    <template #append>
                        <el-button :disabled="countdown > 0 || sending" @click="handleSendCode">
                            {{ countdown > 0 ? `${countdown}s 后重试` : '发送验证码' }}
                        </el-button>
                    </template>
                </el-input>
            </el-form-item>
            <el-form-item label="用户名" prop="username">
                <el-input v-model="formData.username" size="large" placeholder="请输入用户名"></el-input>
            </el-form-item>
            <el-form-item label="密码" prop="password">
                <el-input v-model="formData.password" size="large" placeholder="请输入密码" type="password" show-password></el-input>
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="formData.confirmPassword" size="large" placeholder="请再次输入密码" type="password" show-password></el-input>
            </el-form-item>
            <el-form-item>
                <el-button class="btn" type="primary" @click="submitForm(ruleFormRef)">注册</el-button>
            </el-form-item>
            <div class="flex-box">
                <p style="text-align: center;">已有账户?<router-link to="/auth/login">去登录</router-link></p>
            </div>
        </el-form>
        </div>
    </div>
</template>
<script setup>
    import { onBeforeUnmount, ref } from 'vue'
    import { ElMessage } from 'element-plus'
    import { useRouter } from 'vue-router'
    import { useUserStore } from '@/stores/user'
    import { sendCode } from '@/api/admin'

    const router = useRouter()
    const userStore = useUserStore()
    const ruleFormRef = ref()

    // ---- 发送验证码 ----
    const countdown = ref(0)
    const sending = ref(false)
    let timer = null

    const stopTimer = () => {
        if (timer) {
            clearInterval(timer)
            timer = null
        }
    }

    const startCountdown = (seconds) => {
        stopTimer()
        countdown.value = Math.max(1, Number(seconds) || 60)
        timer = setInterval(() => {
            countdown.value -= 1
            if (countdown.value <= 0) {
                countdown.value = 0
                stopTimer()
            }
        }, 1000)
    }

    // 必须清掉：组件卸载后定时器还在跑的话，回调会去改一个已经不存在的组件的状态。
    // 注：这里的倒计时纯粹是体验，真正的 60 秒限制在后端（Redis 的 cooldown 键），
    // 所以刷新页面丢掉倒计时并不会绕过限制——点下去会被服务端拒绝。
    onBeforeUnmount(stopTimer)

    const handleSendCode = async () => {
        if (countdown.value > 0 || sending.value) return

        // 只校验邮箱一项，不要触发整表校验——否则会把「请输入用户名」这类
        // 还没填的必填项提示一起弹出来，干扰正在一步步填表的人。
        try {
            await ruleFormRef.value.validateField('email')
        } catch {
            return
        }

        sending.value = true
        try {
            const res = await sendCode({ email: formData.value.email })
            ElMessage.success(res.msg || '验证码已发送')
            // 用后端返回的秒数，而不是前端写死 60
            startCountdown(res?.data?.cooldownSeconds ?? 60)
        } catch (e) {
            // 发送失败绝不能把用户锁在倒计时里。
            // 但有一种失败是「服务端还在冷却」（典型场景：刷新页面后重发），
            // 此时后端会把剩余秒数写在提示语里，用它把倒计时续上，
            // 用户才不会撞上一个没有倒计时、点一次错一次的按钮。
            const remain = /(\d+)\s*秒/.exec(e?.message || '')
            if (remain) {
                startCountdown(Number(remain[1]))
            }
        } finally {
            sending.value = false
        }
    }

    const formData = ref({
        email: '',
        code: '',
        username: '',
        password: '',
        confirmPassword: ''
    })
    const rules = ref({
        email: [
            { required: true, message: '请输入邮箱', trigger: 'blur' },
            { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
        ],
        code: [
            { required: true, message: '请输入验证码', trigger: 'blur' }
        ],
        username: [
            { required: true, message: '请输入用户名', trigger: 'blur' }
        ],
        password: [
            { required: true, message: '请输入密码', trigger: 'blur' }
        ],
        confirmPassword: [
            { required: true, message: '请再次输入密码', trigger: 'blur' },
            {
                validator: (rule, value, callback) => {
                    if (value !== formData.value.password) {
                        callback(new Error('两次输入的密码不一致'))
                    } else {
                        callback()
                    }
                },
                trigger: 'blur'
            }
        ]
    })
    //注册
    const submitForm = async (formEl) => {
        if (!formEl) return

        try {
            await formEl.validate()
        } catch {
            // 校验不通过，el-form 已提示（两次密码不一致也在这里拦下，不发请求）
            return
        }

        try {
            // payload 含 email/code/username/password/confirmPassword：后端 RegisterRequest
            // 显式声明了 confirmPassword 并会校验，不依赖未知字段忽略策略。
            const res = await userStore.register(formData.value)
            ElMessage.success(res.msg || '注册成功')
            router.replace('/auth/login')
        } catch {
            // 响应拦截器已提示（如「用户名已被注册」），这里只中断流程
        }
    }
</script>
<style scoped lang="scss">
    .register-container{
        width: 456px;
        box-sizing: border-box;
        /* 注册表单字段比登录页多，内边距收小，避免切换路由时整页高度明显变化 */
        padding: 28px 32px 24px;
        /* 半透明白 + 高斯模糊，做磨砂玻璃的朦胧感（与 login.vue 保持一致） */
        background-color: rgba(255, 255, 255, 0.35);
        backdrop-filter: blur(10px);
        -webkit-backdrop-filter: blur(10px);
        border: 1px solid rgba(255, 255, 255, 0.7);
        border-radius: 16px;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
    }
    .title{
        .back-home{
            margin-bottom:14px;
        }
        .title-text{
            h2{
                font-size: 28px;
                /* 清掉浏览器默认的上下外边距，只留一点和副标题的间距 */
                margin: 0 0 6px;
            }
            p{
                font-size:16px;
                margin: 0;
                color: #6b7236;
            }
        }
    }
    .form-container{
        /* 收紧跟表单字段的默认间距（el-form-item 默认 18px） */
        :deep(.el-form-item){
            margin-bottom: 12px;
        }
        .btn{
            margin-top:22px;
            width:100%;
        }
        .flex-box{
            p{
                margin: 0;
            }
        }
    }
</style>
