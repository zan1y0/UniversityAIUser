<script setup lang="ts">
import { computed, ref } from 'vue';
import { schools } from '../data/demo';
import type { School } from '../domain/types';
import { useAppStore } from '../stores/app';
import { useLoad } from '../composables/useLoad';
import { backend } from '../api/backend';
import AsyncState from '../components/AsyncState.vue';
import AppIcon from '../components/AppIcon.vue';
const app = useAppStore();
const { status, error, run } = useLoad();
const major = ref('计算机技术');
const score = ref(340);
const region = ref('全部地区');
const difficulty = ref('全部梯度');
const list = ref<School[]>(app.mode === 'demo' ? schools : []);
const selected = ref<string[]>([]);
const estimate = ref(false);
const filtered = computed(() =>
  list.value.filter(
    (s) =>
      (major.value === '全部专业' || s.major === major.value) &&
      (region.value === '全部地区' || s.region === region.value) &&
      (difficulty.value === '全部梯度' || s.difficulty === difficulty.value),
  ),
);
const comparison = computed(() => list.value.filter((s) => selected.value.includes(s.id)));
async function search() {
  estimate.value = false;
  if (app.mode === 'live') {
    list.value = [];
    selected.value = [];
    const result = await run(() => backend.schools(major.value, score.value));
    if (result) list.value = result;
  } else app.notify(`已按专业、地区与梯度筛选 ${filtered.value.length} 所示例院校`);
}
function compare(id: string) {
  if (selected.value.includes(id)) selected.value = selected.value.filter((v) => v !== id);
  else if (selected.value.length < 3) selected.value.push(id);
  else app.notify('最多同时对比 3 所院校');
}
</script>
<template>
  <section class="card">
    <div class="card-heading">
      <h2>找到适合你的目标</h2>
      <span class="tag">择校分析</span>
    </div>
    <form @submit.prevent="search">
      <div class="grid four">
        <label class="field"
          >本科 / 目标专业<select v-model="major">
            <option
              v-for="m in ['计算机技术', '软件工程', '教育学', '工商管理', '临床医学', '全部专业']"
              :key="m"
            >
              {{ m }}
            </option>
          </select></label
        ><label class="field"
          >参考分数<input v-model.number="score" type="number" min="0" max="500" required /></label
        ><label class="field"
          >意向地区<select v-model="region">
            <option v-for="r in ['全部地区', '华东', '华北', '华南', '华中', '西部']" :key="r">
              {{ r }}
            </option>
          </select></label
        ><label class="field"
          >目标梯度<select v-model="difficulty">
            <option v-for="d in ['全部梯度', '冲刺', '稳妥', '保底']" :key="d">{{ d }}</option>
          </select></label
        >
      </div>
      <div class="form-actions">
        <button class="btn" :disabled="status === 'loading'">
          <AppIcon name="search" :size="16" />查看推荐</button
        ><span class="muted small">真实接口当前仅接收专业与分数；地区与梯度在返回结果中筛选。</span>
      </div>
    </form>
  </section>
  <AsyncState :status="status" :error="error" @retry="search" />
  <div class="row between section-gap">
    <h2>
      院校推荐 <span class="muted small">{{ filtered.length }} 所</span>
    </h2>
    <span class="muted small">可选择最多 3 所院校进行对比</span>
  </div>
  <div v-if="!filtered.length && status !== 'loading'" class="state-box">
    没有匹配的院校，试试调整筛选条件。
  </div>
  <div class="grid three section-gap">
    <article v-for="s in filtered" :key="s.id" class="card">
      <div class="row between">
        <span class="module-icon"><AppIcon name="school" /></span
        ><span
          class="tag"
          :class="s.difficulty === '冲刺' ? 'orange' : s.difficulty === '保底' ? 'green' : ''"
          >{{ s.difficulty }}</span
        >
      </div>
      <h3>{{ s.name }}</h3>
      <p class="muted small space-top">{{ s.region }} · {{ s.major }}</p>
      <div class="grid two space-top">
        <div>
          <p class="muted small">参考分数线</p>
          <p class="summary-value">{{ s.line }}</p>
        </div>
        <div>
          <p class="muted small">招生人数</p>
          <p class="summary-value">{{ s.enrollment }}</p>
        </div>
      </div>
      <p class="muted small space-top">报录比 {{ s.ratio }} · {{ s.scoreRange }}</p>
      <p class="muted small">{{ s.source }}</p>
      <button
        class="btn secondary stretch space-top"
        :class="{ light: selected.includes(s.id) }"
        @click="compare(s.id)"
      >
        {{ selected.includes(s.id) ? '已加入对比 · 点击移除' : '加入对比' }}
      </button>
    </article>
  </div>
  <section v-if="comparison.length" class="card comparison">
    <div class="card-heading">
      <h2>院校对比</h2>
      <button class="text-btn" @click="selected = []">清空对比</button>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>指标</th>
            <th v-for="s in comparison" :key="s.id">{{ s.name }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in [
              { key: 'major', title: '专业' },
              { key: 'line', title: '分数线' },
              { key: 'enrollment', title: '招生人数' },
              { key: 'ratio', title: '报录比' },
              { key: 'risk', title: '风险提示' },
            ]"
            :key="item.key"
          >
            <td>{{ item.title }}</td>
            <td v-for="s in comparison" :key="s.id">{{ s[item.key as keyof School] }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <button class="btn light space-top" @click="estimate = !estimate">查看录取评估</button>
    <div v-if="estimate" class="notice space-top">
      <strong>录取概率暂不可计算</strong>
      <p>
        需要同口径的真实招生数据、考生信息与已验证模型。当前不提供虚构的录取百分比。{{
          app.mode === 'demo' ? '以下仅展示示例分数差及风险提示。' : ''
        }}
      </p>
      <p v-for="s in comparison" :key="s.id">
        {{ s.name }}：参考分数差 {{ score - s.line }} 分。{{ s.risk }}
      </p>
    </div>
  </section>
</template>
