import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const page = (name: string) => new URL(`./${name}`, import.meta.url).pathname

// base 相对路径，便于部署在任意子路径（如 bugu.app 或对象存储静态托管）
// 多页面入口：首页 + 隐私政策 + 用户协议（商店审核要求可公开访问的独立 URL）
export default defineConfig({
  base: './',
  plugins: [vue()],
  build: {
    rollupOptions: {
      input: {
        main: page('index.html'),
        privacy: page('privacy.html'),
        terms: page('terms.html'),
        bond: page('bond/index.html'),
      },
    },
  },
})
