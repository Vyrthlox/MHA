import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import {resolve} from 'path'
// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve:{
    alias:{
      '@':resolve(__dirname,'src')
    },
  },
  server:{
    proxy:{
      '/api':{
        target:'http://localhost:8080',
        changeOrigin:true
        // 不写 rewrite：后端 server.servlet.context-path=/api，两边都保留 /api 前缀。
        // 前缀只能由其中一方负责，同时做会变成 /api/api/...。
      }
    }
  }
})
