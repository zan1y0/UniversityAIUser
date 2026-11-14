import { expect, test } from '@playwright/test';
import fixtures from '../fixtures/responses.json' with { type: 'json' };
import type { StudyMode, TaskAction } from '../../src/api/study-supervision';

test('旧后端没有督学任务接口时，仍能用数字人设置切换模式并在刷新后恢复', async ({ page }, testInfo) => {
  const missingStatus = testInfo.project.name === 'mobile' ? 500 : 404;
  let preferences = { ...fixtures.op012, mode: 'gentle' };
  const saves: unknown[] = [];
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/events')) {
      await route.fulfill({ contentType: 'text/event-stream', body: ': connected\n\n' });
      return;
    }
    let data: unknown = {};
    let status = 200;
    if (path.endsWith('/auth/me')) data = fixtures.op084;
    else if (path.endsWith('/session/csrf')) data = fixtures.op001;
    else if (path.endsWith('/study-supervision') || path.endsWith('/study-mode')) status = missingStatus;
    else if (path.endsWith('/avatar/preferences')) {
      if (request.method() === 'PUT') {
        saves.push(request.postDataJSON());
        preferences = { ...request.postDataJSON(), version: preferences.version + 1 };
      }
      data = preferences;
    }
    await route.fulfill({
      status,
      json: {
        code: status === 200 ? 'OK' : status === 500 ? 'INTERNAL_ERROR' : 'RESOURCE_NOT_FOUND',
        message: '测试响应',
        traceId: 'test',
        data,
      },
    });
  });
  await page.goto('/dashboard');
  async function openSettings() {
    await page.getByRole('button', { name: '研小途，切换对话' }).click();
    await page.getByRole('button', { name: '设置', exact: true }).click();
  }
  await openSettings();
  await expect(
    page.getByText(missingStatus === 404 ? '任务提醒和专注计时需更新后端后使用' : '仍可切换模式', { exact: false }),
  ).toBeVisible();
  const strict = page
    .getByRole('group', { name: '模式切换' })
    .getByRole('button', { name: '严管模式' });
  await strict.click();
  await expect(strict).toHaveAttribute('aria-pressed', 'true');
  expect(saves).toEqual([{ ...fixtures.op012, mode: 'strict' }]);
  await page.reload();
  await openSettings();
  await expect(strict).toHaveAttribute('aria-pressed', 'true');
});

test('机器人设置里的三个答辩预览共用模拟任务且不写入正式数据', async ({ page }) => {
  const calls: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/v1/study-supervision'))
      calls.push(request.url());
  });
  await page.goto('/login');
  await page.getByRole('button', { name: '先逛逛学习空间' }).click();
  await page.getByRole('button', { name: '研小途，切换对话' }).click();
  await page.getByRole('button', { name: '设置', exact: true }).click();
  const options = page.getByRole('group', { name: '模式切换' });
  await page.getByRole('button', { name: '预览督学反馈' }).click();
  const preview = page.getByRole('region', { name: '答辩预览' });
  for (const [mode, action, reminder, feedback] of [
    ['学霸模式', '开始推荐任务', '优先级：高', '实际学习 20 分钟'],
    ['温柔模式', '先学 5 分钟', '先读一句，找出主谓宾', '又推进了一项'],
    ['严管模式', '立即开始计时', '当前尚未开始', '剩余 2 项任务'],
  ]) {
    const option = options.getByRole('button', { name: mode });
    await option.click();
    await expect(option).toHaveAttribute('aria-pressed', 'true');
    await expect(preview.getByText(/英语长难句 · 计划 10:00/)).toBeVisible();
    await expect(preview.getByText(reminder, { exact: false })).toBeVisible();
    await expect(preview.getByText(feedback, { exact: false })).toBeVisible();
    await preview.getByRole('button', { name: action }).click();
    await expect(preview.getByRole('status')).toContainText('预览不会修改正式数据');
  }
  await preview.getByRole('button', { name: '调整时间' }).click();
  expect(calls).toEqual([]);
});

test('正式卡片改期、启动正确任务并在切换模式时保留计时', async ({ page }) => {
  const realTask = '11111111-1111-4111-8111-111111111111';
  let mode: StudyMode = 'STRICT';
  let at = '2026-11-10T02:00:00Z';
  let start: string | null = null;
  let end: string | null = null;
  let shouldFail = true;
  const actions: { id: string; body: TaskAction }[] = [];
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    let data: unknown = {};
    let status = 200;
    if (path.endsWith('/events')) {
      await route.fulfill({ contentType: 'text/event-stream', body: ': connected\n\n' });
      return;
    }
    if (path.endsWith('/auth/me')) data = fixtures.op084;
    else if (path.endsWith('/profile')) data = fixtures.op002;
    else if (path.endsWith('/session/csrf')) data = { token: 'test', headerName: 'X-CSRF-Token' };
    else if (path.endsWith('/study-mode')) {
      if (shouldFail) {
        shouldFail = false;
        status = 503;
      } else mode = request.postDataJSON().mode;
      data = { mode };
    } else if (path.includes('/study-supervision/tasks/')) {
      const body = request.postDataJSON() as TaskAction;
      actions.push({ id: path.split('/').at(-1)!, body });
      if (body.action === 'SCHEDULE') at = new Date(body.scheduledAt).toISOString();
      if (body.action === 'START') {
        start = new Date().toISOString();
        end = new Date(Date.now() + 300_000).toISOString();
      }
      if (body.action === 'STOP') end = null;
      data = { ok: true };
    } else if (path.endsWith('/study-supervision')) {
      data = {
        mode,
        date: url.searchParams.get('date'),
        tasks: [],
        feedback: null,
        reminders: start
          ? []
          : [
              {
                taskId: realTask,
                sourceTaskId: realTask,
                scheduledAt: at,
                scheduledLabel: '11-10 10:00',
                title: '英语长难句',
                round: 1,
                priority: null,
                minutes: 20,
                action: mode === 'STRICT' ? 'START_TIMER' : 'START_FIVE',
                text: '英语长难句任务尚未开始。',
              },
            ],
        timers: start
          ? [{ id: realTask, title: '英语长难句', focusStart: start, focusEnd: end }]
          : [],
      };
    }
    await route.fulfill({
      status,
      json: {
        code: status === 200 ? 'OK' : 'UNAVAILABLE',
        message: '测试响应',
        traceId: 'test',
        data,
      },
    });
  });
  await page.goto('/dashboard');
  const open = async () => {
    await page.getByRole('button', { name: '研小途，切换对话' }).click();
    await page.getByRole('button', { name: '设置', exact: true }).click();
  };
  await open();
  const settings = page.getByRole('region', { name: '督学设置' });
  const gentle = settings.getByRole('button', { name: '温柔模式' });
  const strict = settings.getByRole('button', { name: '严管模式' });
  const card = page.getByRole('region', { name: '正式督学提醒' });
  await card.getByRole('button', { name: '调整时间' }).click();
  await page.getByLabel('计划开始时间（北京时间）').fill('2026-11-10T10:15');
  await page.getByRole('button', { name: '保存时间' }).click();
  await expect
    .poll(() => actions[0])
    .toEqual({
      id: realTask,
      body: { action: 'SCHEDULE', scheduledAt: '2026-11-10T10:15:00+08:00' },
    });
  await gentle.click();
  await expect(page.getByText('模式保存失败，请再次选择重试。')).toBeVisible();
  await expect(strict).toHaveAttribute('aria-pressed', 'true');
  await gentle.click();
  await expect(gentle).toHaveAttribute('aria-pressed', 'true');
  await card.getByRole('button', { name: '先学 5 分钟' }).click();
  await expect.poll(() => actions[1]).toEqual({ id: realTask, body: { action: 'START' } });
  const timer = page.getByRole('region', { name: '任务专注计时' });
  await expect(timer.getByRole('timer')).toBeVisible();
  const originalEnd = end;
  await strict.click();
  await expect(strict).toHaveAttribute('aria-pressed', 'true');
  await expect(timer.getByRole('timer')).toBeVisible();
  await page.reload();
  await open();
  await expect(strict).toHaveAttribute('aria-pressed', 'true');
  await expect(timer.getByRole('timer')).toBeVisible();
  expect(end).toEqual(originalEnd);
  expect(actions).toHaveLength(2);

  // Drive the browser clock past the same server deadline.
  await page.clock.install();
  await page.clock.fastForward(310_000);
  await expect(timer.getByText('本次计时已结束。', { exact: false })).toBeVisible();
  await expect(timer.getByRole('button', { name: '继续学习' })).toBeVisible();
  await timer.getByRole('button', { name: '结束本次专注' }).click();
  await expect(timer.getByText('任务仍待完成', { exact: false })).toBeVisible();
  expect(actions[2]).toEqual({ id: realTask, body: { action: 'STOP' } });
});
