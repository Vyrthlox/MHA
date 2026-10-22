//侧边栏组件
<template>
    <el-aside width="64px">
    <!-- 固定为图标模式：文字隐藏，鼠标悬浮图标时由 Element Plus 自动显示标题提示 -->
    <el-menu
            :collapse="true"
            :collapse-transition="false"
            default-active="2"
            class="menu-style"
        >
        <el-menu-item @click="selectmenu" v-for="item in menuItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.meta.icon" /></el-icon>
            <template #title>{{ item.meta.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>
</template>

<script setup>
import {computed} from 'vue'
import {useRouter} from 'vue-router'

const router = useRouter()

//菜单项：过滤掉 meta.hidden 的路由（如首页占位页）
const menuItems = computed(()=> (router.options.routes.find(r => r.path === '/back')?.children || []).filter(item => !item.meta?.hidden))

const selectmenu = (key) => {
  const currentRoute = router.options.routes[1]
  router.push(`${currentRoute.path}/${key.index}`)
}
</script>

<style lang="scss" scoped>
.menu-style{
  height:100%;
}
</style>
