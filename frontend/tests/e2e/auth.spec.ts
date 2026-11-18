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

test('邮箱注册、刷新恢复、退出撤销和重置密码', async ({ page, context }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const account = 'student@example.test';
  let active = false;
  let password = 'Study2026!';
  let resetStarted = false;
  const user = { id: '11111111-1111-4111-8111-111111111111', email: account, displayName: '微光同学',
    activeRole: 'STUDENT', roles: ['STUDENT'], permissions: ['student:study:use'] };
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    const body = route.request().postDataJSON();
    const envelope = (data: unknown) => ({ code: 'OK', message: 'ok', traceId: 'test', data });
    if (path.endsWith('/session/csrf')) return route.fulfill({ json: envelope({ token: 'csrf', headerName: 'X-CSRF-Token' }) });
    if (path.endsWith('/auth/email/code')) {
      expect(body.email).toBe(account);
      expect(body.purpose).toBe(resetStarted ? 'RESET' : 'REGISTER');
      return route.fulfill({ status: 202, json: envelope({ accepted: true }) });
    }
    if (path.endsWith('/auth/register')) {
      expect(body).toMatchObject({ email: account, password, code: '246810', acceptedTerms: true, displayName: user.displayName });
      active = true;
      return route.fulfill({ status: 201, json: envelope(user), headers: { 'Set-Cookie': 'AUTH_TOKEN=test; Path=/; HttpOnly; SameSite=Lax' } });
    }
    if (path.endsWith('/auth/logout')) {
      active = false;
      return route.fulfill({ status: 204, headers: { 'Set-Cookie': 'AUTH_TOKEN=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax' } });
    }
    if (path.endsWith('/auth/password/reset')) {
      expect(body).toEqual({ email: account, code: '246810', password: 'NewStudy2027!', confirmPassword: 'NewStudy2027!' });
      password = body.password;
      return route.fulfill({ status: 204 });
    }
    if (path.endsWith('/auth/login')) {
      if (body.password !== password) return route.fulfill({ status: 401, json: { ...envelope(null), code: 'INVALID_CREDENTIALS', message: '邮箱或密码错误' } });
      active = true;
      return route.fulfill({ json: envelope(user) });
    }
    if (path.endsWith('/auth/me') && !active) return route.fulfill({ status: 401, json: { ...envelope(null), code: 'UNAUTHENTICATED' } });
    return route.fulfill({ json: envelope(path.endsWith('/auth/me') ? user : {}) });
  });
  await page.goto('/');
  await page.getByRole('button', { name: '注册', exact: true }).click();
  await page.getByLabel('昵称', { exact: true }).fill('微光同学');
  await page.getByLabel('邮箱', { exact: true }).fill(account);
  await page.getByRole('button', { name: '获取验证码' }).click();
  await expect(page.getByText('请求已受理，请查看邮箱。')).toBeVisible();
  await page.locator('#email-code').fill('246810');
  await page.getByRole('checkbox', { name: /同意用户协议/ }).check();
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
  await page.getByLabel('邮箱', { exact: true }).fill(account);
  await page.getByLabel('密码', { exact: true }).fill('Wrong2026!');
  await page.getByRole('button', { name: '登录学习空间' }).click();
  await expect(page.getByRole('alert')).toHaveText('邮箱或密码错误');
  resetStarted = true;
  await page.getByRole('button', { name: '忘记密码' }).click();
  await page.getByRole('button', { name: '获取验证码' }).click();
  await expect(page.getByText('请求已受理，请查看邮箱。')).toBeVisible();
  await page.locator('#email-code').fill('246810');
  await page.getByLabel('密码', { exact: true }).fill('NewStudy2027!');
  await page.getByLabel('确认密码', { exact: true }).fill('NewStudy2027!');
  await page.getByRole('button', { name: '重置密码', exact: true }).click();
  await expect(page.getByText('密码已重置，请使用新密码登录。')).toBeVisible();
  await page.getByLabel('密码', { exact: true }).fill('NewStudy2027!');
  await page.getByRole('button', { name: '登录学习空间' }).click();
  await expect(page).toHaveURL(/\/planning$/);
  expect(errors).toEqual([]);
});

test('老师邀请缺少原因时不提交，投递失败后可重发', async ({ page }) => {
  const email = 'operator@example.test';
  const teacherEmail = 'teacher@example.test';
  const user = { id: '11111111-1111-4111-8111-111111111111', email, displayName: '平台管理员',
    activeRole: 'ADMIN', roles: ['ADMIN'], permissions: ['user:list', 'teacher:create', 'teacher:invite', 'user:update'] };
  let loggedIn = false;
  let deliveryStatus: 'FAILED' | 'SENT' | undefined;
  let submissions = 0;
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    const envelope = (data: unknown) => ({ code: 'OK', message: 'ok', traceId: 'test', data });
    if (path.endsWith('/session/csrf')) return route.fulfill({ json: envelope({ token: 'csrf', headerName: 'X-CSRF-Token' }) });
    if (path.endsWith('/auth/login')) {
      expect(route.request().postDataJSON()).toEqual({ email, password: 'Study2026!' });
      loggedIn = true;
      return route.fulfill({ json: envelope(user) });
    }
    if (path.endsWith('/auth/me') && !loggedIn) return route.fulfill({ status: 401, json: { ...envelope(null), code: 'UNAUTHENTICATED' } });
    if (path.endsWith('/admin/teachers')) {
      submissions++;
      expect(route.request().postDataJSON()).toEqual({ email: teacherEmail, displayName: '新老师', reason: '新增指导教师' });
      deliveryStatus = 'FAILED';
      return route.fulfill({ status: 503, json: { ...envelope({ id: user.id, deliveryStatus }),
        code: 'MAIL_UNAVAILABLE', message: '邀请未发送，可在用户列表重新发送' } });
    }
    if (path.endsWith('/invitation/resend')) {
      expect(route.request().postDataJSON()).toEqual({ reason: '新增指导教师' });
      deliveryStatus = 'SENT';
      return route.fulfill({ status: 202, json: envelope({ id: user.id, deliveryStatus }) });
    }
    if (path.endsWith('/admin/users')) return route.fulfill({ json: envelope({ items: deliveryStatus ? [{
      id: user.id, email: teacherEmail, displayName: '新老师', status: 'PENDING_ACTIVATION', roles: ['TEACHER'], deliveryStatus,
    }] : [] }) });
    return route.fulfill({ json: envelope(path.endsWith('/auth/me') ? user : {}) });
  });
  await page.goto('/login');
  await expect(page.getByLabel('登录身份')).toHaveCount(0);
  await page.getByLabel('邮箱', { exact: true }).fill(email);
  await page.getByLabel('密码', { exact: true }).fill('Study2026!');
  await page.getByRole('button', { name: '登录学习空间' }).click();
  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByRole('heading', { name: '平台管理' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '页面未找到' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '操作日志' })).toHaveCount(0);
  await page.getByLabel('老师姓名').fill('新老师');
  await page.getByLabel('老师邮箱').fill(teacherEmail);
  await page.getByRole('button', { name: '创建并发送邀请' }).click();
  await expect(page.getByRole('alert')).toHaveText('请填写操作原因后再提交。');
  await expect(page.getByLabel('操作原因')).toBeFocused();
  expect(submissions).toBe(0);
  await page.getByLabel('操作原因').fill('新增指导教师');
  await page.getByRole('button', { name: '创建并发送邀请' }).click();
  await expect(page.getByRole('alert')).toHaveText('邀请未发送，可在用户列表重新发送');
  await expect(page.getByText('邀请发送失败，可重发')).toBeVisible();
  await page.getByRole('button', { name: '重发邀请' }).click();
  await expect(page.getByText('邮件服务已接收', { exact: true })).toBeVisible();
  expect(submissions).toBe(1);
});
