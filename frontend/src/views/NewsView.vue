<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { notices } from '../data/demo';
import type { Notice } from '../domain/types';
import { useAppStore } from '../stores/app';
import { useStudyStore } from '../stores/study';
import { backend } from '../api/backend';
import { useLoad } from '../composables/useLoad';
import AsyncState from '../components/AsyncState.vue';
import AppIcon from '../components/AppIcon.vue';
const app = useAppStore();
const study = useStudyStore();
const { status, error, run } = useLoad();
const category = ref('全部');
const query = ref('');
const list = ref<Notice[]>(app.mode === 'demo' ? notices : []);
const selected = ref<string | null>(null);
const followedOnly = ref(false);
const filtered = computed(() =>
  list.value.filter(
    (n) =>
      (category.value === '全部' || n.category === category.value) &&
      n.title.includes(query.value) &&
      (!followedOnly.value || study.state.followed.includes(n.id)),
  ),
);
async function load() {
  if (app.mode === 'live') {
    list.value = [];
    const data = await run(() => backend.notices(category.value));
    if (data) list.value = data;
  }
}
function follow(id: string) {
  if (app.mode !== 'demo') {
    app.notify('关注与推送接口尚未实现');
    return;
  }
  if (study.state.followed.includes(id))
    study.state.followed = study.state.followed.filter((v) => v !== id);
  else study.state.followed.push(id);
}
onMounted(load);
</script>
<template>
  <div class="split">
    <div class="stack">
      <section class="card">
        <div class="card-heading">
          <h2>重要事项与政策资讯</h2>
          <span class="tag">来源可核验</span>
        </div>
        <form class="form-grid" @submit.prevent="load">
          <label class="field"
            >资讯分类<select v-model="category" @change="load">
              <option
                v-for="c in ['全部', '报名提醒', '网上确认', '准考证', '初试提醒', '院校政策']"
                :key="c"
              >
                {{ c }}
              </option>
            </select></label
          ><label class="field"
            >搜索标题<input v-model="query" placeholder="输入关键词筛选" maxlength="100"
          /></label>
        </form>
        <div class="row between space-top">
          <label class="inline-label"
            ><input
              v-model="followedOnly"
              type="checkbox"
              :disabled="app.mode === 'live'"
            />只看本地关注</label
          ><button class="text-btn" :disabled="status === 'loading'" @click="load">刷新资讯</button>
        </div>
      </section>
      <AsyncState :status="status" :error="error" @retry="load" />
      <article v-for="n in filtered" :key="n.id" class="card news-card">
        <span class="news-date"
          ><AppIcon name="calendar" :size="23" />
          <p>{{ n.date }}</p></span
        >
        <div class="news-body">
          <div class="row wrap">
            <span class="tag" :class="n.important ? 'orange' : ''">{{ n.category }}</span
            ><span v-if="n.important" class="muted small">优先核对</span>
          </div>
          <h3>{{ n.title }}</h3>
          <p>来源：{{ n.source }}</p>
          <p v-if="selected === n.id" class="reading">{{ n.content }}</p>
          <p v-else>{{ n.content.slice(0, 65) }}…</p>
          <div class="row between space-top">
            <button class="text-btn" @click="selected = selected === n.id ? null : n.id">
              {{ selected === n.id ? '收起详情' : '查看详情' }}</button
            ><button class="text-btn" @click="follow(n.id)">
              <AppIcon name="bell" :size="14" />{{
                study.state.followed.includes(n.id) ? '已关注 · 取消' : '关注事项'
              }}
            </button>
          </div>
        </div>
      </article>
      <div v-if="!filtered.length && status !== 'loading'" class="state-box">
        暂无匹配资讯，试试其他分类或关键词。
      </div>
    </div>
    <div class="stack">
      <section class="card">
        <h2>备考事项清单</h2>
        <div
          v-for="(s, i) in [
            '报名信息核对',
            '报考点确认材料',
            '准考证与考场信息',
            '初试时间与考试用品',
          ]"
          :key="s"
          class="task-row"
        >
          <span class="number-badge">{{ i + 1 }}</span>
          <div class="task-info">
            <h3>{{ s }}</h3>
            <p>日期待官方公告确认</p>
          </div>
        </div>
        <p class="muted small space-top">
          演示不设置虚构官方日期。真实通知应包含来源、发布时间与版本。
        </p>
      </section>
      <section class="card">
        <h2>提醒与订阅</h2>
        <p class="muted small space-top">
          本地关注
          {{ study.state.followed.length }}
          项演示事项。真实院校更新、政策变化和实时推送需后端轮询或事件通知。
        </p>
        <a
          class="text-btn space-top"
          href="https://yz.chsi.com.cn/"
          target="_blank"
          rel="noopener noreferrer"
          >前往中国研究生招生信息网<AppIcon name="arrow" :size="14"
        /></a>
      </section>
    </div>
  </div>
</template>
