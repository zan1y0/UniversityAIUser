import { test, expect } from '@playwright/test';
import { operations } from '../../src/api/schema';
import fixtures from '../fixtures/responses.json' with { type: 'json' };
import results from '../fixtures/job-results.json' with { type: 'json' };

test('真实数字人聊天发送、连续上下文、长度边界与新对话', async ({ page }) => {
  const messages: { text: string; contextId: string }[] = [];
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request();
    const path = new URL(req.url()).pathname;
    if (path.endsWith('/events')) {
      await route.fulfill({ contentType: 'text/event-stream', body: ': connected\n\n' });
      return;
    }
    let data: unknown;
    let status = 200;
    if (path.endsWith('/auth/me')) {
      data = { ...fixtures.op084, displayName: '聊天测试同学' };
    } else if (path.endsWith('/avatar/interactions')) {
      messages.push(req.postDataJSON());
      expect(req.headers()['x-csrf-token']).toBeTruthy();
      expect(req.headers()['idempotency-key']).toBeTruthy();
      data = fixtures.op014;
      status = 202;
    } else if (path.includes('/jobs/')) {
      data = {
        ...fixtures.op009,
        status: 'completed',
        progress: 100,
        result: { ...results.avatarInteraction, text: `真实接口测试回复${messages.length}` },
      };
    } else {
      const entry = Object.entries(operations).find(
        ([, op]) => op.method === req.method() && op.path === path,
      );
      if (!entry) throw new Error(`未预期的请求：${req.method()} ${path}`);
      data = fixtures[entry[0] as keyof typeof fixtures];
    }
    await route.fulfill({
      status,
      json: { code: 'OK', message: 'ok', traceId: 'avatar-e2e', data },
    });
  });
  await page.goto('/companion');
  const chat = page.locator('.chat-panel');
  const input = chat.getByRole('textbox', { name: '输入消息' });
  await input.fill('今天有点累');
  await chat.getByRole('button', { name: '发送', exact: true }).click();
  await expect(chat.getByText('真实接口测试回复1', { exact: true })).toBeVisible();
  await input.fill('字'.repeat(4000));
  await chat.getByRole('button', { name: '发送', exact: true }).click();
  await expect(chat.getByText('真实接口测试回复2', { exact: true })).toBeVisible();
  expect(messages[1]!.text).toBe('字'.repeat(4000));
  expect(messages[1]!.contextId).toBe(messages[0]!.contextId);
  await chat.getByRole('button', { name: '新对话', exact: true }).click();
  await expect(chat.getByText('真实接口测试回复1', { exact: true })).toHaveCount(0);
  await input.fill('重新开始');
  await chat.getByRole('button', { name: '发送', exact: true }).click();
  await expect(chat.getByText('真实接口测试回复3', { exact: true })).toBeVisible();
  expect(messages[2]!.contextId).not.toBe(messages[0]!.contextId);
  expect(messages[2]!.text).not.toContain('今天有点累');

  await page.goto('/practice');
  const robot = page.getByRole('button', { name: '研小途，切换对话' });
  const panel = page.getByRole('region', { name: '研小途对话' });
  await expect(robot).toBeInViewport();
  await robot.click();
  await panel.getByRole('textbox').fill('刷题前先做什么？');
  await panel.getByRole('button', { name: '发送', exact: true }).click();
  await expect(panel.getByText('真实接口测试回复4', { exact: true })).toBeVisible();
  expect(messages[3]!.text).toBe('刷题前先做什么？');
  await panel.getByRole('button', { name: '收起对话' }).click();
  await expect(panel).toBeHidden();
  expect(errors).toEqual([]);
});
