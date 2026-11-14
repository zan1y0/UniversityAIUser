import { test, expect } from '@playwright/test';

test('老师登录后显示教学入口、发布任务且不显示学生功能', async ({ page }) => {
  const student = '11111111-1111-4111-8111-111111111111';
  const teacher = '22222222-2222-4222-8222-222222222222';
  let authenticated = false;
  let loginRole = '';
  const tasks: Record<string, unknown>[] = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request(); const path = new URL(request.url()).pathname;
    let data: unknown = {};
    if (path.endsWith('/session/csrf')) data = { token: 'test-csrf', headerName: 'X-CSRF-Token' };
    else if (path.endsWith('/auth/me') && !authenticated) {
      await route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED', message: '请登录', data: null, traceId: 'test' } }); return;
    } else if (path.endsWith('/auth/login') || path.endsWith('/auth/me')) {
      if (path.endsWith('/auth/login')) { loginRole = request.postDataJSON().email; authenticated = true; }
      data = { id: teacher, loginName: 'teacher', displayName: '指导老师', activeRole: 'TEACHER', roles: ['TEACHER'], permissions: ['teacher:student:list', 'teacher:analytics:read', 'teacher:task:read', 'teacher:task:create', 'teacher:task:cancel'] };
    } else if (path.endsWith('/teacher/students')) data = { items: [{ id: student, loginName: 'student', displayName: '学习同学' }] };
    else if (path.endsWith('/summary')) data = { studentId: student, statistics: { from: '2026-11-02', to: '2026-11-08', timezone: 'Asia/Shanghai', studyMinutes: 120, focus: null, correctRate: null, attemptCount: 0, curve: [], algorithmVersion: 'interval-union-self-report-v1' }, planTaskCount: 0, completedPlanTaskCount: 0, examScores: [] };
    else if (path.endsWith('/teacher/tasks')) {
      if (request.method() === 'POST') {
        const body = request.postDataJSON();
        tasks.push({ id: '33333333-3333-4333-8333-333333333333', teacherId: teacher, ...body, status: 'pending', version: 1, createdAt: new Date().toISOString(), completedAt: null });
        data = tasks[0];
      } else data = { items: tasks };
    }
    await route.fulfill({ status: request.method() === 'POST' && path.endsWith('/teacher/tasks') ? 201 : 200, json: { code: 'OK', message: '操作成功', data, traceId: 'test' } });
  });
  await page.goto('/login');
  await page.getByLabel('邮箱', { exact: true }).fill('teacher@example.test');
  await page.getByLabel('密码', { exact: true }).fill('Test2026!');
  await page.locator('form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/teacher$/);
  expect(loginRole).toBe('teacher@example.test');
  await expect(page.getByRole('heading', { name: '我的指导学生' })).toBeVisible();
  await expect(page.locator('nav').getByText('AI 答疑')).toHaveCount(0);
  await page.getByLabel('选择学生').selectOption(student);
  await expect(page.getByLabel('学生学习概况')).toBeVisible();
  await page.getByLabel('任务标题').fill('数学专项复盘');
  await page.getByLabel('任务说明').fill('完成练习后整理错题');
  const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 16);
  await page.getByLabel('截止时间', { exact: true }).fill(tomorrow);
  await page.getByRole('button', { name: '发布任务' }).click();
  await expect(page.getByText('数学专项复盘', { exact: true })).toBeVisible();
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/teacher$/);
});

test('学生可授权指导老师并完成教学任务，不能进入老师页面', async ({ page }) => {
  const student = '11111111-1111-4111-8111-111111111111';
  const teacher = '22222222-2222-4222-8222-222222222222';
  let connected = false;
  let completed = false;
  let completedVersion = 0;
  const task = () => ({ id: '33333333-3333-4333-8333-333333333333', teacherId: teacher, studentId: student,
    title: '英语专项练习', description: '整理阅读错题', deadline: new Date(Date.now() + 86400000).toISOString(),
    status: completed ? 'completed' : 'pending', version: completed ? 2 : 1,
    createdAt: new Date().toISOString(), completedAt: completed ? new Date().toISOString() : null });
  await page.route('**/api/v1/**', async route => {
    const req = route.request(); const path = new URL(req.url()).pathname;
    let data: unknown = {};
    if (path.endsWith('/session/csrf')) data = { token: 'csrf', headerName: 'X-CSRF-Token' };
    else if (path.endsWith('/auth/me')) data = { id: student, loginName: 'student', displayName: '学习同学', activeRole: 'STUDENT', roles: ['STUDENT'], permissions: ['student:study:use', 'student:teacher:manage', 'student:task:read', 'student:task:complete'] };
    else if (path.endsWith('/teaching/teachers')) data = { items: [{ id: teacher, displayName: '英语老师', connected }] };
    else if (path.endsWith('/teaching/teachers/' + teacher)) {
      connected = req.method() === 'POST'; data = { teacherId: teacher, connected };
    } else if (path.endsWith('/teaching/tasks')) data = { items: connected ? [task()] : [] };
    else if (path.endsWith('/complete')) {
      completedVersion = req.postDataJSON().version; completed = true; data = task();
    }
    await route.fulfill({ status: 200, json: { code: 'OK', message: '操作成功', data, traceId: 'test' } });
  });
  await page.goto('/teaching');
  await expect(page.getByRole('heading', { name: '我的指导老师' })).toBeVisible();
  await page.getByRole('button', { name: '授权指导', exact: true }).click();
  await expect(page.getByRole('heading', { name: '英语专项练习' })).toBeVisible();
  await page.getByRole('button', { name: '标记完成' }).click();
  await expect(page.getByText('已完成', { exact: true })).toBeVisible();
  expect(completedVersion).toBe(1);
  await page.getByRole('button', { name: '撤销授权' }).click();
  await expect(page.getByRole('heading', { name: '英语专项练习' })).toHaveCount(0);
  await page.goto('/teacher');
  await expect(page).toHaveURL(/\/dashboard$/);
});
