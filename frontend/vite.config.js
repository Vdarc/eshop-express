import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  // 站点挂在 https://wlvdarc7.site/ecommerce/ 下，资源路径必须带此前缀，
  // 否则浏览器请求 /assets/... 会落回主站 file_server 导致 404 白屏
  base: '/ecommerce/',
  build: {
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    },
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['vue'],
          axios: ['axios'],
          echarts: ['echarts'],
          excel: ['xlsx', 'file-saver'],
          docx: ['mammoth']
        },
        // 配置缓存策略
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: 'assets/[ext]/[name]-[hash].[ext]'
      }
    }
  },
  server: {
    port: 20682,
    host: true,
    allowedHosts: ['frp-put.com'],
    proxy: {
      '/api': {
        // 指向网关
        target: 'http://127.0.0.1:10600',
        changeOrigin: true
      },
      '/uploads': {
        // 用户上传的头像与商品图由 platform-service 提供
        target: 'http://127.0.0.1:10600',
        changeOrigin: true
      }
    }
  },
  preview: {
    // 10683 已被 embedding 服务占用
    port: 20683
  }
})