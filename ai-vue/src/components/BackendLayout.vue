<template>
  <div class="backend-layout">
    <!-- 顶部通栏：导航栏横跨整个页面宽度 -->
    <Navbar class="navbar-bar"></Navbar>
    <div class="body-container">
      <Sidebar></Sidebar>
      <el-container>
        <el-main class="main-container">
            <router-view class="content-container"></router-view>
        </el-main>
      </el-container>
    </div>
  </div>
</template>

<script setup>
import Sidebar from './Sidebar.vue'
import Navbar from './Navbar.vue'
</script>

<style lang="scss" scoped>
.backend-layout{
    height: 100vh;
    display: flex;
    flex-direction: column;
    .navbar-bar{
      flex-shrink: 0;
      height: 66px;
    }
    .body-container{
      flex: 1;
      min-height: 0;
      display: flex;
    }
    .main-container{
        height:100%;
        overflow: auto;
        .content-container{
          background-color:white;
            height:100%;
        }
    }
}
</style>
