<template>
  <svg
    class="app-ic"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>

<script>
/**
 * 线性图标集。
 *
 * 改造前侧栏用「◈ ▤ ◧ ⇄ ◎」这类几何字符当图标：字形随系统字体变化、
 * 基线不齐、字重不可控，是界面显得「拼凑」的主要来源之一。
 * 这里统一为 24×24 视框、1.7px 描边、currentColor 着色的一组路径，
 * 只保留业务真正用到的 16 个图标，避免引入整套图标库的体积。
 */
const PATHS = {
  // 翻译：地球 + 双向箭头
  translate: [
    'M12 21a9 9 0 100-18 9 9 0 000 18z',
    'M3.6 9h16.8M3.6 15h16.8',
    'M12 3a14 14 0 000 18a14 14 0 000-18z'
  ],
  // 历史：时钟回转
  history: [
    'M3 12a9 9 0 109-9 9 9 0 00-7.5 4',
    'M3 4v4h4',
    'M12 8v4l3 2'
  ],
  // 术语库：书
  glossary: [
    'M5 4.5A1.5 1.5 0 016.5 3H19v16H6.5A1.5 1.5 0 005 20.5z',
    'M5 19.5A1.5 1.5 0 016.5 18H19',
    'M9 7h6M9 10.5h4'
  ],
  // 术语反哺：互换箭头
  feedback: [
    'M4 8h13l-3-3',
    'M20 16H7l3 3'
  ],
  // 发布：上传
  publish: [
    'M12 15V4',
    'M8 8l4-4 4 4',
    'M4 15v3.5A1.5 1.5 0 005.5 20h13a1.5 1.5 0 001.5-1.5V15'
  ],
  // 商品管理：网格盒
  products: [
    'M4 7.5L12 3l8 4.5v9L12 21l-8-4.5z',
    'M4 7.5l8 4.5 8-4.5M12 12v9'
  ],
  // 推荐：目标 + 星
  recommend: [
    'M12 21a9 9 0 100-18 9 9 0 000 18z',
    'M12 15.2a3.2 3.2 0 100-6.4 3.2 3.2 0 000 6.4z',
    'M12 3.2v2.2M12 18.6v2.2M3.2 12h2.2M18.6 12h2.2'
  ],
  // 仪表盘
  dashboard: [
    'M4 4h7v7H4zM13 4h7v4.5h-7zM13 10.5h7V20h-7zM4 13h7v7H4z'
  ],
  // 分析：柱状图
  analytics: [
    'M4 20V6M4 20h16',
    'M8 20v-6M12.5 20V9M17 20v-9.5'
  ],
  // 搜索
  search: [
    'M10.8 18a7.2 7.2 0 100-14.4 7.2 7.2 0 000 14.4z',
    'M16.2 16.2L21 21'
  ],
  // 商品详情：标签页
  detail: [
    'M6 3.5h8.5L20 9v11.5H6z',
    'M14 3.5V9h5.5',
    'M9.5 13h6M9.5 16.5h4'
  ],
  // 用户
  users: [
    'M9 11.5a3.5 3.5 0 100-7 3.5 3.5 0 000 7z',
    'M3 20.5a6 6 0 0112 0',
    'M16.5 5.2a3.5 3.5 0 010 6.6M17.5 14.4a6 6 0 013.5 5.4'
  ],
  // 商家审核：店铺 + 勾
  merchant: [
    'M4 9.5V20h16V9.5',
    'M3 9.5L5.2 4h13.6L21 9.5a3 3 0 01-6 0 3 3 0 01-6 0 3 3 0 01-6 0z',
    'M9.5 15.5l2 2 3.5-3.5'
  ],
  // 日志：列表
  log: [
    'M5 4h14M5 9h14M5 14h9M5 19h6'
  ],
  // 个人中心
  profile: [
    'M12 21a9 9 0 100-18 9 9 0 000 18z',
    'M12 11.6a2.9 2.9 0 100-5.8 2.9 2.9 0 000 5.8z',
    'M6.8 18.4a5.6 5.6 0 0110.4 0'
  ],
  // 登录 / 注册
  login: [
    'M10 4H5.5A1.5 1.5 0 004 5.5v13A1.5 1.5 0 005.5 20H10',
    'M15 8l4 4-4 4M9.5 12H19'
  ],
  register: [
    'M10 20H5.5A1.5 1.5 0 014 18.5v-13A1.5 1.5 0 015.5 4H10',
    'M16 8l4 4-4 4M9.5 12H20'
  ]
}

export default {
  name: 'AppIcon',
  props: {
    name: { type: String, default: 'detail' },
    size: { type: [Number, String], default: 16 },
    strokeWidth: { type: [Number, String], default: 1.7 }
  },
  computed: {
    paths() {
      return PATHS[this.name] || PATHS.detail
    }
  }
}
</script>

<style scoped>
.app-ic {
  display: block;
  flex: none;
}
</style>
