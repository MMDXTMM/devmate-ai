<script setup lang="ts">
import { computed, ref, watch, useId } from 'vue'
import type { ProjectGuide } from '../types/project'
import { moduleEmoji } from '../utils/guideAppearance'

const props = defineProps<{ guide: ProjectGuide }>()
const emit = defineEmits<{ selectModule: [id: string]; selectFlow: [id: string] }>()
const selectedId = ref('')
const selectedFlowId = ref('')
const view = ref<'relations' | 'journey'>('relations')
const titleId = useId()
const modules = computed(() => props.guide.content.modules)
const flows = computed(() => props.guide.content.flows)
watch(() => props.guide, () => {
  selectedId.value = modules.value.find(item => item.relatedModuleIds.length)?.id ?? modules.value[0]?.id ?? ''
  selectedFlowId.value = flows.value[0]?.id ?? ''
  view.value = 'relations'
}, { immediate: true })
const focus = computed(() => modules.value.find(item => item.id === selectedId.value))
const neighbors = computed(() => modules.value.filter(item => item.id !== focus.value?.id &&
  (focus.value?.relatedModuleIds.includes(item.id) || item.relatedModuleIds.includes(selectedId.value))))
const journey = computed(() => flows.value.find(item => item.id === selectedFlowId.value))
const graphHeight = computed(() => Math.max(190, neighbors.value.length * 104 + 24))
function moduleName(id: string) { return modules.value.find(item => item.id === id)?.name }
function moduleIcon(id: string) {
  const item = modules.value.find(item => item.id === id)
  return item ? moduleEmoji(item, props.guide.content.appearance) : '🧩'
}
</script>

<template>
  <section class="business-diagram" :aria-labelledby="titleId">
    <header class="diagram-heading">
      <div><span class="diagram-kicker">🗺️ 把业务串起来</span><h4 :id="titleId">项目业务地图</h4><p>点选一个模块，看看它和谁有关；也可以沿一件事走一遍。</p></div>
      <div class="diagram-tabs" role="group" aria-label="业务地图视图">
        <button type="button" :aria-pressed="view === 'relations'" @click="view = 'relations'">🧩 模块联系</button>
        <button type="button" :aria-pressed="view === 'journey'" @click="view = 'journey'">👣 业务路线</button>
      </div>
    </header>
    <template v-if="view === 'relations'">
      <div class="diagram-module-picker" role="group" aria-label="选择要了解的模块">
        <button v-for="item in modules" :key="item.id" type="button" :aria-pressed="selectedId === item.id" @click="selectedId = item.id"><span aria-hidden="true">{{ moduleEmoji(item, props.guide.content.appearance) }}</span> {{ item.name }}</button>
      </div>
      <div v-if="focus && neighbors.length" class="diagram-scroll" tabindex="0" aria-label="模块关联图，可横向滚动">
        <div class="relation-canvas" :style="{ height: `${graphHeight}px` }">
          <svg class="relation-wires" width="660" :height="graphHeight" aria-hidden="true">
            <path v-for="(item, index) in neighbors" :key="item.id" :d="`M 228 ${graphHeight / 2} C 320 ${graphHeight / 2}, 330 ${index * 104 + 62}, 420 ${index * 104 + 62}`" />
          </svg>
          <button type="button" class="relation-node relation-focus" :style="{ top: `${graphHeight / 2 - 44}px` }" @click="emit('selectModule', focus.id)"><span aria-hidden="true">{{ moduleEmoji(focus, guide.content.appearance) }}</span><b>{{ focus.name }}</b><small>当前模块 · 查看业务 ↗</small></button>
          <button v-for="(item, index) in neighbors" :key="item.id" type="button" class="relation-node relation-neighbor" :style="{ top: `${index * 104 + 18}px` }" @click="emit('selectModule', item.id)"><span aria-hidden="true">{{ moduleEmoji(item, props.guide.content.appearance) }}</span><b>{{ item.name }}</b><small>关联模块 · 查看业务 ↗</small></button>
        </div>
      </div>
      <div v-else class="diagram-empty"><span aria-hidden="true">🌱</span><p>{{ focus ? '这份导览尚未确认该模块与其他模块的联系。可以从业务路线继续了解。' : '当前没有可展示的模块，等待更多项目证据。' }}</p><button v-if="focus" class="diagram-text-button" type="button" @click="emit('selectModule', focus.id)">先了解 {{ focus.name }} ↗</button></div>
      <p class="diagram-caption">连线表示已保存导览中的模块关联，不表示调用方向或先后顺序。具体步骤请看业务路线。</p>
    </template>
    <template v-else>
      <div class="diagram-module-picker" role="group" aria-label="选择业务路线"><button v-for="item in flows" :key="item.id" type="button" :aria-pressed="selectedFlowId === item.id" @click="selectedFlowId = item.id">👣 {{ item.name }}</button></div>
      <template v-if="journey">
        <div class="journey-heading"><b>{{ journey.name }}</b><span>{{ journey.goal }}</span><button class="diagram-text-button" type="button" @click="emit('selectFlow', journey.id)">查看规则与依据 ↗</button></div>
        <ol class="journey-track" tabindex="0" aria-label="业务执行步骤，可横向滚动">
          <li class="journey-start"><span class="journey-number" aria-hidden="true">🏁</span><b>从这里开始</b><p>{{ journey.trigger }}</p></li>
          <li v-for="(step, index) in journey.steps" :key="index"><span class="journey-number">{{ String(index + 1).padStart(2, '0') }}</span><b>{{ step.action }}</b><p>{{ step.result }}</p><div class="journey-modules"><span v-for="id in step.moduleIds.filter(id => moduleName(id))" :key="id">{{ moduleIcon(id) }} {{ moduleName(id) }}</span></div><small>{{ { OBSERVED: '证据观察', INFERRED: '推断步骤', UNKNOWN: '尚待确认' }[step.conclusionStatus] }}</small></li>
          <li class="journey-finish"><span class="journey-number" aria-hidden="true">🎁</span><b>最后得到</b><p>{{ journey.outcomes[0]?.text ?? '结果仍待确认' }}</p></li>
        </ol>
        <p class="diagram-caption">按报告中的步骤顺序展示；模块标签表示参与该步骤，不额外推断模块间调用关系。</p>
      </template>
      <div v-else class="diagram-empty"><span aria-hidden="true">👣</span><p>当前证据还不足以串起业务路线，先从模块卡片开始探索吧。</p></div>
    </template>
  </section>
</template>

<style scoped>
.business-diagram { margin: 28px 0; border: 1px solid #e2deef; border-radius: 24px; padding: 24px; background: #faf8ff; min-width: 0; color: #3e3957; }
.diagram-heading { display:flex; justify-content:space-between; gap:16px; align-items:flex-start; flex-wrap:wrap; }
.diagram-kicker { color:#70618d; font-size:12px; font-weight:700; }
.diagram-heading h4 { font-size:21px; margin:5px 0; }
.diagram-heading p,.diagram-caption { font-size:12px; line-height:1.7; color:#69617b; }
.diagram-heading p { margin:4px 0 14px; }
.diagram-tabs { padding:4px; border:1px solid #e3deed; background:#fff; border-radius:14px; display:flex; gap:4px; flex-wrap:wrap; }
.diagram-tabs button,.diagram-module-picker button { border:0; color:#625a73; background:transparent; font:inherit; font-size:13px; padding:9px 13px; border-radius:10px; cursor:pointer; }
.diagram-tabs button[aria-pressed=true] { background:#ece5fc; color:#5a4087; font-weight:700; }
.diagram-module-picker { display:flex; gap:8px; flex-wrap:wrap; margin:12px 0; }
.diagram-module-picker button { border:1px solid #e4dfee; background:#fff; overflow-wrap:anywhere; text-align:left; }
.diagram-module-picker button[aria-pressed=true] { border-color:#9277b9; background:#eee7fa; color:#503577; }
.diagram-scroll { overflow-x:auto; overscroll-behavior:contain; border-radius:18px; background-color:#f4f0fb; background-image:radial-gradient(#dcd4ea 1px,transparent 1px); background-size:18px 18px; max-height:530px; overflow-y:auto; }
.relation-canvas { position:relative; width:660px; margin-inline:auto; }
.relation-wires { position:absolute; inset:0; pointer-events:none; }
.relation-wires path { stroke:#ad98ca; stroke-width:2; fill:none; }
.relation-node { position:absolute; width:204px; min-height:88px; box-sizing:border-box; padding:10px 16px; border:1px solid #dacfe9; border-radius:18px; box-shadow:0 4px 0 #e8e1f1; background:#fff; display:grid; grid-template-columns:32px 1fr; align-content:center; column-gap:8px; color:#463955; font:inherit; text-align:left; cursor:pointer; }
.relation-node > span { grid-row:1 / 3; font-size:25px; align-self:center; }
.relation-node b { font-size:13px; line-height:1.5; overflow-wrap:anywhere; }
.relation-node small { font-size:10px; color:#756580; }
.relation-focus { left:24px; background:#e9def9; border-color:#bca4db; }
.relation-neighbor { left:420px; }
.diagram-caption { margin:14px 0 0; }
.diagram-empty { padding:26px 16px; text-align:center; border:1px dashed #d9cfe6; border-radius:18px; background:#fff; }
.diagram-empty > span { font-size:30px; }
.diagram-empty p { font-size:13px; }
.diagram-text-button { font:inherit; font-size:12px; background:transparent; border:0; color:#614582; cursor:pointer; padding:6px 0; text-decoration:underline; text-underline-offset:3px; }
.journey-heading { display:flex; gap:8px 16px; align-items:center; flex-wrap:wrap; margin:20px 0 14px; }
.journey-heading > span { font-size:12px; color:#6f637c; }
.journey-track { list-style:none; display:flex; overflow-x:auto; gap:28px; margin:0; padding:6px 6px 20px; }
.journey-track > li { flex:0 0 194px; position:relative; padding:18px; border:1px solid #e0d9ea; border-radius:18px; background:#fff; box-sizing:border-box; }
.journey-track > li:not(:last-child)::after { content:'→'; position:absolute; left:calc(100% + 7px); top:40px; color:#a18abf; }
.journey-number { display:block; color:#745693; font-size:20px; margin-bottom:12px; font-weight:800; }
.journey-track b { font-size:13px; }
.journey-track p { font-size:12px; line-height:1.7; margin:8px 0; }
.journey-track small { display:block; color:#726479; font-size:10px; margin-top:10px; }
.journey-track .journey-start { background:#edf7f0; border-color:#cfe4d7; }
.journey-track .journey-finish { background:#fff3e5; border-color:#edddc5; }
.journey-modules { display:flex; flex-wrap:wrap; gap:4px; }
.journey-modules span { font-size:10px; border-radius:7px; padding:3px 6px; background:#f2edf7; }
button:focus-visible,.diagram-scroll:focus-visible,.journey-track:focus-visible { outline:3px solid #9270bd; outline-offset:3px; }
@media(max-width:640px) { .business-diagram { padding:16px; border-radius:18px; } .journey-track > li { flex-basis:178px; } }
</style>
