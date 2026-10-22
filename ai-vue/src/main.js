import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'
import*as ElementPlusIconsVue from '@element-plus/icons-vue'
import {createPinia} from 'pinia'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

//注册图标组件
for(const [key,component] of Object.entries(ElementPlusIconsVue)){
    app.component(key,component)
}

app.use(router)
.use(ElementPlus)
.mount('#app')