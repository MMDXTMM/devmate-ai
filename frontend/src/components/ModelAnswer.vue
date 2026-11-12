<script setup lang="ts">
import { computed, defineComponent, h, type VNodeChild } from 'vue'
import MarkdownIt from 'markdown-it'
import type Token from 'markdown-it/lib/token.mjs'
import AnswerCodeBlock from './AnswerCodeBlock.vue'
const props = defineProps<{ content: string }>()
// Parse Markdown, then create Vue nodes from a fixed tag/attribute whitelist. No v-html or model HTML.
const markdown = new MarkdownIt({ html: false, linkify: false, breaks: true })
const allowedTags = new Set(['p', 'ul', 'ol', 'li', 'blockquote', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'strong', 'em', 's'])
function safeLink(value: string) {
  try { const url = new URL(value); return ['https:', 'http:', 'mailto:'].includes(url.protocol) ? url.href : undefined }
  catch { return undefined }
}
function renderTokens(tokens: Token[]): VNodeChild[] {
  let index = 0
  function read(): VNodeChild[] {
    const nodes: VNodeChild[] = []
    while (index < tokens.length) {
      const token = tokens[index++]!
      if (token.nesting === -1) return nodes
      if (token.nesting === 1) {
        const children = read()
        if (token.type === 'link_open') {
          const href = safeLink(token.attrGet('href') || '')
          nodes.push(h(href ? 'a' : 'span', href ? { href, target: '_blank', rel: 'noopener noreferrer' } : {}, children))
        } else {
          const tag = token.type === 'heading_open' ? (Number(token.tag.slice(1)) <= 2 ? 'h3' : 'h4') : token.tag
          if (allowedTags.has(tag) || tag === 'h3' || tag === 'h4') {
            const attrs = tag === 'ol' && token.attrGet('start') ? { start: Number(token.attrGet('start')) } : {}
            const element = h(tag, attrs, children)
            nodes.push(tag === 'table' ? h('div', { class: 'answer-table', tabindex: 0, 'aria-label': '可横向滚动的表格' }, [element]) : element)
          } else nodes.push(...children)
        }
      } else if (token.type === 'inline') nodes.push(...renderTokens(token.children || []))
      else if (token.type === 'fence' || token.type === 'code_block') {
        nodes.push(h(AnswerCodeBlock, { code: token.content, language: token.info.trim().split(/\s+/)[0] }))
      } else if (token.type === 'code_inline') nodes.push(h('code', { class: 'inline-code' }, token.content))
      else if (token.type === 'softbreak' || token.type === 'hardbreak') nodes.push(h('br'))
      else if (token.type === 'hr') nodes.push(h('hr'))
      else if (token.type === 'image') nodes.push(h('span', { class: 'answer-image-label' }, `[图片：${token.content || '未加载'}]`))
      else nodes.push(token.content)
    }
    return nodes
  }
  return read()
}
const tokens = computed(() => markdown.parse(props.content, {}))
const Content = defineComponent({ setup: () => () => h('div', { class: 'model-answer' }, renderTokens(tokens.value)) })
</script>
<template><Content /></template>
