<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { teaching } from '../api/teaching';
import type { TeachingClass, TeachingPublishClassTarget, TeachingTask, TeacherStudent, TeachingTeacher, TeacherStudentSummary } from '../api/generated';
import { useAppStore } from '../stores/app';
import TeachingRecipients from '../components/TeachingRecipients.vue';
import TeachingTaskCard from '../components/TeachingTaskCard.vue';

const app = useAppStore();
const route = useRoute();
const teacher = computed(() => app.user?.activeRole === 'TEACHER');
const students = ref<TeacherStudent[]>([]);
const teachers = ref<TeachingTeacher[]>([]);
const classes = ref<TeachingClass[]>([]);
const tasks = ref<TeachingTask[]>([]);
const summary = ref<TeacherStudentSummary | null>(null);
const selected = ref('');
const title = ref('');
const description = ref('');
const deadline = ref('');
const requiresSubmission = ref(false);
const recipients = ref<TeachingPublishClassTarget[]>([]);
const viewedClassId = ref('');
const classDialog = ref<HTMLDialogElement | null>(null);
const classDialogOpen = ref(false);
const className = ref('');
const classCode = ref('');
const pendingReviewsOnly = ref(false);
const busy = ref(false);
const error = ref('');
const has = (permission: string) => app.user?.permissions?.includes(permission) === true;
const names = computed(() => new Map([...students.value, ...teachers.value].map(v => [v.id, v.displayName])));
const classNames = computed(() => new Map(classes.value.map(c => [c.id, c.name])));
const viewedClass = computed(() => classes.value.find(c => c.id === viewedClassId.value));
const classTasks = computed(() => viewedClass.value
  ? tasks.value.filter(task => task.classId === viewedClassId.value)
  : tasks.value);
const pendingReviews = computed(() => classTasks.value.filter(task => task.status === 'submitted'));
const visibleTasks = computed(() => pendingReviewsOnly.value ? pendingReviews.value : classTasks.value);
const publications = computed(() => {
  const groups = new Map<string, { task: TeachingTask; classes: Map<string, TeachingTask[]> }>();
  for (const task of visibleTasks.value) {
    const key = task.publicationId ?? task.id;
    const publication = groups.get(key) ?? { task, classes: new Map<string, TeachingTask[]>() };
    const classId = task.classId ?? '';
    const assignments = publication.classes.get(classId) ?? [];
    assignments.push(task);
    publication.classes.set(classId, assignments);
    groups.set(key, publication);
  }
  return groups;
});
const linkedTask = computed(() => typeof route.query.task === 'string' ? route.query.task : '');
async function load() {
  if (teacher.value) {
    const [people, work, classrooms] = await Promise.all([
      teaching.students(), teaching.teacherTasks(),
      has('teacher:class:manage') ? teaching.teacherClasses() : { items: [] },
    ]);
    students.value = people.items; tasks.value = work.items;
    classes.value = classrooms.items;
    if (!students.value.some(s => s.id === selected.value)) { selected.value = ''; summary.value = null; }
  } else {
    const [people, work, classrooms] = await Promise.all([
      teaching.teachers(), teaching.studentTasks(),
      has('student:class:manage') ? teaching.studentClasses() : { items: [] },
    ]);
    teachers.value = people.items; tasks.value = work.items;
    classes.value = classrooms.items;
  }
}
async function action(work: () => Promise<unknown>) {
  if (busy.value) return;
  busy.value = true; error.value = '';
  try { await work(); await load(); }
  catch (e) { error.value = e instanceof Error ? e.message : '操作未完成，请重试'; }
  finally { busy.value = false; }
}
async function showSummary() {
  summary.value = null;
  if (!selected.value) return;
  await action(async () => { summary.value = await teaching.summary(selected.value); });
}
async function publish() {
  await action(async () => {
    if (!title.value.trim() || !deadline.value) throw new Error('请填写标题和截止时间');
    const date = new Date(deadline.value);
    if (!Number.isFinite(date.getTime()) || date.getTime() <= Date.now()) throw new Error('截止时间必须晚于当前时间');
    const body = { title: title.value.trim(), description: description.value.trim(), deadline: date.toISOString(), requiresSubmission: requiresSubmission.value };
    if (recipients.value.length) {
      const result = await teaching.publish({ ...body, classes: recipients.value });
      app.notify(`已发布 ${result.assignedCount} 份任务`);
    } else {
      if (!selected.value) throw new Error('请选择发布对象');
      await teaching.create({ studentId: selected.value, ...body });
      app.notify('教学任务已发布');
    }
    recipients.value = [];
    title.value = ''; description.value = ''; deadline.value = '';
  });
}
function openClassDialog() {
  error.value = '';
  className.value = ''; classCode.value = '';
  classDialog.value?.showModal();
  classDialogOpen.value = true;
}
function selectClass(classroom: TeachingClass) {
  viewedClassId.value = viewedClassId.value === classroom.id ? '' : classroom.id;
}
const saveClass = () => action(async () => {
  const code = classCode.value.trim().toUpperCase();
  if (teacher.value) {
    await teaching.createClass(className.value.trim(), code);
    app.notify('班级已创建');
  } else {
    await teaching.joinClass(code);
    app.notify('已加入班级');
  }
  classDialog.value?.close();
});
function leaveClass(classroom: TeachingClass) {
  if (window.confirm(`退出「${classroom.name}」后，该班任务和成绩将从列表隐藏。确定退出？`)) {
    void action(() => teaching.leaveClass(classroom.id));
  }
}
function updateTask(updated: TeachingTask) {
  tasks.value = tasks.value.map(task => task.id === updated.id ? updated : task);
}
const changeConnection = (t: TeachingTeacher) => action(() => t.connected ? teaching.disconnect(t.id) : teaching.connect(t.id));
const formatTime = (value: string) => new Date(value).toLocaleString('zh-CN');
async function showLinkedTask() {
  if (linkedTask.value) {
    viewedClassId.value = '';
    pendingReviewsOnly.value = false;
  }
  await action(async () => {});
  if (!linkedTask.value) return;
  await nextTick();
  const task = document.getElementById(`teaching-task-${linkedTask.value}`);
  task?.scrollIntoView({ block: 'center' });
  task?.focus({ preventScroll: true });
}
onMounted(showLinkedTask);
watch(linkedTask, showLinkedTask);
</script>

<template>
  <div class="teaching-workspace">
    <p v-if="app.mode !== 'live'" class="notice">教学与指导功能需要登录真实账号。</p>
    <template v-else>
      <p v-if="error && !classDialogOpen" class="inline-error" role="alert">{{ error }}</p>
      <p v-if="busy" role="status">正在处理，请稍候…</p>
      <section v-if="has(teacher ? 'teacher:class:manage' : 'student:class:manage')" class="teaching-card">
        <div class="class-toolbar">
          <h2>{{ teacher ? '班级管理' : '我的班级' }}</h2>
          <button v-for="classroom in classes" :key="classroom.id" class="btn class-choice"
            type="button" :aria-pressed="viewedClassId === classroom.id" @click="selectClass(classroom)">
            {{ classroom.name }}
          </button>
          <button class="btn primary" type="button" :disabled="busy" @click="openClassDialog">
            {{ teacher ? '增添班级' : '加入班级' }}
          </button>
        </div>
        <div v-if="viewedClass" class="teacher-row class-details">
          <span>班级编号 {{ viewedClass.code }} · {{ teacher ? viewedClass.memberCount + ' 名学生' : viewedClass.teacherName }}</span>
          <button v-if="!teacher" class="btn" :disabled="busy" @click="leaveClass(viewedClass)">退出班级</button>
        </div>
        <dialog ref="classDialog" class="class-dialog" aria-labelledby="class-dialog-title"
          @cancel="busy && $event.preventDefault()" @close="classDialogOpen = false; error = ''">
          <h2 id="class-dialog-title">{{ teacher ? '增添班级' : '加入班级' }}</h2>
          <form @submit.prevent="saveClass">
            <label v-if="teacher">班级名称<input v-model="className" required maxlength="80" :disabled="busy" /></label>
            <label>班级编号
              <input v-model.trim="classCode" required pattern="[A-Za-z0-9]{6,16}" maxlength="16" placeholder="6–16 位字母或数字" :disabled="busy" />
            </label>
            <p v-if="!teacher" class="muted">加入后，老师可以给你布置任务、查看作业和学习概况。</p>
            <p v-if="error && classDialogOpen" class="inline-error" role="alert">{{ error }}</p>
            <div class="class-dialog-actions">
              <button class="btn" type="button" :disabled="busy" @click="classDialog?.close()">取消</button>
              <button class="btn primary" type="submit" :disabled="busy">{{ teacher ? '创建班级' : '加入班级' }}</button>
            </div>
          </form>
        </dialog>
      </section>
      <template v-if="teacher">
        <section class="teaching-card">
          <h2>我的指导学生</h2>
          <p class="muted">这里显示加入你班级或授权你指导的学生。</p>
          <p v-if="!students.length && !busy">暂无学生。学生可在「老师与任务」里输入班级编号加入。</p>
          <label v-else>选择学生
            <select v-model="selected" :disabled="busy" @change="showSummary">
              <option value="">请选择学生</option>
              <option v-for="s in students" :key="s.id" :value="s.id">{{ s.displayName }}（{{ s.loginName }}）</option>
            </select>
          </label>
          <div v-if="summary" class="summary-grid" aria-label="学生学习概况">
            <p>近七天学习时长<strong>{{ Math.round(summary.statistics.studyMinutes) }} 分钟</strong></p>
            <p>近七天练习次数<strong>{{ summary.statistics.attemptCount }}</strong></p>
            <p>近七天正确率<strong>{{ summary.statistics.correctRate === null ? '暂无数据' : (summary.statistics.correctRate * 100).toFixed(1) + '%' }}</strong></p>
            <p>个人计划完成<strong>{{ summary.completedPlanTaskCount }} / {{ summary.planTaskCount }}</strong></p>
            <p>已完成模考成绩<strong>{{ summary.examScores.map(s => s.totalScore ?? '未评分').join('、') || '暂无成绩' }}</strong></p>
          </div>
        </section>
        <section v-if="has('teacher:task:create')" class="teaching-card">
          <h2>布置教学任务</h2>
          <form @submit.prevent="publish">
            <TeachingRecipients v-model="recipients" :classes="classes" :disabled="busy" />
            <p v-if="selected && !recipients.length" class="muted">个人发布对象：{{ names.get(selected) }}</p>
            <label>任务标题<input v-model="title" required maxlength="100" :disabled="busy" /></label>
            <label>任务说明<textarea v-model="description" maxlength="2000" rows="3" :disabled="busy"></textarea></label>
            <label>截止时间<input v-model="deadline" type="datetime-local" required :disabled="busy" /></label>
            <label v-if="has('teacher:task:grade')" class="checkbox-label"><input v-model="requiresSubmission" type="checkbox" :disabled="busy" /> 要求提交作业并评分</label>
            <button type="submit" class="btn primary" :disabled="busy || (!recipients.length && !selected)">发布任务</button>
          </form>
        </section>
      </template>
      <section v-else class="teaching-card">
        <h2>我的指导老师</h2>
        <p class="muted">授权后，老师可查看你的学习时长、练习正确率、计划完成情况和模考成绩摘要，并为你布置任务。私人对话和附件不会共享；你可以随时撤销授权。</p>
        <p v-if="!teachers.length && !busy">暂无可选择的老师。</p>
        <div v-for="t in teachers" :key="t.id" class="teacher-row">
          <span>{{ t.displayName }} · {{ t.connected ? '已授权指导' : '未授权' }}</span>
          <button class="btn" :disabled="busy" @click="changeConnection(t)">{{ t.connected ? '撤销授权' : '授权指导' }}</button>
        </div>
      </section>
      <section class="teaching-card">
        <div class="tasks-heading"><h2>{{ viewedClass ? viewedClass.name + '的任务' : teacher ? '我布置的任务' : '老师布置的任务' }}</h2><button class="btn" :disabled="busy" @click="action(async () => {})">刷新</button></div>
        <label v-if="teacher && has('teacher:task:grade')" class="checkbox-label"><input v-model="pendingReviewsOnly" type="checkbox" /> 只看待批改（{{ pendingReviews.length }}）</label>
        <p v-if="!visibleTasks.length && !busy">{{ pendingReviewsOnly ? '暂无待批改作业。' : '暂无教学任务。' }}</p>
        <p v-if="linkedTask && !busy && !tasks.some(task => task.id === linkedTask)" class="muted">通知对应的任务当前不可查看。</p>
        <template v-if="teacher">
          <details v-for="[publicationId, publication] in publications" :key="publicationId" class="publication">
            <summary><strong>{{ publication.task.title }}</strong><span class="muted">截止 {{ formatTime(publication.task.deadline) }}</span></summary>
            <p v-if="publication.task.description">{{ publication.task.description }}</p>
            <details v-for="[classId, assignments] in publication.classes" :key="classId" class="publication-class">
              <summary>{{ classId ? classNames.get(classId) || '班级任务' : '个人分配' }}</summary>
              <details v-for="task in assignments" :key="task.id" class="publication-student">
                <summary>{{ names.get(task.studentId) || '指导学生' }}</summary>
                <TeachingTaskCard :task="task" teacher @updated="updateTask" />
              </details>
            </details>
          </details>
        </template>
        <TeachingTaskCard v-for="task in teacher ? [] : visibleTasks" :key="task.id" :task="task" :teacher="false"
          :person="names.get(task.teacherId)" :class-name="task.classId ? classNames.get(task.classId) : undefined"
          :linked="task.id === linkedTask" @updated="updateTask" />
      </section>
    </template>
  </div>
</template>

<style scoped>
.teaching-workspace { display: grid; gap: 20px; }
.teaching-card { padding: 24px; border: 1px solid #e3e9e6; border-radius: 18px; background: white; }
.teaching-card h2 { margin: 0 0 12px; }
.class-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.class-toolbar h2 { margin: 0 8px 0 0; }
.class-choice[aria-pressed='true'] { color: #126f5c; border-color: #126f5c; background: #e8f4ef; }
.class-details { margin-top: 8px; }
.class-dialog { box-sizing: border-box; width: min(440px, calc(100% - 32px)); padding: 24px; border: 1px solid #d3dfd8; border-radius: 16px; color: inherit; }
.class-dialog::backdrop { background: rgb(16 37 30 / 42%); }
.class-dialog-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; margin-top: 18px; }

.teaching-card label { display: grid; gap: 8px; margin: 14px 0; }
.teaching-card input, .teaching-card select, .teaching-card textarea { width: 100%; padding: 10px 12px; border: 1px solid #d3dfd8; border-radius: 9px; font: inherit; }
.teaching-card .checkbox-label { display: flex; align-items: center; }
.checkbox-label input { width: auto; accent-color: #126f5c; }

.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
.summary-grid p { padding: 14px; border-radius: 12px; background: #f1f7f3; }
.summary-grid strong { display: block; margin-top: 8px; }
.teacher-row, .tasks-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.teacher-row { padding: 14px 0; border-bottom: 1px solid #edf1ee; }
.publication { padding: 12px 0; border-bottom: 1px solid #edf1ee; overflow-wrap: anywhere; }
.publication summary { cursor: pointer; padding: 8px 0; }
.publication > summary span { display: inline-block; margin-left: 12px; font-size: .9rem; }
.publication-class { margin: 4px 0 4px 12px; padding-left: 12px; border-left: 2px solid #e3e9e6; }
.publication-student { margin-left: 12px; }
@media (max-width: 600px) { .teaching-card { padding: 16px; } .teacher-row { align-items: flex-start; flex-direction: column; } }
</style>
