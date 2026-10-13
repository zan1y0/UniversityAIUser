import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.reload();
});
test('11个模块可打开且没有浏览器运行错误', async ({ page }, testInfo) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  for (const [path, title] of [
    ['/dashboard', '学习工作台'],
    ['/companion', '数字人陪伴督学'],
    ['/schools', '智能择校与专业分析'],
    ['/public-courses', '公共课智能辅导'],
    ['/professional-courses', '专业课学习支持'],
    ['/qa', 'AI 答疑与解题'],
    ['/practice', '刷题与错题专项训练'],
    ['/planning', '个性化学习规划'],
    ['/exams', '模考与估分系统'],
    ['/interview', '复试 AI 训练'],
    ['/news', '资讯与政策提醒'],
  ]) {
    await page.goto(path!);
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    await expect(page.getByText('当前为前端演示。', { exact: false })).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
    ).toBe(true);
    await page.screenshot({
      path: testInfo.outputPath(`${path!.slice(1)}.png`),
      fullPage: true,
      animations: 'disabled',
    });
  }
  expect(errors).toEqual([]);
});
test('计划候选确认、任务勾选和刷新恢复', async ({ page }) => {
  await page.goto('/planning');
  await page.getByRole('button', { name: '生成候选计划' }).click();
  await expect(page.getByText('候选计划 · 尚未生效')).toBeVisible();
  await page.getByRole('button', { name: '确认生效' }).click();
  const task = page.locator('.task-check').first();
  await task.check();
  await page.reload();
  await expect(page.locator('.task-check').first()).toBeChecked();
  await expect(page.getByText('生效版本 1', { exact: false })).toBeVisible();
});
test('答错自动加入错题本，正确选项反馈有效', async ({ page }) => {
  await page.goto('/practice');
  await page.getByRole('radio').first().check();
  await page.getByRole('button', { name: '提交答案', exact: true }).click();
  await expect(page.getByText('这道题已加入本地错题本。', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: /错题本/ }).click();
  await expect(
    page.getByRole('heading', { name: '当 x → 0 时，sin x / x 的极限是？' }),
  ).toBeVisible();
});
test('模考答案刷新保留，交卷后只读且有解析', async ({ page }) => {
  await page.goto('/exams');
  await page.getByRole('button', { name: '开始模考' }).click();
  await page.getByRole('radio').nth(1).check();
  await page.reload();
  await expect(page.getByRole('radio').nth(1)).toBeChecked();
  page.on('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: '提交试卷' }).click();
  await expect(page.getByRole('heading', { name: '模考结果', exact: true })).toBeVisible();
  await expect(page.getByRole('radio')).toHaveCount(0);
  await page.getByRole('button', { name: '查看答卷与解析' }).click();
  await expect(page.getByRole('heading', { name: '答卷复盘' })).toBeVisible();
});
test('过期模考刷新后自动交卷', async ({ page }) => {
  await page.goto('/exams');
  await page.getByRole('button', { name: '开始模考' }).click();
  await page.evaluate(() => {
    const raw = localStorage.getItem('kaoyan-demo-v1');
    const data = JSON.parse(raw!);
    data.exam.startedAt = Date.now() - 1000000;
    localStorage.setItem('kaoyan-demo-v1', JSON.stringify(data));
  });
  await page.reload();
  await expect(page.getByRole('heading', { name: '模考结果', exact: true })).toBeVisible();
});
test('课程完成后仪表盘更新，模拟AI对话能发送', async ({ page }) => {
  await page.goto('/public-courses');
  await page.getByRole('button', { name: '开始学习' }).first().click();
  await page.getByRole('button', { name: '标记已学完' }).click();
  await page.goto('/dashboard');
  await expect(page.getByText('0.6', { exact: false }).first()).toBeVisible();
  await page.goto('/qa');
  await page.getByRole('textbox', { name: '输入消息' }).fill('如何判断极限形式？');
  await page.getByRole('button', { name: '发送', exact: true }).click();
  await expect(page.getByText('【演示解题思路】', { exact: false })).toBeVisible();
});
test('登录空成功响应不得进入真实工作台', async ({ page }) => {
  await page.route('**/api/user/login', (route) =>
    route.fulfill({ json: { code: 200, message: 'success', data: null } }),
  );
  await page.goto('/login');
  await page.getByLabel('手机号', { exact: true }).fill('13800000000');
  await page.getByLabel('密码', { exact: true }).fill('demo-only');
  await page.getByRole('button', { name: '登录真实服务' }).click();
  await expect(page.getByRole('alert')).toContainText('后端尚未返回业务数据');
  await expect(page).toHaveURL(/login/);
});
test('真实模式空业务响应显示错误，不回退为演示数据', async ({ page }) => {
  await page.route('**/api/user/login', (route) =>
    route.fulfill({ json: { code: 200, data: { id: '100', nickname: '联调用户' } } }),
  );
  await page.route('**/api/dashboard/overview*', (route) =>
    route.fulfill({ json: { code: 200, data: null } }),
  );
  await page.goto('/login');
  await page.getByLabel('手机号', { exact: true }).fill('13800000000');
  await page.getByLabel('密码', { exact: true }).fill('demo-only');
  await page.getByRole('button', { name: '登录真实服务' }).click();
  await expect(page.getByRole('alert')).toContainText('后端尚未返回业务数据');
  await expect(page.getByText('当前为前端演示。', { exact: false })).toHaveCount(0);
  await expect(page.locator('.metric')).toHaveCount(0);
});
