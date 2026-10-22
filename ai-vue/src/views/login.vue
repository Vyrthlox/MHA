<template>
    <div class="login-container">
        <div class="title">
            <div class="back-home">
                <span><router-link to="/back"><el-icon><Back/></el-icon>返回首页</router-link></span>
            </div>
            <div class="title-text">
                <h2 style="text-align: center;">登录您的账户</h2>
                <p style="text-align: center;">输入登录信息</p>
            </div>
        </div>
        <div class="form-container">
            <el-form ref="ruleFormRef"  
            :model="formData"  
            :rules="rules"
            label-position="top">
            <el-form-item label="用户名或邮箱" prop="username">
                <el-input v-model="formData.username" placeholder="请输入用户名"></el-input>
            </el-form-item>
            <el-form-item label="密码" prop="password">
                <el-input v-model="formData.password" size="large" placeholder="请输入密码" type="password" show-password></el-input>
            </el-form-item>
            <el-form-item>
                <el-button class="btn" type="primary" @click="submitForm(ruleFormRef)">登录</el-button>
            </el-form-item>
            <div class="flex-box">
                <p style="text-align: center;">还没有账户?<router-link to="/auth/register">去注册</router-link></p>

            </div>
        </el-form>
        </div>
    </div>
</template>
<script setup>
    import { ref , reactive } from 'vue'
    import { ElMessage } from 'element-plus'
    import { useRouter, useRoute } from 'vue-router'
    import { useUserStore } from '@/stores/user'

    const router = useRouter()
    const route = useRoute()
    const userStore = useUserStore()
    const ruleFormRef=ref()

    const formData = ref({
        username: '',
        password: ''
    })
    const rules = ref({
        username: [
            { required: true, message: '请输入用户名', trigger: 'blur' }
        ],
        password: [
            { required: true, message: '请输入密码', trigger: 'blur' }
        ]
    })
    //登录
    const submitForm=async(formEl)=>{
        if(!formEl) return

        try{
            // 用纯 promise 形式：原来 validate(callback) 与 await 混用，
            // await 拿到的是几乎立刻 resolve 的 undefined，
            // 校验不通过时后面的登录逻辑照样会执行。
            await formEl.validate()
        }catch{
            // 校验不通过，el-form 已经在字段下方给出提示
            return
        }

        try{
            // 存 token / userInfo 由 store 负责
            await userStore.login(formData.value)
            ElMessage.success('登录成功')
            // 守卫拦截过来的 redirect 优先。它来自 URL query，是用户可控值，
            // 所以只接受站内绝对路径：`//evil.com` 这种协议相对地址
            // 会被浏览器当作外部站点，必须挡掉。
            const raw = route.query.redirect
            const redirect = typeof raw === 'string' && /^\/(?!\/)/.test(raw)
                ? raw
                : '/back/dashboard'
            router.replace(redirect)
        }catch{
            // 响应拦截器已经弹过错误提示，这里只需要中断流程。
            // 不 catch 的话会变成未处理的 promise rejection。
        }
    }
</script>
<style scoped lang="scss">
    .login-container{
        width: 456px;
        box-sizing: border-box;
        padding: 40px 36px;
        /* 半透明白 + 高斯模糊，做磨砂玻璃的朦胧感 */
        background-color: rgba(255, 255, 255, 0.35);
        backdrop-filter: blur(10px);
        -webkit-backdrop-filter: blur(10px);
        border: 1px solid rgba(255, 255, 255, 0.7);
        border-radius: 16px;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
    }
    .title{
        .back-home{
            margin-bottom:60px;
        }
        .title-text{
            h2{
                font-size: 36px;
                margin-bottom:10px;
            }
            p{
                font-size:18px;
                color: #6b7236;
            }
        }
    }
    .form-container{
        .btn{
            margin-top:40px;
            width:100%;
        }
    }

</style>
