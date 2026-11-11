<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type {
  ProjectGuide,
  GuideImplementation,
  GuideConclusion,
} from '../types/project'
const props = defineProps<{ guide: ProjectGuide }>()
const emit = defineEmits<{ chat: [question: string] }>()
const selected = ref('')
const evidenceOpen = ref(false)
watch(
  () => props.guide,
  () => {
    selected.value = ''
    evidenceOpen.value = false
  },
)
const module = computed(() =>
  props.guide.content.modules.find(
    (item) => `module:${item.id}` === selected.value,
  ),
)
const flow = computed(() =>
  props.guide.content.flows.find(
    (item) => `flow:${item.id}` === selected.value,
  ),
)
const relatedFlows = computed(() =>
  module.value
    ? props.guide.content.flows.filter((item) =>
        item.moduleIds.includes(module.value!.id),
      )
    : [],
)
const currentFlows = computed(() =>
  flow.value ? [flow.value] : relatedFlows.value,
)
const currentEvidence = computed(() => {
  const ids = new Set(
    selected.value === 'project'
      ? props.guide.content.project.evidenceIds
      : (module.value?.evidenceIds ?? []),
  )
  currentFlows.value.forEach((item) => {
    item.steps.forEach((step) => step.evidenceIds.forEach((id) => ids.add(id)))
    ;[...item.rules, ...item.outcomes].forEach((claim) =>
      claim.evidenceIds.forEach((id) => ids.add(id)),
    )
  })
  return props.guide.evidence.filter((item) => ids.has(item.id))
})
const implementationLabel = (status: GuideImplementation) =>
  ({
    IMPLEMENTED: '源码中已实现',
    PARTIAL: '部分实现或演示',
    PLANNED: '仅计划',
    UNCLEAR: '实现情况未确认',
  })[status]
const conclusionLabel = (status: GuideConclusion) =>
  ({ OBSERVED: '证据观察', INFERRED: '推断', UNKNOWN: '未确认' })[status]
function select(value: string) {
  selected.value = value
  evidenceOpen.value = false
}
</script>
<template>
  <section class="project-guide" aria-label="项目通俗导览">
    <template v-if="!selected">
      <header class="guide-hero">
        <span class="eyebrow">先看懂</span>
        <h3>{{ guide.content.project.purpose }}</h3>
        <p><b>给谁用：</b>{{ guide.content.project.audience }}</p>
        <p>{{ guide.content.project.scenario }}</p>
        <small
          >{{
            implementationLabel(guide.content.project.implementationStatus)
          }}
          ·
          {{
            conclusionLabel(guide.content.project.conclusionStatus)
          }}；依据静态阅读，未验证运行</small
        >
        <button type="button" @click="select('project')">了解输入与输出</button>
      </header>
      <h4>它能帮你做什么</h4>
      <div class="guide-module-grid">
        <button
          v-for="item in guide.content.modules"
          :key="item.id"
          class="guide-module-card"
          type="button"
          @click="select(`module:${item.id}`)"
        >
          <b>{{ item.name }}</b
          ><span>{{ item.responsibility }}</span
          ><span>{{ item.capabilities.slice(0, 3).join('、') }}</span
          ><small
            >{{ implementationLabel(item.implementationStatus) }} ·
            {{ conclusionLabel(item.conclusionStatus) }}</small
          ><em>了解这块业务 →</em>
        </button>
      </div>
      <h4 v-if="guide.content.flows.length">一件事是怎样完成的</h4>
      <button
        v-for="item in guide.content.flows"
        :key="item.id"
        class="guide-flow-preview"
        type="button"
        @click="select(`flow:${item.id}`)"
      >
        <b>{{ item.name }}</b
        ><span>{{ item.goal }}</span
        ><small
          >{{ item.trigger }} →
          {{ item.steps.map((step) => step.action).join(' → ') }} →
          {{ item.outcomes[0]?.text ?? '结果仍待确认' }}</small
        ><small v-if="item.rules.length"
          >关键判断：{{ item.rules[0]?.text }}</small
        ><em>查看步骤与关键判断 →</em>
      </button>
      <section v-if="guide.content.openQuestions.length">
        <h4>还需要知道</h4>
        <ul>
          <li
            v-for="item in guide.content.openQuestions.slice(0, 3)"
            :key="item"
          >
            {{ item }}
          </li>
        </ul>
        <details v-if="guide.content.openQuestions.length > 3">
          <summary>其余未知项</summary>
          <ul>
            <li
              v-for="item in guide.content.openQuestions.slice(3)"
              :key="item"
            >
              {{ item }}
            </li>
          </ul>
        </details>
      </section>
      <p class="guide-scope-note">
        这是代表性内容导览，未读取的实现与外部服务可能影响结论。
      </p>
    </template>
    <section v-else class="guide-detail" aria-label="了解一件事">
      <button class="button secondary" type="button" @click="select('')">
        ← 返回项目概要
      </button>
      <h3>{{ module?.name ?? flow?.name ?? '项目的输入与输出' }}</h3>
      <template v-if="selected === 'project'"
        ><p>输入：{{ guide.content.project.input }}</p>
        <p>输出：{{ guide.content.project.output }}</p></template
      >
      <template v-if="module"
        ><p>{{ module.responsibility }}</p>
        <p>输入：{{ module.input }}</p>
        <p>输出：{{ module.output }}</p>
        <ul>
          <li v-for="item in module.capabilities" :key="item">{{ item }}</li>
        </ul>
        <p v-if="!relatedFlows.length">
          这部分还没有足够证据串起完整流程，可以继续提问。
        </p></template
      >
      <article
        v-for="item in currentFlows"
        :key="item.id"
        class="guide-flow-detail"
      >
        <h4>{{ item.name }}</h4>
        <p>{{ item.goal }}</p>
        <p>参与者：{{ item.actors.join('、') }}</p>
        <p>何时开始：{{ item.trigger }}</p>
        <ol>
          <li v-for="(step, index) in item.steps" :key="index">
            <b>{{ step.action }}</b>
            <p>输入：{{ step.input }}；结果：{{ step.result }}</p>
            <small>{{ conclusionLabel(step.conclusionStatus) }}</small>
          </li>
        </ol>
        <h5 v-if="item.rules.length">关键判断</h5>
        <ul>
          <li v-for="rule in item.rules" :key="rule.text">
            {{ rule.text }}
            <small>（{{ conclusionLabel(rule.conclusionStatus) }}）</small>
          </li>
        </ul>
        <h5 v-if="item.outcomes.length">结果与失败情况</h5>
        <ul>
          <li v-for="outcome in item.outcomes" :key="outcome.text">
            {{ outcome.text }}
            <small>（{{ conclusionLabel(outcome.conclusionStatus) }}）</small>
          </li>
        </ul>
        <ul v-if="item.unknowns.length">
          <li v-for="unknown in item.unknowns" :key="unknown">
            待确认：{{ unknown }}
          </li>
        </ul>
      </article>
      <div class="guide-detail-actions">
        <button
          class="button secondary"
          type="button"
          :aria-expanded="evidenceOpen"
          @click="evidenceOpen = !evidenceOpen"
        >
          {{ evidenceOpen ? '收起实现依据' : '查看实现依据' }}</button
        ><button
          class="button primary"
          type="button"
          @click="
            emit(
              'chat',
              `请继续解释“${module?.name ?? flow?.name ?? guide.content.project.name}”：先讲使用目的、步骤、关键判断和结果，区分源码观察与未知。只有需要深入时再展开技术实现。`,
            )
          "
        >
          继续问 AI
        </button>
      </div>
      <section v-if="evidenceOpen" class="guide-evidence" aria-label="实现依据">
        <p>
          建议先读入口与实现，再核对测试和文档。以下是同一快照中的实际片段；测试存在不表示已运行。
        </p>
        <p v-if="!currentEvidence.length">
          当前内容没有可展示的实现依据，不能据此确认实现。
        </p>
        <details v-for="item in currentEvidence" :key="item.id">
          <summary>
            {{ item.filePath }}:{{ item.startLine }}–{{ item.endLine }} ·
            {{ item.sourceKind }}
          </summary>
          <p>{{ item.symbolName }}</p>
          <pre><code>{{ item.code }}</code></pre>
          <small v-if="item.truncated"
            >片段已截断，可从文件结构中继续阅读。</small
          >
        </details>
      </section>
    </section>
    <details class="guide-coverage">
      <summary>阅读范围与限制</summary>
      <p>
        已索引 {{ guide.coverage.indexedFiles }} 个文件；本轮读取
        {{ guide.coverage.readFiles }} 个文件的
        {{ guide.coverage.readChunks }} 个片段。这是抽样，不代表全仓覆盖。
      </p>
      <ul>
        <li v-for="item in guide.coverage.limitations" :key="item">
          {{ item }}
        </li>
      </ul>
      <p>
        快照：{{ guide.snapshot.revision }} · {{ guide.snapshot.readPolicy }}
      </p>
    </details>
  </section>
</template>
<style scoped>
.project-guide {
  color: #263642;
  line-height: 1.7;
}
.guide-hero {
  padding: 24px;
  background: #edf6f3;
  border-radius: 16px;
  margin-bottom: 24px;
}
.guide-hero h3 {
  font-size: 23px;
  line-height: 1.6;
  margin: 8px 0;
}
.guide-hero small {
  display: block;
}
.guide-hero button {
  margin-top: 12px;
  background: transparent;
  border: 0;
  color: #167260;
  cursor: pointer;
}
.guide-module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 12px;
}
.guide-module-card {
  background: #fff;
  border: 1px solid #dce5e2;
  border-radius: 12px;
  text-align: left;
  cursor: pointer;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: inherit;
  font: inherit;
}
.guide-module-card:hover {
  border-color: #368676;
  background: #f4faf8;
}
.guide-module-card b {
  font-size: 17px;
}
.guide-module-card small {
  color: #63716d;
}
.guide-module-card em,
.guide-flow-preview em {
  font-style: normal;
  color: #247f6c;
  font-size: 13px;
}
.guide-flow-preview {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: 18px;
  background: #fff;
  border: 1px solid #dce5e2;
  border-radius: 12px;
  text-align: left;
  margin: 10px 0;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.guide-flow-preview small {
  line-height: 1.8;
}
.guide-detail {
  padding: 16px;
}
.guide-flow-detail {
  border-top: 1px solid #dde4e1;
  padding-top: 12px;
  margin-top: 16px;
}
.guide-flow-detail li {
  margin: 12px 0;
}
.guide-detail-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.guide-evidence {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7f6;
  border-radius: 12px;
}
.guide-evidence details {
  margin: 12px 0;
}
.guide-evidence pre {
  overflow: auto;
  max-height: 400px;
  background: #1f2935;
  color: #e2e9ee;
  padding: 16px;
  white-space: pre;
  max-width: 100%;
}
.guide-coverage {
  margin-top: 24px;
  color: #63716d;
  font-size: 13px;
  border-top: 1px solid #e1e7e4;
  padding: 16px;
}
.guide-coverage p {
  overflow-wrap: anywhere;
}
.guide-scope-note {
  color: #63716d;
  font-size: 13px;
  margin-top: 18px;
}
</style>
