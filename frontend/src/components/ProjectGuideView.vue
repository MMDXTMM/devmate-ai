<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch, useId } from 'vue'
import { readReadingLocation, writeReadingLocation, subscribeReadingLocation } from '../utils/readingLocation'
import EvidenceCode from './EvidenceCode.vue'
import { sourceLabel } from '../utils/sourcePresentation'
import ProjectBusinessDiagram from './ProjectBusinessDiagram.vue'
import { moduleEmoji, projectEmoji } from '../utils/guideAppearance'
import type {
  ProjectGuide,
  GuideImplementation,
  GuideConclusion,
} from '../types/project'
const props = defineProps<{ guide: ProjectGuide; projectId?: string }>()
const emit = defineEmits<{ chat: [question: string] }>()
const highlightsTitleId = useId()
const selected = ref('')
const evidenceOpen = ref(false)
const expandedSteps = ref<Record<string, boolean>>({})
const evidenceSelection = ref<string[]>()
function showEvidence(ids: string[]) { evidenceSelection.value = ids; evidenceOpen.value = true }
function restoreSelection() {
  if (!props.projectId) { selected.value = ''; return }
  const route = readReadingLocation()
  const item = route.projectId === props.projectId && route.revision === props.guide.snapshot.revision ? route.item ?? '' : ''
  const valid = item === 'project' || props.guide.content.modules.some(m => item === `module:${m.id}`)
    || props.guide.content.flows.some(f => item === `flow:${f.id}`)
    || /^highlight:\d+$/.test(item) && !!props.guide.content.overview?.highlights[Number(item.split(':')[1])]
  selected.value = valid ? item : ''
  evidenceOpen.value = false
  evidenceSelection.value = undefined
}
watch(() => props.guide, restoreSelection, { immediate: true })
const stopReading = subscribeReadingLocation(restoreSelection)
onBeforeUnmount(stopReading)
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
const highlight = computed(() => {
  if (!selected.value.startsWith('highlight:')) return undefined
  return props.guide.content.overview?.highlights[
    Number(selected.value.slice('highlight:'.length))
  ]
})
const relatedFlows = computed(() => {
  const ids =
    highlight.value?.moduleIds ?? (module.value ? [module.value.id] : [])
  return props.guide.content.flows.filter((item) =>
    item.moduleIds.some((id) => ids.includes(id)),
  )
})
const currentFlows = computed(() =>
  flow.value ? [flow.value] : relatedFlows.value,
)
const currentEvidence = computed(() => {
  const ids = new Set(
    selected.value === 'project'
      ? props.guide.content.project.evidenceIds
      : (highlight.value?.evidenceIds ?? module.value?.evidenceIds ?? []),
  )
  currentFlows.value.forEach((item) => {
    item.transitions?.forEach(t => t.evidenceIds.forEach(id => ids.add(id)))
    item.steps.forEach((step) => step.evidenceIds.forEach((id) => ids.add(id)))
    ;[...item.rules, ...item.outcomes].forEach((claim) =>
      claim.evidenceIds.forEach((id) => ids.add(id)),
    )
  })
  return props.guide.evidence.filter((item) => evidenceSelection.value ? evidenceSelection.value.includes(item.id) : ids.has(item.id))
})
const implementationLabel = (status: GuideImplementation) =>
  ({
    IMPLEMENTED: '源码中已实现',
    PARTIAL: '已观察到部分实现',
    PLANNED: '仅计划',
    UNCLEAR: '本轮未核实实现',
  })[status]
const conclusionLabel = (status: GuideConclusion) =>
  ({ OBSERVED: '证据观察', INFERRED: '推断', UNKNOWN: '未确认' })[status]
function select(value: string) {
  selected.value = value
  if (props.projectId) writeReadingLocation({ projectId: props.projectId, revision: props.guide.snapshot.revision, item: value || undefined })
  evidenceOpen.value = false
  evidenceSelection.value = undefined
}
</script>
<template>
  <section class="project-guide" aria-label="项目通俗导览">
    <template v-if="!selected">
      <header class="guide-hero">
        <div class="guide-welcome"><span class="guide-mascot" aria-hidden="true">{{ projectEmoji({}, guide.content.appearance) }}</span><div><span class="eyebrow">很高兴认识你，{{ guide.content.project.name }}</span><p>一起把这个项目读懂吧</p></div><span class="guide-sticker" aria-hidden="true">✨</span></div>
        <span class="guide-hero-label">这个项目是做什么的</span>
        <h3>{{ guide.content.project.purpose }}</h3>
        <p class="guide-project-type">{{ guide.content.project.type }}</p>
        <div class="guide-languages" aria-label="项目语言">
          <b>已识别语言：</b
          ><span v-for="language in guide.languages ?? []" :key="language">{{
            language
          }}</span
          ><small v-if="!guide.languages?.length"
            >当前读取范围未确认源码语言</small
          >
        </div>
        <p><b>🧑‍💻 给谁用：</b>{{ guide.content.project.audience }}</p>
        <p class="guide-scenario">💭 {{ guide.content.project.scenario }}</p>
        <small
          >{{ guide.content.project.conclusionStatus === 'UNKNOWN' ? '本轮未核实实现' : implementationLabel(guide.content.project.implementationStatus) }}
          ·
          {{
            conclusionLabel(guide.content.project.conclusionStatus)
          }}；依据静态阅读，未验证运行</small
        >
        <button type="button" @click="select('project')">了解输入与输出</button>
      </header>
      <div class="guide-section-heading"><span aria-hidden="true">🧩</span><div><h4>有哪些模块 · 能帮你做什么</h4><p>每张卡片都是项目的一块拼图，点开看看它负责什么。</p></div></div>
      <div class="guide-module-grid">
        <button
          v-for="(item, index) in guide.content.modules"
          :key="item.id"
          class="guide-module-card"
          :data-tone="index % 4"
          type="button"
          @click="select(`module:${item.id}`)"
        >
          <span class="guide-card-top"><span class="guide-module-emoji" aria-hidden="true">{{ moduleEmoji(item, guide.content.appearance) }}</span><span class="guide-card-number">MODULE {{ String(index + 1).padStart(2, '0') }}</span></span>
          <b>{{ item.name }}</b
          ><span class="guide-short-responsibility">{{ item.responsibility }}</span
          ><small
            >{{ item.conclusionStatus === 'UNKNOWN' ? '本轮未核实实现' : implementationLabel(item.implementationStatus) }} ·
            {{ conclusionLabel(item.conclusionStatus) }}</small
          ><em>了解这块业务 →</em>
        </button>
      </div>
      <details class="guide-extra"><summary>🗺️ 展开业务地图与路线</summary><ProjectBusinessDiagram :guide="guide" @select-module="select(`module:${$event}`)" @select-flow="select(`flow:${$event}`)" /></details>
      <details v-if="guide.content.overview" class="guide-extra"><summary>💡 想深入学习：看看设计与练习建议</summary><section
        v-if="guide.content.overview"
        class="guide-highlights"
        :aria-labelledby="highlightsTitleId"
      >
        <h4 :id="highlightsTitleId">💡 哪些模块值得关注 · 有什么学习价值</h4>
        <p class="guide-learning-note">
          这些是基于已读实现的学习建议；不代表性能或运行效果已通过验证。
        </p>
        <div
          v-if="guide.content.overview.highlights.length"
          class="guide-highlight-grid"
        >
          <button
            v-for="(item, index) in guide.content.overview.highlights"
            :key="index"
            class="guide-highlight-card"
            type="button"
            @click="select(`highlight:${index}`)"
          >
            <b>{{ item.title }}</b
            ><span><strong>解决的问题：</strong>{{ item.problem }}</span>
            <span><strong>值得学习：</strong>{{ item.learningValue }}</span>
            <small
              >{{ item.conclusionStatus === 'UNKNOWN' ? '本轮未核实实现' : implementationLabel(item.implementationStatus) }} ·
              {{ conclusionLabel(item.conclusionStatus) }}</small
            >
            <em>了解设计与学习建议 →</em>
          </button>
        </div>
        <p v-else>
          当前证据还不足以推荐具体的实现亮点。可以先从上面的能力模块开始理解。
        </p>
      </section>
      </details>
      <h4 v-if="guide.content.flows.length">👣 一件事是怎样完成的</h4>
      <button
        v-for="item in guide.content.flows.slice(0, 1)"
        :key="item.id"
        class="guide-flow-preview"
        type="button"
        @click="select(`flow:${item.id}`)"
      >
        <b>{{ item.name }}</b
        ><span>{{ item.goal }}</span
        ><small
          >{{ item.trigger }} →
          {{ item.goal }}</small
        ><small v-if="item.rules.length"
          >关键判断：{{ item.rules[0]?.text }}</small
        ><em>查看步骤与关键判断 →</em>
      </button>
      <details v-if="guide.content.flows.length > 1" class="guide-extra"><summary>查看其他主流程</summary>
        <button v-for="item in guide.content.flows.slice(1)" :key="item.id" type="button" class="guide-flow-preview" @click="select(`flow:${item.id}`)"><b>{{ item.name }}</b><span>{{ item.goal }}</span></button>
      </details>
      <details v-if="guide.content.openQuestions.length" class="guide-extra"><summary>哪些内容还没有核实</summary><section>
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
      </details>
      <p class="guide-scope-note">
        这是代表性内容导览，未读取的实现与外部服务可能影响结论。
      </p>
    </template>
    <section v-else class="guide-detail" aria-label="了解一件事">
      <button class="button secondary" type="button" @click="select('')">
        ← 返回项目概要
      </button>
      <h3>
        <span aria-hidden="true">{{ module ? moduleEmoji(module, guide.content.appearance) : highlight ? '💡' : flow ? '👣' : projectEmoji({}, guide.content.appearance) }}</span>
        {{
          highlight?.title ?? module?.name ?? flow?.name ?? '项目的输入与输出'
        }}
      </h3>
      <template v-if="selected === 'project'"
        ><p>输入：{{ guide.content.project.input }}</p>
        <p>输出：{{ guide.content.project.output }}</p></template
      >
      <section v-if="highlight" class="guide-highlight-detail">
        <h4>它解决了什么问题</h4>
        <p>{{ highlight.problem }}</p>
        <h4>项目采用的做法</h4>
        <p>{{ highlight.solution }}</p>
        <h4>你可以学到什么</h4>
        <p>{{ highlight.learningValue }}</p>
        <h4>建议怎么学</h4>
        <p>{{ highlight.suggestedExercise }}</p>
        <p><b>边界与未确认项：</b>{{ highlight.limitation }}</p>
        <small
          >{{ implementationLabel(highlight.implementationStatus) }} ·
          {{
            conclusionLabel(highlight.conclusionStatus)
          }}。学习练习是建议，系统未执行。</small
        >
      </section>
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
            <small>{{ conclusionLabel(step.conclusionStatus) }}</small><button v-if="step.evidenceIds.length" type="button" class="guide-claim-evidence" @click="showEvidence(step.evidenceIds)">核对这一步的依据</button>
            <details v-if="step.evidenceIds.length" class="guide-step-implementation" @toggle="expandedSteps[`${item.id}:${index}`] = ($event.target as HTMLDetailsElement).open"><summary>展开这一步的实现代码</summary>
              <template v-if="expandedSteps[`${item.id}:${index}`]"><article v-for="evidence in guide.evidence.filter(entry => step.evidenceIds.includes(entry.id))" :key="evidence.id">
                <b>{{ evidence.filePath }}:{{ evidence.startLine }}–{{ evidence.endLine }}</b><small>{{ sourceLabel(evidence.filePath, evidence.sourceKind) }}</small>
                <EvidenceCode :project-id="projectId" :revision="guide.snapshot.revision" :chunk-id="evidence.id" :file-path="evidence.filePath" :code="evidence.code" :truncated="evidence.truncated" />
              </article></template>
            </details>
            <button type="button" class="guide-claim-evidence" @click="emit('chat', `请围绕「${item.name}」的第${index + 1}步「${step.action}」讲解：入口是什么、关键判断、数据变化及失败分支。优先读取这一步证据涉及的实现及直接关联代码，区分源码观察、推断与待确认。`)">追问这一步</button>
          </li>
        </ol>
        <section v-if="item.transitions?.length" class="guide-branches"><h5>在这里判断，会走向哪里</h5>
          <p v-for="(transition, index) in item.transitions" :key="index">步骤 {{ transition.fromStep }} · {{ transition.condition }} → {{ transition.result }} → {{ transition.toStep ? `进入步骤 ${transition.toStep}` : '到此结束' }}<button type="button" class="guide-claim-evidence" @click="showEvidence(transition.evidenceIds)">核对分支依据</button></p>
        </section>
        <h5 v-if="item.rules.length">关键判断</h5>
        <ul>
          <li v-for="rule in item.rules" :key="rule.text">
            {{ rule.text }}<button v-if="rule.evidenceIds.length" type="button" class="guide-claim-evidence" @click="showEvidence(rule.evidenceIds)">核对依据</button>
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
          @click="evidenceSelection = undefined; evidenceOpen = !evidenceOpen"
        >
          {{ evidenceOpen ? '收起实现依据' : '查看实现依据' }}</button
        ><button
          class="button primary"
          type="button"
          @click="
            emit(
              'chat',
              `请继续解释“${highlight?.title ?? module?.name ?? flow?.name ?? guide.content.project.name}”：先讲使用目的、步骤、关键判断和结果，区分源码观察与未知。只有需要深入时再展开技术实现。`,
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
            {{ sourceLabel(item.filePath, item.sourceKind) }}
          </summary>
          <p>{{ item.symbolName }}</p>
          <EvidenceCode :project-id="projectId" :revision="guide.snapshot.revision" :chunk-id="item.id" :file-path="item.filePath" :code="item.code" :truncated="item.truncated" />
        </details>
      </section>
    </section>
    <details v-if="guide.content.glossary.length" class="guide-extra guide-glossary">
      <summary>📖 先认识这些词 · {{ guide.content.glossary.length }} 个术语</summary>
      <dl><template v-for="item in guide.content.glossary" :key="item.term"><dt>{{ item.term }}</dt><dd>{{ item.explanation }}</dd></template></dl>
    </details>
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
  background: linear-gradient(125deg, #f1eafa, #fdf6ec 70%, #f0f7ef);
  border: 1px solid #e7dff0;
  border-radius: 24px;
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
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 230px), 1fr));
  gap: 12px;
}
.guide-module-card {
  background: var(--card-bg, #f2f8f4);
  border: 1px solid var(--card-line, #d7e6dc);
  border-radius: 22px;
  text-align: left;
  cursor: pointer;
  padding: 22px;
  box-shadow: 0 4px 0 var(--card-line, #d7e6dc);
  transition: transform 160ms ease, box-shadow 160ms ease;
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: inherit;
  font: inherit;
}
.guide-module-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 7px 0 var(--card-line, #d7e6dc);
}
.guide-module-card > b {
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
  padding: 22px;
  border: 1px solid #e4dfec;
  border-radius: 22px;
  background: #fffcf8;
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
.guide-languages {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 13px;
  margin: 12px 0;
}
.guide-languages span {
  background: #fff;
  border: 1px solid #cfdfd6;
  border-radius: 20px;
  padding: 2px 10px;
}
.guide-project-type,
.guide-learning-note {
  color: #617369;
  font-size: 13px;
}
.guide-highlight-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
  gap: 12px;
}
.guide-highlight-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px;
  border: 1px solid #dedfcf;
  background: #fff8e8;
  border-radius: 20px;
  text-align: left;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.guide-highlight-card:hover {
  border-color: #8d9b63;
}
.guide-highlight-card > b {
  font-size: 17px;
}
.guide-highlight-card small {
  color: #657366;
}
.guide-highlight-card em {
  font-size: 13px;
  font-style: normal;
  color: #42724b;
}
.guide-highlight-detail {
  margin-top: 20px;
  padding: 18px;
  background: #fff8e8;
  border-radius: 20px;
}
.guide-highlight-detail h4 {
  margin: 16px 0 6px;
}
.guide-highlights {
  margin: 24px 0;
}

.guide-welcome { display:flex; align-items:center; gap:14px; margin-bottom:24px; }
.guide-welcome .eyebrow { color:#6e567e; font-size:12px; letter-spacing:.03em; }
.guide-welcome p { margin:3px 0 0; color:#77677b; font-size:12px; }
.guide-mascot { display:grid; place-items:center; width:62px; height:62px; flex:0 0 auto; background:#fffafc; border:1px solid #e8d6e8; border-radius:22px; box-shadow:0 4px 0 #e8ddec; font-size:34px; transform:rotate(-6deg); }
.guide-sticker { margin-left:auto; font-size:26px; }
.guide-hero-label { font-size:12px; color:#79647c; }
.guide-scenario { background:#ffffffa8; border-radius:14px; padding:12px 16px; }
.guide-section-heading { display:flex; gap:12px; align-items:center; margin:30px 0 16px; }
.guide-section-heading > span { background:#f1ecfa; padding:12px; font-size:24px; border-radius:16px; }
.guide-section-heading h4 { margin:0; font-size:20px; }
.guide-section-heading p { margin:4px 0 0; color:#69756d; font-size:12px; }
.guide-card-top { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:8px; }
.guide-module-emoji { display:grid; place-items:center; width:48px; height:48px; font-size:28px; background:#ffffffc9; border-radius:16px; }
.guide-card-number { font-size:10px; color:#6d706f; font-weight:700; letter-spacing:.08em; }
.guide-module-card[data-tone="1"] { --card-bg:#f3eefa; --card-line:#e1d6ef; }
.guide-module-card[data-tone="2"] { --card-bg:#fff3e7; --card-line:#f0dfc8; }
.guide-module-card[data-tone="3"] { --card-bg:#edf4fc; --card-line:#d6e2f1; }
.guide-module-card:focus-visible, .guide-hero button:focus-visible, .guide-highlight-card:focus-visible, .guide-flow-preview:focus-visible { outline:3px solid #8c6db0; outline-offset:4px; }
@media(prefers-reduced-motion:reduce) { .guide-module-card { transition:none; } .guide-module-card:hover { transform:none; } }
@media(max-width:640px) { .guide-hero { padding:18px; } .guide-hero h3 { font-size:20px; } .guide-detail { padding:16px; } .guide-welcome { gap:10px; } .guide-sticker { display:none; } }
</style>

<style scoped>
.guide-extra { margin: 16px 0; border: 1px solid #e3deea; border-radius: 12px; padding: 12px 16px; }
.guide-extra > summary { cursor: pointer; font-weight: 600; color: #5e496f; }
.guide-short-responsibility { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.guide-claim-evidence { border: 0; background: transparent; color: #68508a; text-decoration: underline; cursor: pointer; margin: 0 8px; }
.guide-glossary dt { font-weight: 700; margin-top: 12px; }
.guide-glossary dd { margin-left: 0; }
</style>
