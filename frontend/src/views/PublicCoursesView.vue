<script setup lang="ts">
import { computed, ref } from 'vue';
import { publicCourses } from '../data/demo';
import type { Subject } from '../domain/types';
import CourseLibrary from '../components/CourseLibrary.vue';
import { useStudyStore } from '../stores/study';
import { useAppStore } from '../stores/app';
const app = useAppStore();
const subject = ref<Subject>('数学');
const study = useStudyStore();
const units = computed(() => publicCourses[subject.value] || []);
const completed = computed(
  () => units.value.filter((u) => study.state.completedUnits.includes(u.id)).length,
);
</script>
<template>
  <div class="tabs" role="group" aria-label="公共课科目">
    <button
      v-for="s in ['数学', '英语', '政治']"
      :key="s"
      class="tab"
      :class="{ active: subject === s }"
      @click="subject = s as Subject"
    >
      {{ s }}
    </button>
  </div>
  <section class="hero">
    <div>
      <div class="hero-kicker">
        {{
          subject === '数学'
            ? 'THINK CLEARLY'
            : subject === '英语'
              ? 'LEARN IN CONTEXT'
              : 'BUILD YOUR KNOWLEDGE MAP'
        }}
      </div>
      <h2>
        {{
          subject === '数学'
            ? '懂方法，才更会做题。'
            : subject === '英语'
              ? '让每个词句，都有自己的语境。'
              : '先建立框架，再把知识连起来。'
        }}
      </h2>
      <p>
        {{
          subject === '数学'
            ? '推导讲解 · 题型训练 · 易错点复盘'
            : subject === '英语'
              ? '词汇记忆 · 长难句拆解 · 作文练习'
              : '大纲整理 · 带背训练 · 时政与考点'
        }}
      </p>
      <span v-if="app.mode === 'demo'" class="tag"
        >{{ completed }} / {{ units.length }} 个示例单元完成</span
      >
    </div>
  </section>
  <CourseLibrary :units="units" :subject="subject" />
</template>
