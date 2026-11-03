<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { execute, waitJob, subscribeEvents, type OperationId, type Input } from '../api/contract';
import { operations, displaySchema, type Schema, assertSchema } from '../api/schema';
import { ApiError, isRecord } from '../api/client';
import { useAppStore } from '../stores/app';
import { useCatalogStore } from '../stores/catalog';
import { defaultValue, label, technicalFields, today } from '../domain/contract-ui';
import ContractField from './ContractField.vue';
import ContractResult from './ContractResult.vue';
import AvatarGuide from './AvatarGuide.vue';
import ChartPanel from './ChartPanel.vue';
import { useSpeech } from '../composables/useSpeech';
import { sessionValue, saveSessionValue, removeSessionValue } from '../domain/session-storage';

const route = useRoute();
const app = useAppStore();
const catalog = useCatalogStore();
const moduleMap: Record<string, string> = {
  '/companion': 'M01',
  '/schools': 'M02',
  '/public-courses': 'M03',
  '/professional-courses': 'M04',
  '/qa': 'M05',
  '/practice': 'M06',
  '/planning': 'M07',
  '/exams': 'M08',
  '/interview': 'M09',
  '/dashboard': 'M10',
  '/news': 'M11',
};
const moduleId = computed(() => moduleMap[route.path]);
const actionIds = computed(() =>
  Object.entries(operations)
    .filter(([, o]) => o.tag.startsWith(moduleId.value || '__'))
    .map(([id]) => id as OperationId),
);
const extras = computed(() =>
  moduleId.value === 'M07'
    ? (['op004', 'op005'] as OperationId[])
    : ['M01', 'M05', 'M09'].includes(moduleId.value || '')
      ? (['op006', 'op007', 'op008'] as OperationId[])
      : [],
);
const active = ref<OperationId>('op072');
const form = ref<Input>({});
const op = computed(() => operations[active.value]!);
const response = ref<unknown>();
const results = ref<Record<string, unknown>>({});
const loading = ref(false);
const initializing = ref(true);
const error = ref('');
const initError = ref('');
const statusText = ref('');
const progress = ref(0);
const transient = ref('');
const info = ref('');
const uploadFile = ref<File>();
const uploadPurpose = ref('qaImage');
const uploadPreview = ref('');
const filters = ref({ subject: 'math', year: new Date().getFullYear() + 1, examType: 'math1' });
let controller: AbortController | undefined;
const lifetime = new AbortController();
let closeEvents: (() => void) | undefined;
let last: { id: OperationId; input: Input; key?: string } | undefined;
let pendingMessage: string | undefined;
let activeJobId: string | undefined;
let jobMessageVersion = 1;
let runSequence = 0;
let automaticSave: ReturnType<typeof setTimeout> | undefined;
let clockTimer: ReturnType<typeof setInterval> | undefined;
const now = ref(Date.now());
let clockOffset = 0;
const primary: Record<string, OperationId> = {
  M01: 'op012',
  M02: 'op019',
  M03: 'op025',
  M04: 'op031',
  M05: 'op037',
  M06: 'op046',
  M07: 'op004',
  M08: 'op057',
  M09: 'op065',
  M10: 'op072',
  M11: 'op075',
};
const titleOverrides: Record<string, string> = {
  op013: '选择形象与陪伴模式',
  op014: '和研小途聊一聊',
  op015: '记录今日打卡',
  op018: '设置每日督学提醒',
  op020: '寻找适合我的院校',
  op022: '对比目标院校',
  op023: '分析上岸概率与风险',
  op025: '查看公共课知识体系',
  op026: '听一段知识点讲解',
  op028: '英语专项辅导',
  op029: '政治专项辅导',
  op030: '数学专项辅导',
  op037: '开始新一轮答疑',
  op038: '识别题目图片',
  op039: '继续提问',
  op043: '开始练习',
  op045: '提交本题作答',
  op050: '生成候选学习计划',
  op053: '确认计划并开始执行',
  op054: '根据进度重新调整',
  op058: '开始限时模考',
  op060: '保存本题答案',
  op061: '交卷并查看成绩',
  op065: '开始复试模拟',
  op066: '提交回答并继续对练',
  op067: '结束练习并生成总结',
  op069: '完善我的自我介绍',
  op071: '记录一次学习',
  op078: '设置院校与政策关注',
};
const buttonTitle = (id: OperationId) => titleOverrides[id] || operations[id]!.title;
const selectedQuestion = computed(() => {
  const body = isRecord(form.value.body) ? form.value.body : {};
  const version =
    typeof body.questionVersionId === 'string'
      ? catalog.options('questionVersionId').find((v) => v.value === body.questionVersionId)?.record
          .id
      : undefined;
  return typeof form.value.path?.qid === 'string'
    ? form.value.path.qid
    : typeof version === 'string'
      ? version
      : undefined;
});
const latestExam = computed(
  () =>
    catalog.options('examSessionId').find((v) => v.value === form.value.path?.id) ||
    catalog.options('examSessionId').at(-1),
);
const remaining = computed(() =>
  latestExam.value
    ? Math.max(
        0,
        Math.ceil(
          (Date.parse(String(latestExam.value.record.deadlineAt)) - (now.value + clockOffset)) /
            1000,
        ),
      )
    : null,
);
const closedExam = computed(
  () =>
    !!latestExam.value && (latestExam.value.record.status === 'submitted' || remaining.value === 0),
);
const answerActions = computed(() => ['op060', 'op061'].includes(active.value));
const curveOption = computed(() => {
  if (!isRecord(response.value) || !Array.isArray(response.value.curve)) return null;
  const curve = response.value.curve.filter(isRecord);
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 45, right: 15, bottom: 35, top: 25 },
    xAxis: { type: 'category', data: curve.map((v) => v.date) },
    yAxis: { type: 'value', name: '分钟' },
    series: [
      {
        type: 'line',
        data: curve.map((v) => v.studyMinutes),
        smooth: true,
        areaStyle: { color: '#e1f0e7' },
        color: '#258d76',
      },
    ],
  };
});
const radarOption = computed(() => {
  if (active.value !== 'op073' || !isRecord(response.value) || !Array.isArray(response.value.items))
    return null;
  const items = response.value.items
    .filter(isRecord)
    .filter((v) => v.mastery !== null)
    .slice(0, 12);
  if (items.length < 3) return null;
  return {
    radar: { indicator: items.map((v) => ({ name: v.name, max: 100 })), radius: '65%' },
    series: [
      {
        type: 'radar',
        data: [{ value: items.map((v) => Number(v.mastery) * 100), name: '掌握程度' }],
        areaStyle: { opacity: 0.18 },
        color: '#258d76',
      },
    ],
  };
});
function referenceName(id: OperationId, name: string) {
  if (name === 'qid') return 'questionId';
  if (name !== 'id') return name;
  const path = operations[id]!.path;
  const pairs = [
    ['/qa/conversations/', 'conversationId'],
    ['/qa/messages/', 'messageId'],
    ['/schools/programs/', 'programId'],
    ['/schools/evaluations/', 'evaluationId'],
    ['/practice/wrong-items/', 'wrongItemId'],
    ['/practice/sessions/', 'practiceSessionId'],
    ['/mock-exams/sessions/', 'examSessionId'],
    ['/mock-exams/papers/', 'paperId'],
    ['/plans/', 'planId'],
    ['/interviews/', 'interviewId'],
    ['/files/', 'fileId'],
    ['/news/notices/', 'noticeId'],
    ['/news/notifications/', 'notificationId'],
  ];
  return pairs.find(([prefix]) => path.includes(prefix!))?.[1] || name;
}
function parameters(where: string) {
  return op.value.parameters.filter((p) => p.in === where);
}
function selectDefault(schema: Schema, name: string): unknown {
  if (name === 'consent') return false;
  const definition = displaySchema(schema);
  if (schema.anyOf?.some((v) => v.type === 'null')) return null;
  if (definition.oneOf) return selectDefault(definition.oneOf[0]!, name);
  if (definition.type === 'object')
    return Object.fromEntries(
      Object.entries(definition.properties || {})
        .filter(([k]) => definition.required?.includes(k))
        .map(([k, s]) => [k, selectDefault(s, k)]),
    );
  if (definition.format === 'uuid' && name !== 'optionId')
    return catalog.options(name).at(-1)?.value || '';
  if (name === 'subject') return filters.value.subject;
  if (name === 'year' || name === 'targetYear') return filters.value.year;
  if (name === 'examType') return filters.value.examType;
  return defaultValue(definition, name);
}
function choose(id: OperationId, force = false) {
  if (loading.value && !force) return;
  active.value = id;
  response.value = results.value[id];
  error.value = '';
  statusText.value = '';
  info.value = '';
  transient.value = '';
  progress.value = 0;
  const next: Input = { path: {}, query: {} };
  for (const p of operations[id]!.parameters) {
    if (!['path', 'query'].includes(p.in)) continue;
    if (!p.required) continue;
    const field = referenceName(id, p.name);
    const val = selectDefault(p.schema, field);
    if (p.in === 'path') next.path![p.name] = val;
    else next.query![p.name] = val;
  }
  if (operations[id]!.body && !operations[id]!.multipart)
    next.body = selectDefault(operations[id]!.body!, 'body');
  const profile = catalog.profile;
  if (id === 'op005' && profile)
    next.body = {
      undergraduateMajor: profile.undergraduateMajor,
      targetProgramId: profile.targetProgramId,
      targetDate: profile.targetDate,
      dailyMinutes: profile.dailyMinutes,
      timezone: profile.timezone,
      baseline: profile.baseline,
      version: profile.version,
    };
  if (['op013', 'op018', 'op078'].includes(id)) {
    const previous = results.value[id === 'op013' ? 'op012' : id === 'op018' ? 'op017' : 'op077'];
    if (isRecord(previous)) next.body = { ...previous };
  }
  if (id === 'op050' && profile && isRecord(next.body)) {
    next.body.dailyMinutes = profile.dailyMinutes;
    next.body.baseline = profile.baseline;
    if (profile.targetDate) next.body.targetDate = profile.targetDate;
  }
  if (id === 'op072' && next.query) next.query.timezone = 'Asia/Shanghai';
  form.value = next;
}
function updateParameter(where: 'query' | 'path', name: string, value: unknown) {
  form.value = { ...form.value, [where]: { ...form.value[where], [name]: value } };
}
function mergeTechnical(
  schema: Schema,
  value: unknown,
  key: string,
  path: Record<string, unknown>,
): unknown {
  const s = displaySchema(schema);
  if (s.oneOf) {
    const branch =
      s.oneOf.find(
        (v) =>
          isRecord(value) &&
          (!displaySchema(v).properties?.kind?.const ||
            displaySchema(v).properties?.kind?.const === value.kind) &&
          Object.keys(displaySchema(v).properties || {}).every(
            (k) => !(displaySchema(v).required || []).includes(k) || k in value,
          ),
      ) || s.oneOf[0]!;
    return mergeTechnical(branch, value, key, path);
  }
  if (s.type === 'object') {
    const next = isRecord(value) ? { ...value } : {};
    for (const [field, definition] of Object.entries(s.properties || {})) {
      if (technicalFields.has(field)) {
        if (['requestId', 'eventId'].includes(field)) next[field] = key;
        else if (field === 'contextId') next[field] = interactionContext;
        else if (field === 'timezone') next[field] = 'Asia/Shanghai';
        else if (field === 'profileVersion') next[field] = catalog.profile?.version || 1;
        else if (field === 'progressVersion')
          next[field] = catalog.progressVersions[String(path.id)] || 1;
        else if (field === 'version' && active.value === 'op060')
          next[field] = catalog.answerVersions[String(path.qid)] || 0;
        else
          next[field] =
            catalog.versions[String(path.taskId || path.id)] ||
            Number(next[field]) ||
            catalog.profile?.version ||
            1;
      } else if (field in next) next[field] = mergeTechnical(definition, next[field], key, path);
    }
    return next;
  }
  if (s.type === 'array' && s.items && Array.isArray(value))
    return value.map((v) => mergeTechnical(s.items!, v, key, path));
  return value;
}
const interactionContext = crypto.randomUUID();
async function run(retry = false) {
  if (loading.value) return;
  if (active.value === 'op060' && closedExam.value) {
    error.value = '考试已结束，不能继续修改答案。';
    return;
  }
  const sequence = ++runSequence;
  controller = new AbortController();
  loading.value = true;
  error.value = '';
  statusText.value = op.value.status === 202 ? '正在提交学习任务…' : '正在处理…';
  transient.value = '';
  try {
    let input: Input;
    let key: string | undefined;
    let id = active.value;
    if (retry && last) {
      id = last.id;
      input = last.input;
      key = last.key;
    } else {
      key = op.value.idempotent ? crypto.randomUUID() : undefined;
      input = JSON.parse(JSON.stringify(form.value)) as Input;
      if (op.value.multipart) {
        if (!uploadFile.value)
          throw new ApiError('请选择需要上传的图片或录音', 'VALIDATION_FAILED');
        if ((uploadPurpose.value === 'qaImage') !== uploadFile.value.type.startsWith('image/'))
          throw new ApiError('附件用途和文件类型不匹配，请重新选择。', 'VALIDATION_FAILED');
        const data = new FormData();
        data.append('file', uploadFile.value);
        data.append('purpose', uploadPurpose.value);
        input.body = data;
      } else if (op.value.body)
        input.body = mergeTechnical(
          op.value.body,
          input.body,
          key || crypto.randomUUID(),
          input.path || {},
        );
      if (id === 'op061' && typeof input.path?.id === 'string') {
        const latest = await execute(
          'op059',
          { path: { id: input.path.id } },
          { signal: controller.signal },
        );
        catalog.collect(latest);
        if (isRecord(input.body)) input.body.version = latest.version;
      }
      last = { id, input, key };
    }
    const data: unknown = await execute(id, input, { key, signal: controller.signal });
    if (sequence !== runSequence) return;
    if (id === 'op061' && typeof input.path?.id === 'string') {
      // 202交卷凭据已经确认服务端完成状态转换，立即使本地答卷只读。
      const session = catalog
        .options('examSessionId')
        .find((item) => item.value === input.path!.id);
      if (session)
        catalog.add('examSessionId', session.value, session.text, {
          ...session.record,
          status: 'submitted',
        });
    }
    response.value = data;
    results.value[id] = data;
    catalog.collect(
      data,
      typeof input.query?.subject === 'string' ? label(input.query.subject) : '',
    );
    if (data instanceof Blob) {
      const url = URL.createObjectURL(data);
      const link = document.createElement('a');
      link.href = url;
      link.download = '学习附件';
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      response.value = '附件已下载。';
    }
    if (isRecord(data) && 'deadlineAt' in data) {
      clockOffset = Date.parse(String(data.serverTime)) - Date.now();
      if (app.user) saveSessionValue(`kaoyan-exam:${app.user.id}`, String(data.id));
    }
    if (id === 'op060' && typeof input.path?.id === 'string') {
      const latest = await execute(
        'op059',
        { path: { id: input.path.id } },
        { signal: controller.signal },
      );
      catalog.collect(latest);
    }
    if (isRecord(data) && typeof data.jobId === 'string') {
      activeJobId = data.jobId;
      if (
        id === 'op039' &&
        typeof data.contextVersion === 'number' &&
        typeof input.path?.id === 'string'
      )
        catalog.versions[input.path.id] = data.contextVersion;
      pendingMessage = typeof data.messageId === 'string' ? data.messageId : undefined;
      if (pendingMessage)
        catalog.add('messageId', pendingMessage, '正在生成的答疑消息', { version: 1 });
      statusText.value = '任务已接受，正在生成…';
      const result = await waitJob(data.jobId, controller.signal, (job) => {
        progress.value = job.progress;
        statusText.value =
          job.status === 'queued' ? '正在排队，请稍候…' : `正在生成 · ${job.progress}%`;
      });
      if (sequence !== runSequence) return;
      response.value = result;
      results.value[id] = result;
      catalog.collect(result);
      pendingMessage = undefined;
      activeJobId = undefined;
      transient.value = '';
      if (isRecord(result) && result.kind === 'qa' && isRecord(result.message))
        jobMessageVersion = Number(result.message.version) || 1;
    }
    if (id === 'op005') await refreshProfile();
    if (
      id === 'op039' &&
      isRecord(data) &&
      typeof data.contextVersion === 'number' &&
      typeof input.path?.id === 'string'
    )
      catalog.versions[input.path.id] = data.contextVersion;
    statusText.value = '已完成';
    app.notify('学习操作已完成');
    if (id === 'op058') {
      choose('op060', true);
      response.value = data;
      info.value = '选择题目并作答，答案会自动保存；交卷后不可修改。';
    }
    if (id === 'op037') {
      choose('op039', true);
      info.value = '会话已创建，可以连续提问。';
    }
    if (id === 'op065') {
      choose('op066', true);
      response.value = data;
      info.value = '请回答面试问题，完成后选择结束练习。';
    }
  } catch (e) {
    if (sequence !== runSequence) return;
    error.value = e instanceof Error ? e.message : '操作未完成';
    statusText.value = e instanceof ApiError && e.code === 'CANCELLED' ? '已停止' : '未完成';
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT')
      error.value += ' 请重新查询最新记录后再操作，系统不会覆盖已更新内容。';
    if (e instanceof ApiError && e.code === 'UNAUTHENTICATED')
      error.value = '登录已失效，请重新登录。';
  } finally {
    if (sequence === runSequence) loading.value = false;
  }
}
async function cancel() {
  activeJobId = undefined;
  controller?.abort();
  speech.stop();
  transient.value = '';
  if (pendingMessage) {
    const id = pendingMessage;
    pendingMessage = undefined;
    try {
      const latest = await execute('op041', { path: { id } });
      jobMessageVersion = latest.version;
      const cancelled = await execute('op042', {
        path: { id },
        body: { version: jobMessageVersion },
      });
      catalog.collect(cancelled);
      response.value = cancelled;
    } catch (e) {
      error.value = `本地已停止等待，但未能确认服务端取消：${e instanceof Error ? e.message : '请稍后查询消息状态'}`;
    }
  }
}
const speech = useSpeech((text) => {
  const body = isRecord(form.value.body) ? form.value.body : {};
  const field = 'text' in body ? 'text' : 'content' in body ? 'content' : null;
  if (field) form.value.body = { ...body, [field]: `${body[field] || ''}${text}` };
});
function fileChosen(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  error.value = '';
  if (!file) return;
  const audio = ['audio/wav', 'audio/mpeg', 'audio/webm', 'audio/ogg'];
  const image = ['image/jpeg', 'image/png', 'image/webp'];
  if (file.size > 10 * 1024 * 1024 || ![...audio, ...image].includes(file.type)) {
    error.value = '请选择不超过10MB的JPG、PNG、WebP图片或WAV、MP3、WebM、Ogg录音。';
    input.value = '';
    return;
  }
  uploadFile.value = file;
  uploadPurpose.value = image.includes(file.type)
    ? 'qaImage'
    : moduleId.value === 'M01'
      ? 'avatarAudio'
      : 'interviewAudio';
  if (uploadPreview.value) URL.revokeObjectURL(uploadPreview.value);
  uploadPreview.value = image.includes(file.type) ? URL.createObjectURL(file) : '';
}
async function refreshProfile() {
  const profile = await execute('op004', {});
  catalog.collect(profile);
  results.value.op004 = profile;
  return profile;
}
async function initialize() {
  initializing.value = true;
  initError.value = '';
  try {
    if (!catalog.profile) await refreshProfile();
    if (!catalog.options('programId').length) {
      try {
        catalog.collect(
          await execute(
            'op019',
            { query: { year: filters.value.year } },
            { signal: lifetime.signal },
          ),
        );
      } catch {
        /* 关联列表失败不阻断无关模块；依赖选择的操作显示空选择。 */
      }
    }
    if (!catalog.options('subjectId').length) {
      try {
        catalog.collect(
          await execute(
            'op025',
            { query: { subject: 'math', year: filters.value.year, examType: 'math1' } },
            { signal: lifetime.signal },
          ),
          '数学',
        );
      } catch {}
    }
    if (moduleId.value === 'M01')
      for (const id of ['op011', 'op012', 'op017'] as const) {
        try {
          const data = await execute(id, {}, { signal: lifetime.signal });
          catalog.collect(data);
          results.value[id] = data;
        } catch {}
      }
    if (moduleId.value === 'M11') {
      try {
        const data = await execute('op077', {}, { signal: lifetime.signal });
        catalog.collect(data);
        results.value.op077 = data;
      } catch {}
    }
    const initial = primary[moduleId.value || ''];
    if (initial) choose(initial);
    if (initial && operations[initial]!.method === 'GET') await run();
    if (moduleId.value === 'M08' && app.user) {
      const stored = sessionValue(`kaoyan-exam:${app.user.id}`);
      if (stored) {
        try {
          const session = await execute('op059', { path: { id: stored } });
          catalog.collect(session);
          clockOffset = Date.parse(session.serverTime) - Date.now();
          choose(session.status === 'in_progress' ? 'op060' : 'op062');
          response.value = session;
          if (session.status === 'submitted') await run();
        } catch {
          removeSessionValue(`kaoyan-exam:${app.user.id}`);
        }
      }
    }
    if (moduleId.value === 'M07' && catalog.profile?.activePlanId) {
      try {
        const data = await execute('op051', {
          path: { id: catalog.profile.activePlanId },
          query: { view: 'week', date: today() },
        });
        catalog.collect(data);
      } catch {}
    }
  } catch (e) {
    initError.value = e instanceof Error ? e.message : '资料加载未完成';
  } finally {
    initializing.value = false;
  }
}
watch(
  () => form.value.body,
  () => {
    clearTimeout(automaticSave);
    if (active.value !== 'op060' || loading.value || closedExam.value) return;
    automaticSave = setTimeout(() => {
      try {
        const input = isRecord(form.value.body)
          ? mergeTechnical(
              op.value.body!,
              form.value.body,
              crypto.randomUUID(),
              form.value.path || {},
            )
          : null;
        assertSchema(op.value.body!, input);
        void run();
      } catch {
        /* 尚未完整作答，不发送半成品。 */
      }
    }, 800);
  },
  { deep: true },
);
watch(remaining, (seconds) => {
  if (seconds !== 0 || latestExam.value?.record.status !== 'in_progress' || loading.value) return;
  choose('op061');
  void run();
});
onMounted(async () => {
  await initialize();
  if (lifetime.signal.aborted) return;
  closeEvents = subscribeEvents(
    (event) => {
      if (
        event.type === 'ai.delta' &&
        event.data.jobId === activeJobId &&
        loading.value &&
        (!pendingMessage || event.data.messageId === pendingMessage)
      )
        transient.value += event.data.text;
      else if (event.type === 'task.reminder') app.notify(event.data.message);
      else if (event.type === 'policy.updated') app.notify('关注的院校政策有更新，请查看通知。');
      else if (event.type === 'exam.scored') app.notify('模考评分已完成，可以查看成绩。');
    },
    () => {
      if (moduleId.value === 'M11' && !loading.value) {
        choose('op080');
        void run();
      }
    },
  );
  clockTimer = setInterval(() => (now.value = Date.now()), 500);
});
onBeforeUnmount(() => {
  runSequence++;
  controller?.abort();
  lifetime.abort();
  closeEvents?.();
  speech.stop();
  clearTimeout(automaticSave);
  clearInterval(clockTimer);
  if (uploadPreview.value) URL.revokeObjectURL(uploadPreview.value);
});
</script>
<template>
  <div class="contract-workspace">
    <p v-if="initializing" class="notice" role="status">正在载入你的学习资料…</p>
    <p v-if="initError" class="inline-error" role="alert">
      {{ initError }} <button class="text-btn" @click="initialize">重新加载</button>
    </p>
    <section class="card contract-nav" aria-label="本模块学习功能">
      <div class="chips">
        <button
          v-for="id in [...extras, ...actionIds]"
          :key="id"
          class="chip"
          :class="{ active: active === id }"
          :disabled="loading"
          @click="choose(id)"
        >
          {{ buttonTitle(id) }}
        </button>
      </div>
    </section>
    <div class="contract-columns section-gap">
      <section class="card">
        <div class="card-heading">
          <h2>{{ buttonTitle(active) }}</h2>
          <span class="tag">我的学习空间</span>
        </div>
        <AvatarGuide
          :action="
            isRecord(response) && typeof response.actionCode === 'string'
              ? response.actionCode
              : 'idle'
          "
          v-if="moduleId === 'M01'"
          :mood="
            isRecord(form.body) && form.body.mode === 'strict'
              ? '严管'
              : isRecord(form.body) && form.body.mode === 'scholar'
                ? '学霸'
                : '温柔'
          "
        />
        <p v-if="info" class="notice space-top">{{ info }}</p>
        <p v-if="moduleId === 'M05'" class="muted small space-top">
          先创建答疑会话。图片题需先上传、识别并核对题干；系统只提供思路、步骤和方法。
        </p>
        <p v-if="moduleId === 'M07'" class="muted small space-top">
          先设置备考档案，再生成候选计划；确认后生效，动态调整保留已完成记录。
        </p>
        <p v-if="moduleId === 'M08' && latestExam" class="notice space-top" role="timer">
          {{
            latestExam.record.status === 'submitted'
              ? '已交卷'
              : remaining === 0
                ? '考试时间已结束'
                : `剩余 ${Math.floor((remaining || 0) / 60)} 分 ${(remaining || 0) % 60} 秒`
          }}。截止时间以服务端为准。
        </p>
        <form class="contract-form space-top" @submit.prevent="run()">
          <template v-for="where in ['path', 'query'] as const" :key="where">
            <template v-for="parameter in parameters(where)" :key="parameter.name">
              <label
                v-if="!parameter.required && !technicalFields.has(parameter.name)"
                class="optional-toggle"
                ><input
                  type="checkbox"
                  :checked="parameter.name in (form[where] || {})"
                  @change="
                    (event) => {
                      if ((event.target as HTMLInputElement).checked)
                        updateParameter(
                          where,
                          parameter.name,
                          selectDefault(parameter.schema, parameter.name),
                        );
                      else {
                        const next = { ...form[where] };
                        delete next[parameter.name];
                        form[where] = next;
                      }
                    }
                  "
                />按{{ label(parameter.name) }}筛选</label
              >
              <ContractField
                v-if="parameter.required || parameter.name in (form[where] || {})"
                :name="where === 'path' ? referenceName(active, parameter.name) : parameter.name"
                :schema="parameter.schema"
                :model-value="form[where]?.[parameter.name]"
                :required="parameter.required"
                :question-id="selectedQuestion"
                @update:model-value="updateParameter(where, parameter.name, $event)"
              />
            </template>
          </template>
          <template v-if="op.multipart"
            ><label class="field"
              >题目图片或回答录音<input
                type="file"
                accept="image/jpeg,image/png,image/webp,audio/wav,audio/mpeg,audio/webm,audio/ogg"
                @change="fileChosen" /></label
            ><label class="field"
              >附件用途<select v-model="uploadPurpose">
                <option value="qaImage">答疑题目图片</option>
                <option value="avatarAudio">数字人语音</option>
                <option value="interviewAudio">复试回答录音</option>
              </select></label
            ><img
              v-if="uploadPreview"
              :src="uploadPreview"
              alt="待上传题目图片预览"
              class="contract-preview"
            />
            <p v-if="uploadFile" class="muted small">
              {{ uploadFile.name }} · {{ (uploadFile.size / 1024).toFixed(0) }} KB
            </p></template
          >
          <ContractField
            v-else-if="op.body"
            name="body"
            :schema="op.body"
            v-model="form.body"
            :question-id="selectedQuestion"
            required
          />
          <p
            v-if="active === 'op039' && isRecord(form.body) && 'recognitionId' in form.body"
            class="notice"
          >
            请核对识别后的题目文字，修正公式与选项，再填写“已核对的题目文字”发送。
          </p>
          <p v-if="active === 'op060'" class="muted small">
            完整作答后会自动保存，保存完成才可交卷；成绩由服务端批改。
          </p>
          <p v-if="active === 'op023'" class="muted small">
            缺少可核验资料时，概率显示“暂无可用数据”，不以虚构数值代替。
          </p>
          <p v-if="active === 'op065'" class="muted small">
            英语口语模式请同时选择英语；可使用文字回答，录音须由你主动授权。
          </p>
          <div class="form-actions">
            <button
              class="btn"
              :disabled="loading || initializing || (active === 'op060' && closedExam)"
            >
              {{ loading ? '处理中…' : buttonTitle(active) }}</button
            ><button v-if="loading" type="button" class="btn secondary" @click="cancel">
              停止等待</button
            ><button
              v-if="op.body && !op.multipart && !answerActions"
              type="button"
              class="text-btn"
              @click="
                speech.listen(
                  isRecord(form.body) && form.body.language === 'en-US' ? 'en-US' : 'zh-CN',
                )
              "
            >
              {{ speech.listening.value ? '停止语音输入' : '语音填写' }}</button
            ><button
              v-if="speech.listening.value"
              type="button"
              class="text-btn"
              @click="speech.stop"
            >
              停止录音
            </button>
          </div>
        </form>
        <p v-if="error || speech.error.value" class="inline-error space-top" role="alert">
          {{ error || speech.error.value }}
        </p>
        <button
          v-if="error && last && statusText !== '已停止'"
          class="btn secondary space-top"
          :disabled="loading"
          @click="run(true)"
        >
          重试上次操作
        </button>
        <p v-if="statusText" class="muted small space-top" role="status">{{ statusText }}</p>
        <progress
          v-if="loading && progress"
          :value="progress"
          max="100"
          class="stretch space-top"
        />
      </section>
      <section class="card contract-output" aria-label="学习内容与结果">
        <div class="card-heading">
          <h2>{{ moduleId === 'M10' ? '学习数据与反馈' : '学习内容与结果' }}</h2>
          <span v-if="loading" class="tag">正在处理</span>
        </div>
        <p v-if="transient" class="reading space-top" aria-live="polite">{{ transient }}</p>
        <ChartPanel
          v-if="curveOption"
          :option="curveOption"
          summary="学习时长进步曲线；具体分钟数见下方日期记录。"
        />
        <ChartPanel
          v-if="radarOption"
          :option="radarOption"
          summary="薄弱知识点掌握程度；零样本知识点不构造数值。"
        />
        <ContractResult v-if="response !== undefined" :value="response" />
        <p v-else-if="!loading" class="muted space-top">
          选择要进行的学习操作，完成后在这里查看题目、讲解、反馈或记录。服务暂不可用时会明确显示错误，不混入演示数据。
        </p>
        <p
          v-if="['M02', 'M03', 'M04', 'M11'].includes(moduleId || '')"
          class="muted small space-top"
        >
          院校指标、教材和政策以核验来源为准，生成内容需结合原始资料确认。
        </p>
      </section>
    </div>
  </div>
</template>
