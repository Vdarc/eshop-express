// 应用入口
import { createApp } from 'vue'

// 样式层：令牌 → 历史组件 → 工具类（顺序即层叠顺序，勿调换）
import './assets/css/tokens.css'
import './assets/css/main.css'
import './assets/css/utilities.css'

import App from './App.vue'

createApp(App).mount('#app')
