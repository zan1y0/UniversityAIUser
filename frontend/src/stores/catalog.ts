import { defineStore } from 'pinia';
import { ref } from 'vue';
import { isRecord } from '../api/client';
import type { Profile } from '../api/generated';
import { enumLabel } from '../domain/contract-ui';
export interface ResourceOption {
  value: string;
  text: string;
  record: Record<string, unknown>;
}
export const useCatalogStore = defineStore('contract-catalog', () => {
  const resources = ref<Record<string, ResourceOption[]>>({});
  const profile = ref<Profile | null>(null);
  const versions = ref<Record<string, number>>({});
  const progressVersions = ref<Record<string, number>>({});
  const answerVersions = ref<Record<string, number>>({});
  function add(kind: string, value: unknown, text: string, record: Record<string, unknown> = {}) {
    if (typeof value !== 'string' || !value) return;
    const list = (resources.value[kind] ||= []);
    const index = list.findIndex(
      (v) =>
        v.value === value && (kind !== 'optionId' || v.record.questionId === record.questionId),
    );
    const entry = { value, text: text || '已加载记录', record };
    if (index >= 0) list[index] = entry;
    else list.push(entry);
    if (typeof record.version === 'number') versions.value[value] = record.version;
    if (typeof record.contextVersion === 'number') versions.value[value] = record.contextVersion;
  }
  function collect(value: unknown, hint = '', parent?: Record<string, unknown>) {
    if (Array.isArray(value)) {
      value.forEach((v) => collect(v, hint, parent));
      return;
    }
    if (!isRecord(value)) return;
    const title = String(
      value.schoolName
        ? `${value.schoolName} · ${value.programName || value.major || ''}`
        : value.title ||
            value.name ||
            value.programName ||
            value.stem ||
            value.content ||
            value.text ||
            value.inputText ||
            value.scheduledDate ||
            '已加载记录',
    ).slice(0, 80);
    if ('user' in value && 'dailyMinutes' in value) profile.value = value as unknown as Profile;
    let kind = '';
    if ('schoolName' in value) kind = 'programId';
    else if ('discipline' in value) kind = 'subjectId';
    else if ('stem' in value) kind = 'questionId';
    else if ('depth' in value) kind = 'knowledgeId';
    else if ('role' in value) kind = 'messageId';
    else if ('contextVersion' in value) kind = 'conversationId';
    else if ('tasks' in value && 'targetDate' in value) kind = 'planId';
    else if ('scheduledDate' in value && 'minutes' in value) kind = 'taskId';
    else if ('firstQuestion' in value) kind = 'interviewId';
    else if ('attemptCount' in value && 'question' in value) kind = 'wrongItemId';
    else if ('deadlineAt' in value) kind = 'examSessionId';
    else if ('questions' in value) kind = 'practiceSessionId';
    else if ('questionCount' in value) kind = 'paperId';
    else if ('probability' in value) kind = 'evaluationId';
    else if ('confidence' in value && 'fileId' in value) kind = 'recognitionId';
    else if ('mimeType' in value) kind = 'fileId';
    else if ('category' in value && 'publishedAt' in value) kind = 'noticeId';
    else if ('read' in value) kind = 'notificationId';
    if (kind) add(kind, value.id, title, value);
    if (typeof value.subjectId === 'string' && ('nodes' in value || kind === 'subjectId'))
      add('subjectId', value.subjectId, hint || title, value);
    if (typeof value.shapeId === 'string' && ('previewUrl' in value || 'name' in value))
      add('shapeId', value.shapeId, title, value);
    if (kind === 'questionId') add('questionVersionId', value.versionId, title, value);
    if (kind === 'paperId') add('paperVersionId', value.versionId, title, value);
    if (typeof value.scoreSnapshotId === 'string')
      add('scoreSnapshotIds', value.scoreSnapshotId, `成绩 ${value.totalScore ?? ''}`, value);
    if (
      typeof value.questionId === 'string' &&
      typeof value.version === 'number' &&
      'savedAt' in value
    )
      answerVersions.value[value.questionId] = value.version;
    if (typeof value.planId === 'string' && typeof value.version === 'number')
      progressVersions.value[value.planId] = value.version;
    if (kind === 'planId')
      add('planId', value.id, `计划 · ${enumLabel(value.status)} · ${value.targetDate}`, value);
    for (const [key, child] of Object.entries(value)) {
      if (key === 'voices' && Array.isArray(child))
        child.forEach((v) => add('voice', v, String(v)));
      else if (key === 'bookIds' && Array.isArray(child))
        child.forEach((v, i) => add('bookId', v, `${title} · 参考书 ${i + 1}`));
      else if (key === 'options' && Array.isArray(child))
        child.forEach((v) => {
          if (isRecord(v)) add('optionId', v.id, String(v.text), { ...v, questionId: value.id });
        });
      else if (typeof child === 'object' && child !== null) collect(child, hint, value);
    }
  }
  function options(name: string, selectedQuestion?: string) {
    const key = name === 'programIds' ? 'programId' : name === 'optionIds' ? 'optionId' : name;
    const list = resources.value[key] || [];
    return key === 'optionId' && selectedQuestion
      ? list.filter((v) => v.record.questionId === selectedQuestion)
      : list;
  }
  function reset() {
    resources.value = {};
    versions.value = {};
    progressVersions.value = {};
    answerVersions.value = {};
    profile.value = null;
  }
  return {
    resources,
    profile,
    versions,
    progressVersions,
    answerVersions,
    add,
    collect,
    options,
    reset,
  };
});
