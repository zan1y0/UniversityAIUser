<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useAppStore } from '../stores/app';
import { useStudyStore } from '../stores/study';
import { useSpeech } from '../composables/useSpeech';
import type { CourseUnit, Subject } from '../domain/types';
import AppIcon from './AppIcon.vue';
import LiveUnavailable from './LiveUnavailable.vue';
const props = defineProps<{ units: CourseUnit[]; subject: Subject }>();
const app = useAppStore();
const study = useStudyStore();
const selected = ref<string | null>(null);
const unit = computed(() => props.units.find((u) => u.id === selected.value));
const speech = useSpeech();
watch(
  () => props.units,
  () => {
    selected.value = null;
    speech.stop();
  },
);
function complete() {
  if (unit.value) {
    study.completeUnit(unit.value.id, props.subject, unit.value.duration);
    app.notify('已记录本地课程完成状态，时长为课程预计时长');
  }
}
</script>
<template>
  <LiveUnavailable v-if="app.mode === 'live'" capability="课程资料、进度与知识讲解" /><template
    v-else
    ><div class="grid three">
      <article v-for="u in units" :key="u.id" class="card course-card">
        <div class="row between">
          <span class="module-icon"><AppIcon name="book" /></span
          ><span class="tag" :class="study.state.completedUnits.includes(u.id) ? 'green' : ''">{{
            study.state.completedUnits.includes(u.id) ? '已学完' : u.tag
          }}</span>
        </div>
        <h3>{{ u.title }}</h3>
        <p class="muted">{{ u.points[0] }} · 预计 {{ u.duration }} 分钟</p>
        <button
          class="btn secondary"
          @click="
            selected = u.id;
            speech.stop();
          "
        >
          {{ selected === u.id ? '正在阅读' : '开始学习' }}<AppIcon name="arrow" :size="15" />
        </button>
      </article>
    </div>
    <section v-if="unit" class="card section-gap">
      <div class="card-heading">
        <h2>{{ unit.title }}</h2>
        <button
          class="text-btn"
          @click="
            selected = null;
            speech.stop();
          "
        >
          收起
        </button>
      </div>
      <p class="reading">{{ unit.content }}</p>
      <ul class="point-list">
        <li v-for="point in unit.points" :key="point">{{ point }}</li>
      </ul>
      <div class="row wrap">
        <button
          class="btn"
          :disabled="study.state.completedUnits.includes(unit.id)"
          @click="complete"
        >
          <AppIcon name="check" :size="16" />{{
            study.state.completedUnits.includes(unit.id) ? '已完成学习' : '标记已学完'
          }}</button
        ><button
          class="btn secondary"
          @click="speech.speaking.value ? speech.stop() : speech.speak(unit.content)"
        >
          <AppIcon name="volume" :size="16" />{{
            speech.speaking.value ? '停止朗读' : '带读讲解'
          }}</button
        ><RouterLink
          class="btn secondary"
          :to="{ path: '/qa', query: { question: `请引导我理解${unit.title}` } }"
          >继续提问</RouterLink
        >
      </div>
      <p v-if="speech.error.value" class="inline-error">{{ speech.error.value }}</p>
      <p class="muted small space-top">
        学习资料为前端演示内容，完成后只记录本地示例进度。实际考纲、真题与参考书应以授权资料为准。
      </p>
    </section></template
  >
</template>
