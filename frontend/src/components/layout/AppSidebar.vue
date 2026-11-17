<template>
  <aside class="side" :class="{ open }">
    <div class="side-logo">
      <div class="mk">电</div>
      <div class="txt">
        <b>电子速购</b>
        <span>E-SHOP Express</span>
      </div>
    </div>

    <nav class="side-nav" aria-label="主导航">
      <template v-for="group in groups" :key="group.title">
        <div v-if="group.items && group.items.length" class="side-grp">{{ group.title }}</div>
        <a
          v-for="item in group.items"
          :key="item.path"
          href="#"
          :class="{ on: item.path === active }"
          :aria-current="item.path === active ? 'page' : undefined"
          @click.prevent="$emit('navigate', item.path)"
        >
          <span class="side-ic" aria-hidden="true">
            <AppIcon :name="item.icon || 'detail'" :size="16" />
          </span>
          <span class="side-label">{{ item.label }}</span>
          <span v-if="item.badge" class="side-badge">{{ item.badge }}</span>
        </a>
      </template>
    </nav>

    <div v-if="footer" class="side-foot">{{ footer }}</div>
  </aside>
</template>

<script>
import AppIcon from '../ui/AppIcon.vue'

/**
 * 侧栏。
 *
 * 改造前：App.vue 里一段 <ul>，无分组、无图标、无选中态。
 * 改造后：菜单分组、选中态、图标由数据驱动，App.vue 只负责「角色 → 菜单数据」映射。
 *
 * v2 视觉调整：
 *  - 图标由几何字符换成 16px 线性 SVG（字符图标字重/基线不可控）
 *  - 选中态改为「淡品牌底 + 左侧 2.5px 指示条」，比整块高对比底更克制
 *  - 收起态（≤1024px）只留图标，悬停由 title 兜底说明
 */
export default {
  name: 'AppSidebar',
  components: { AppIcon },
  props: {
    // [{ title, items: [{ path, label, icon, badge }] }]
    groups: { type: Array, default: () => [] },
    active: { type: String, default: '' },
    footer: { type: String, default: '' },
    // 移动端抽屉开合
    open: { type: Boolean, default: false }
  },
  emits: ['navigate']
}
</script>
