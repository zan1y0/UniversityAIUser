<script setup lang="ts">
import { ref } from 'vue';
import { teaching } from '../api/teaching';
import type { TeachingClass, TeacherStudent, TeachingPublishClassTarget } from '../api/generated';

const props = defineProps<{ classes: TeachingClass[]; disabled: boolean }>();
const targets = defineModel<TeachingPublishClassTarget[]>({ required: true });
const expanded = ref(new Set<string>());
const rosters = ref<Record<string, TeacherStudent[]>>({});
const errors = ref<Record<string, string>>({});
const loading = ref(new Set<string>());
const targetFor = (classId: string) => targets.value.find(target => target.classId === classId);

function setTarget(classId: string, selection: TeachingPublishClassTarget | null) {
  const remaining = targets.value.filter(target => target.classId !== classId);
  targets.value = selection ? [...remaining, selection] : remaining;
}

function toggleClass(classId: string, checked: boolean) {
  setTarget(classId, checked ? { classId } : null);
}

function toggleStudent(classId: string, studentId: string, checked: boolean) {
  const target = targetFor(classId);
  const selected = new Set(target?.studentIds ?? (target ? rosters.value[classId]?.map(student => student.id) : []));
  if (checked) selected.add(studentId);
  else selected.delete(studentId);
  setTarget(classId, selected.size ? { classId, studentIds: [...selected] } : null);
}

async function toggleRoster(classId: string) {
  if (expanded.value.delete(classId)) return;
  expanded.value.add(classId);
  if (loading.value.has(classId)) return;
  loading.value.add(classId);
  delete errors.value[classId];
  try {
    rosters.value[classId] = (await teaching.roster(classId)).items;
  } catch (error) {
    delete rosters.value[classId];
    errors.value[classId] = error instanceof Error ? error.message : '无法加载班级学生';
  } finally {
    loading.value.delete(classId);
  }
}
</script>

<template>
  <details v-if="props.classes.length" class="recipients">
    <summary>发布对象 <span v-if="targets.length">（已选 {{ targets.length }} 个班级）</span></summary>
    <div v-for="classroom in props.classes" :key="classroom.id" class="recipient-class">
      <div class="recipient-row">
        <label>
          <input type="checkbox" :checked="Boolean(targetFor(classroom.id) && !targetFor(classroom.id)?.studentIds)"
            :indeterminate="Boolean(targetFor(classroom.id)?.studentIds)"
            :disabled="props.disabled || !classroom.memberCount"
            @change="toggleClass(classroom.id, ($event.target as HTMLInputElement).checked)" />
          {{ classroom.name }}（{{ classroom.memberCount }} 人）
        </label>
        <button class="btn" type="button" :aria-expanded="expanded.has(classroom.id)"
          @click="toggleRoster(classroom.id)">{{ expanded.has(classroom.id) ? '收起学生' : '选择学生' }}</button>
      </div>
      <div v-if="expanded.has(classroom.id)" class="roster">
        <p v-if="loading.has(classroom.id)" role="status">正在加载学生…</p>
        <p v-else-if="errors[classroom.id]" role="alert">{{ errors[classroom.id] }}，请收起后重试。</p>
        <p v-else-if="!rosters[classroom.id]?.length" class="muted">该班级暂无学生。</p>
        <label v-for="student in rosters[classroom.id] ?? []" v-else :key="student.id">
          <input type="checkbox" :disabled="props.disabled"
            :checked="Boolean(targetFor(classroom.id) && (targetFor(classroom.id)?.studentIds?.includes(student.id) ?? true))"
            @change="toggleStudent(classroom.id, student.id, ($event.target as HTMLInputElement).checked)" />
          {{ student.displayName }}（{{ student.loginName }}）
        </label>
      </div>
    </div>
  </details>
</template>

<style scoped>
.recipients { padding: 12px; border: 1px solid #d3dfd8; border-radius: 9px; }
.recipients summary { cursor: pointer; }
.recipient-class { padding: 10px 0; border-bottom: 1px solid #edf1ee; }
.recipient-class:last-child { border-bottom: 0; padding-bottom: 0; }
.recipient-row { display: flex; gap: 12px; flex-wrap: wrap; justify-content: space-between; align-items: center; }
.recipients label { display: flex; align-items: center; gap: 8px; margin: 4px 0; }
.recipients input { width: auto; accent-color: #126f5c; }
.roster { display: flex; flex-wrap: wrap; gap: 5px 18px; margin: 8px 0 0 16px; }
</style>
