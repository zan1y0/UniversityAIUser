import { execute } from './contract';
import type { TeachingBatchInput, TeachingClassTaskInput, TeachingTaskInput } from './generated';

export const teaching = {
  students: () => execute('op087', {}),
  summary: (id: string) => execute('op088', { path: { id } }),
  teacherTasks: () => execute('op089', {}),
  create: (body: TeachingTaskInput) => execute('op090', { body }),
  cancel: (id: string, version: number) => execute('op091', { path: { id }, body: { version } }),
  teachers: () => execute('op092', {}),
  connect: (id: string) => execute('op093', { path: { id } }),
  disconnect: (id: string) => execute('op094', { path: { id } }),
  studentTasks: () => execute('op095', {}),
  complete: (id: string, version: number) => execute('op096', { path: { id }, body: { version } }),
  teacherClasses: () => execute('op101', {}),
  createClass: (name: string, code: string) => execute('op102', { body: { name, code } }),
  studentClasses: () => execute('op103', {}),
  joinClass: (code: string) => execute('op104', { body: { code } }),
  submit: (id: string, version: number, content: string) =>
    execute('op105', { path: { id }, body: { version, content } }),
  grade: (id: string, version: number, score: number, feedback: string) =>
    execute('op106', { path: { id }, body: { version, score, feedback } }),
  leaveClass: (id: string) => execute('op107', { path: { id } }),
  publishToClass: (id: string, body: TeachingClassTaskInput) =>
    execute('op108', { path: { id }, body }),
  roster: (id: string) => execute('op109', { path: { id } }),
  publish: (body: TeachingBatchInput) => execute('op110', { body }),
  submitAttachment: (id: string, version: number, content: string, file: File) => {
    const body = new FormData();
    body.set('version', String(version));
    body.set('content', content);
    body.set('file', file);
    return execute('op111', { path: { id }, body });
  },
  downloadAttachment: (id: string, teacher: boolean) =>
    teacher ? execute('op113', { path: { id } }) : execute('op112', { path: { id } }),
};
