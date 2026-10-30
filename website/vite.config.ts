import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// base 相对路径，便于部署在任意子路径（如 bugu.app 或对象存储静态托管）
export default defineConfig({
  base: './',
  plugins: [vue()],
})
