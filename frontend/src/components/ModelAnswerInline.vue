<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ text: string }>()
const parts = computed(() => props.text.split(/(`[^`\n]+`|\*\*[^*\n]+\*\*)/g).map(text => {
  if (text.startsWith('`') && text.endsWith('`')) return { tag: 'code', text: text.slice(1, -1), code: true }
  if (text.startsWith('**') && text.endsWith('**')) return { tag: 'strong', text: text.slice(2, -2), code: false }
  return { tag: 'span', text, code: false }
}))
</script>
<template><template v-for="(part, index) in parts" :key="index"><component :is="part.tag" :class="{ 'inline-code': part.code }">{{ part.text }}</component></template></template>
