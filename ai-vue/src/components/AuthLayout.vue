<template>
    <div class="auth-layout" :style="{ backgroundImage: `url(${authBack})` }">
        <div class="left-section">
            <div class="content">
                <div class="robot">
                    <el-image style="width:180px;height:180px" :src="authLogo" alt="AI心理助手" />
                </div>
            </div>
        </div>
        <div class="right-section">
            <router-view></router-view>
        </div>
    </div>
</template>

<script setup>
import authLogo from '@/img/auth-logo.png'
import authBack from '@/img/authBack.jpeg'
</script>

<style scoped lang="scss">
.auth-layout {
    display: flex;
    height: 100vh;
    background-color: #ffb26b;
    background-size: cover;
    background-position: center;
    background-repeat: no-repeat;

    .left-section,
    .right-section {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .left-section {
        .content {
            display: flex;
            flex-direction: column;
            align-items: center;
            text-align: center;
        }
    }
}
</style>
