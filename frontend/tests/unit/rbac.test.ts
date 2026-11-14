import { afterEach, expect, it, vi } from 'vitest';
import { roleHome, allowedRolePath } from '../../src/domain/auth';
import { backend } from '../../src/api/backend';
import { clearCsrf } from '../../src/api/client';

afterEach(() => { vi.unstubAllGlobals(); clearCsrf(); });
it('老师和学生分别进入自己的工作台', () => {
  const teacher = { id: 'teacher', nickname: '老师', activeRole: 'TEACHER' as const };
  const student = { id: 'student', nickname: '学生', activeRole: 'STUDENT' as const };
  expect(roleHome(teacher)).toBe('/teacher');
  expect(roleHome(student)).toBe('/dashboard');
  expect(allowedRolePath(teacher, '/dashboard')).toBe(false);
  expect(allowedRolePath(teacher, '/teacher')).toBe(true);
  expect(allowedRolePath(student, '/teacher')).toBe(false);
  expect(allowedRolePath(student, '/teaching')).toBe(true);
});
it('邮箱登录由服务端确认身份和权限', async () => {
  const ok = (data: unknown) => new Response(JSON.stringify({ code: 'OK', message: 'ok', traceId: 'test', data }));
  const fetch = vi.fn()
    .mockResolvedValueOnce(ok({ token: 'csrf', headerName: 'X-CSRF-Token' }))
    .mockResolvedValueOnce(ok({ id: '11111111-1111-4111-8111-111111111111', loginName: 'teacher', displayName: '老师', activeRole: 'TEACHER', roles: ['TEACHER'], permissions: ['teacher:student:list'] }));
  vi.stubGlobal('fetch', fetch);
  const user = await backend.login('teacher@example.test', 'Test2026!');
  expect(JSON.parse((fetch.mock.calls[1]![1] as RequestInit).body as string)).toEqual({ email: 'teacher@example.test', password: 'Test2026!' });
  expect(user.activeRole).toBe('TEACHER');
  expect(user.permissions).toEqual(['teacher:student:list']);
});
