import { test, expect } from '@playwright/test';

test('首屏登录注册居中，手机无横向溢出且支持减少动画', async ({ page }, info) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '每一份努力，都有微光' })).toBeVisible();
  await expect(page.locator('.firefly')).toHaveCount(32);
  const card = await page.locator('.auth-card').boundingBox();
  const size = page.viewportSize()!;
  expect(Math.abs(card!.x + card!.width / 2 - size.width / 2)).toBeLessThan(2);
  expect(Math.abs(card!.y + card!.height / 2 - size.height / 2)).toBeLessThan(16);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('login.png'), fullPage: true });
  await page.getByRole('button', { name: '注册', exact: true }).click();
  await expect(page.getByLabel('确认密码', { exact: true })).toBeVisible();
  await page.screenshot({ path: info.outputPath('register.png'), fullPage: true });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  expect(
    await page
      .locator('.firefly')
      .first()
      .evaluate((el) => getComputedStyle(el).animationName),
  ).toBe('none');
});

test('真实注册、刷新恢复、退出撤销和重新登录', async ({ page, context }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const account = `e2e_${Date.now()}_${Math.floor(Math.random() * 10000)}`;
  await page.goto('/');
  await page.getByRole('button', { name: '注册', exact: true }).click();
  await page.getByLabel('昵称', { exact: true }).fill('微光同学');
  await page.getByLabel('账号', { exact: true }).fill(account);
  await page.getByLabel('密码', { exact: true }).fill('Study2026!');
  await page.getByLabel('确认密码', { exact: true }).fill('Mismatch123');
  await page.getByRole('button', { name: '注册并开启学习' }).click();
  await expect(page.getByRole('alert')).toHaveText('两次输入的密码不一致');
  await page.getByLabel('确认密码', { exact: true }).fill('Study2026!');
  await page.getByRole('button', { name: '注册并开启学习' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  const token = (await context.cookies()).find((cookie) => cookie.name === 'AUTH_TOKEN');
  expect(token?.httpOnly).toBe(true);
  expect(token?.sameSite).toBe('Lax');
  expect(await page.evaluate(() => document.cookie.includes('AUTH_TOKEN'))).toBe(false);
  await page.reload();
  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible();
  await expect(page.locator('.user-name')).toHaveText('微光同学');
  await page.getByRole('button', { name: '退出登录' }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect((await context.cookies()).some((cookie) => cookie.name === 'AUTH_TOKEN')).toBe(false);
  await page.goto('/planning');
  await expect(page).toHaveURL(/\/login\?redirect=/);
  await page.getByLabel('账号', { exact: true }).fill(account);
  await page.getByLabel('密码', { exact: true }).fill('Wrong2026!');
  await page.getByRole('button', { name: '登录学习空间' }).click();
  await expect(page.getByRole('alert')).toHaveText('账号或密码错误');
  await page.getByLabel('密码', { exact: true }).fill('Study2026!');
  await page.getByRole('button', { name: '登录学习空间' }).click();
  await expect(page).toHaveURL(/\/planning$/);
  expect(errors).toEqual([]);
});
