<script setup lang="ts">
import { computed, ref } from 'vue';
import { professionalCourses } from '../data/demo';
import CourseLibrary from '../components/CourseLibrary.vue';
import AppIcon from '../components/AppIcon.vue';
import LiveUnavailable from '../components/LiveUnavailable.vue';
import { useAppStore } from '../stores/app';
const app = useAppStore();
const discipline = ref('计算机');
const course = computed(() => professionalCourses[discipline.value]!);
</script>
<template>
  <div class="tabs" role="group" aria-label="专业学科">
    <button
      v-for="d in Object.keys(professionalCourses)"
      :key="d"
      class="tab"
      :class="{ active: discipline === d }"
      @click="discipline = d"
    >
      {{ d }}
    </button>
  </div>
  <LiveUnavailable v-if="app.mode === 'live'" capability="专业知识体系与参考书资料" />
  <div v-else class="grid two">
    <section class="card">
      <div class="card-heading">
        <h2><AppIcon name="layers" />{{ discipline }}知识体系</h2>
        <span class="tag">重难点与真题思路</span>
      </div>
      <div v-for="(unit, i) in course.units" :key="unit.id" class="task-row">
        <span class="number-badge">{{ i + 1 }}</span>
        <div class="task-info">
          <h3>{{ unit.title }}</h3>
          <p>{{ unit.points.join(' / ') }}</p>
        </div>
      </div>
    </section>
    <section class="card">
      <h2>参考书与考点导航</h2>
      <ul class="point-list">
        <li v-for="book in course.books" :key="book">{{ book }}</li>
      </ul>
      <p class="muted small">
        此处列出学习方向，不代表目标院校指定书目。选择学校后，应核实正式专业目录、参考书版本及考试范围。
      </p>
      <p class="muted small space-top">
        考点预测入口使用有来源的历史考点分析；当前示例仅展示重点章节，不生成虚构押题结果。
      </p>
      <RouterLink to="/schools" class="text-btn space-top"
        >先确认目标院校<AppIcon name="arrow" :size="15"
      /></RouterLink>
    </section>
  </div>
  <div class="section-gap"><CourseLibrary :units="course.units" subject="专业课" /></div>
</template>
