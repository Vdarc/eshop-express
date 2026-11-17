<template>
  <span class="ui-tag" :class="`ui-tag-${kind}`"><slot>{{ label }}</slot></span>
</template>

<script>
/**
 * 状态标签。原型里出现 20+ 次（已生成/待更新/2 处差异/通过/强制术语…），
 * 统一成四档语义，避免各页面自行拼色值。
 */
export default {
  name: 'StatusTag',
  props: {
    // ok 绿 / warn 橙 / mute 灰 / brand 蓝 / danger 红
    kind: { type: String, default: 'mute' },
    label: { type: String, default: '' }
  }
}
</script>
