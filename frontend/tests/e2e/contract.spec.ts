import { test, expect, type Page } from '@playwright/test';
import { operations, schemaIssues, resolveSchema } from '../../src/api/schema';
import { isRecord } from '../../src/api/errors';
import fixtureData from '../fixtures/responses.json' with { type: 'json' };
import fixtureJobs from '../fixtures/job-results.json' with { type: 'json' };
const uid = (index: number) => `${String(index).padStart(8, '0')}-1111-4111-8111-111111111111`;
const jobKinds: Record<string, string> = {
  op014: 'avatarInteraction',
  op023: 'schoolEvaluation',
  op026: 'explanation',
  op028: 'studyFeedback',
  op029: 'studyFeedback',
  op030: 'studyFeedback',
  op033: 'explanation',
  op035: 'explanation',
  op036: 'prediction',
  op038: 'recognition',
  op039: 'qa',
  op050: 'plan',
  op054: 'plan',
  op061: 'examScore',
  op064: 'scoreEstimate',
  op066: 'interviewTurn',
  op067: 'interviewReport',
  op069: 'introductionReview',
};
interface Seen {
  id: string;
  body: Record<string, unknown>;
  key?: string;
}
async function mockContract(
  page: Page,
  options: { pending?: boolean; emptyDashboard?: boolean; conflict?: boolean } = {},
) {
  const fixtures = structuredClone(fixtureData) as unknown as Record<
    string,
    Record<string, unknown>
  >;
  const results = structuredClone(fixtureJobs) as unknown as Record<
    string,
    Record<string, unknown>
  >;
  const seen: Seen[] = [];
  const violations: string[] = [];
  let lastKind = 'explanation';
  let contextVersion = 1;
  let firstConflict = true;
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/v1/events') {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: ': connected\n\n',
      });
      return;
    }
    const entry = Object.entries(operations).find(
      ([, op]) =>
        op.method === request.method() &&
        new RegExp('^' + op.path.replace(/\{[^}]+\}/g, '([^/]+)') + '$').test(url.pathname),
    );
    if (!entry) {
      violations.push(`${request.method()} ${url.pathname}`);
      await route.fulfill({
        status: 404,
        json: {
          code: 'RESOURCE_NOT_FOUND',
          message: '未约定的请求',
          data: null,
          traceId: 'mock-test',
          details: [],
        },
      });
      return;
    }
    const [id, op] = entry;
    const body = op.multipart ? {} : request.postDataJSON() || {};
    if (
      !['GET', 'HEAD'].includes(op.method) &&
      request.headers()['x-csrf-token'] !== fixtures.op001!.token
    )
      violations.push(`${id} CSRF missing`);
    if (op.idempotent && !request.headers()['idempotency-key'])
      violations.push(`${id} key missing`);
    if (op.body && !op.multipart)
      violations.push(...schemaIssues(op.body, body).map((v) => `${id} ${v}`));
    for (const parameter of op.parameters.filter((p) => p.in === 'query')) {
      const raw = url.searchParams.get(parameter.name);
      if (raw === null) {
        if (parameter.required) violations.push(`${id} query ${parameter.name} missing`);
        continue;
      }
      const schema = resolveSchema(parameter.schema);
      const value = ['number', 'integer'].includes(String(schema.type))
        ? Number(raw)
        : schema.type === 'boolean'
          ? raw === 'true'
          : schema.type === 'array'
            ? raw.split(',')
            : raw;
      violations.push(...schemaIssues(schema, value).map((v) => `${id} ${v}`));
    }
    if (body.requestId && body.requestId !== request.headers()['idempotency-key'])
      violations.push(`${id} requestId/key mismatch`);
    if (body.eventId && body.eventId !== request.headers()['idempotency-key'])
      violations.push(`${id} eventId/key mismatch`);
    seen.push({ id, body, key: request.headers()['idempotency-key'] });
    if (options.emptyDashboard && id === 'op072') {
      await route.fulfill({
        json: { code: 'OK', message: 'ok', traceId: 'mock-test', data: null },
      });
      return;
    }
    if (options.conflict && id === 'op050' && firstConflict) {
      firstConflict = false;
      await route.fulfill({
        status: 503,
        json: {
          code: 'DEPENDENCY_UNAVAILABLE',
          message: '服务暂不可用',
          traceId: 'mock-conflict',
          data: null,
          details: [],
        },
      });
      return;
    }
    if (id === 'op060') {
      fixtures.op060 = {
        ...fixtures.op060,
        answer: body.answer,
        version: Number(body.version) + 1,
      };
      fixtures.op059 = {
        ...fixtures.op059,
        answers: [fixtures.op060],
        version: Number(fixtures.op059!.version) + 1,
      };
    }
    if (id === 'op039') {
      contextVersion = Number(body.contextVersion) + 1;
      fixtures.op039 = {
        ...fixtures.op039,
        jobId: uid(20),
        messageId: uid(8),
        contextVersion,
        resultUrl: `/api/v1/qa/messages/${uid(8)}`,
      };
      const message = results.qa!.message as Record<string, unknown>;
      message.contextVersion = contextVersion;
      fixtures.op041 = { ...message, status: options.pending ? 'running' : 'completed' };
    }
    if (id === 'op053') {
      fixtures.op053 = { ...fixtures.op053, status: 'active', version: 2 };
      fixtures.op051 = fixtures.op053!;
    }
    if (id === 'op061') fixtures.op059 = { ...fixtures.op059, status: 'submitted' };
    if (id === 'op042') fixtures.op042 = { ...fixtures.op041, status: 'cancelled', version: 3 };
    let data: unknown = fixtures[id];
    if (jobKinds[id]) {
      lastKind = jobKinds[id]!;
      if (lastKind === 'studyFeedback') results.studyFeedback!.taskType = body.type;
      if (lastKind === 'recognition')
        results.recognition = {
          ...results.recognition,
          subjectId: body.subjectId,
          fileId: body.fileId,
        };
      if (lastKind === 'interviewTurn')
        results.interviewTurn = { ...results.interviewTurn, interviewId: uid(18) };
      if (lastKind === 'interviewReport')
        results.interviewReport = {
          ...results.interviewReport,
          interviewId: uid(18),
          status: 'completed',
          summary: '明确观点、依据和实例。',
        };
      if (isRecord(data)) data = { ...data, jobId: uid(20) };
    }
    if (id === 'op009')
      data = {
        ...fixtures.op009,
        id: uid(20),
        kind: lastKind,
        status: options.pending ? 'running' : 'completed',
        progress: options.pending ? 20 : 100,
        result: options.pending ? null : results[lastKind],
        error: null,
      };
    if (op.status === 204) {
      await route.fulfill({ status: 204, body: '' });
      return;
    }
    await route.fulfill({
      status: op.status,
      json: { code: 'OK', message: 'ok', traceId: 'mock-test', data },
    });
  });
  return { seen, violations };
}
async function login(page: Page) {
  await page.goto('/login');
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.reload();
  await page.getByLabel('账号', { exact: true }).fill('student');
  await page.getByLabel('密码', { exact: true }).fill('12345678');
  await page.getByRole('button', { name: '登录真实服务', exact: true }).click();
  await expect(page).toHaveURL(/dashboard/);
  await expect(page.getByRole('status').filter({ hasText: /^已完成$/ })).toBeVisible();
}
function action(page: Page, title: string) {
  return page.locator('.contract-nav').getByRole('button', { name: title, exact: true });
}
function submit(page: Page, title: string) {
  return page.locator('form.contract-form').getByRole('button', { name: title, exact: true });
}
test('真实登录协议与11个模块的正式页面均能显示', async ({ page }, testInfo) => {
  const mock = await mockContract(page);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await login(page);
  for (const path of [
    '/companion',
    '/schools',
    '/public-courses',
    '/professional-courses',
    '/qa',
    '/practice',
    '/planning',
    '/exams',
    '/interview',
    '/dashboard',
    '/news',
  ]) {
    await page.goto(path);
    await expect(page.locator('h1')).toBeVisible();
    await expect(page.locator('.contract-workspace')).toBeVisible();
    await expect(page.locator('.contract-nav .chip').first()).toBeVisible();
    await expect(page.getByText('正在载入你的学习资料…', { exact: true })).toHaveCount(0);
    const width = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(width).toBeLessThanOrEqual(1);
  }
  await page.screenshot({ path: testInfo.outputPath('live-news.png'), fullPage: true });
  expect(errors).toEqual([]);
  expect(mock.violations).toEqual([]);
  expect(mock.seen.find((s) => s.id === 'op002')?.body).toEqual({
    loginName: 'student',
    password: '12345678',
  });
});
test('多轮追问递增上下文版本，展示思路而非最终答案', async ({ page }) => {
  const mock = await mockContract(page);
  await login(page);
  await page.goto('/qa');
  await submit(page, '开始新一轮答疑').click();
  await expect(page.getByText('会话已创建，可以连续提问。')).toBeVisible();
  await page.getByLabel('消息内容', { exact: false }).fill('如何分析极限题？');
  await submit(page, '继续提问').click();
  await expect(page.getByText('分析步骤', { exact: true })).toBeVisible();
  await expect(page.getByText('参考答案', { exact: true })).toHaveCount(0);
  await page.getByLabel('消息内容', { exact: false }).fill('请继续解释第一步');
  await submit(page, '继续提问').click();
  await expect(page.getByRole('status').filter({ hasText: /^已完成$/ })).toBeVisible();
  const messages = mock.seen.filter((s) => s.id === 'op039');
  expect(messages.map((m) => m.body.contextVersion)).toEqual([1, 2]);
  expect(messages[0]?.key).not.toBe(messages[1]?.key);
  expect(mock.violations).toEqual([]);
});
test('生成后确认学习计划，版本与幂等键遵循契约', async ({ page }) => {
  const mock = await mockContract(page);
  await login(page);
  await page.goto('/planning');
  await expect(page.getByRole('status').filter({ hasText: /^已完成$/ })).toBeVisible();
  await action(page, '生成候选学习计划').click();
  await submit(page, '生成候选学习计划').click();
  await expect(page.getByText('候选计划', { exact: true })).toBeVisible();
  await action(page, '确认计划并开始执行').click();
  await submit(page, '确认计划并开始执行').click();
  await expect(page.getByText('生效计划', { exact: true })).toBeVisible();
  expect(mock.seen.find((s) => s.id === 'op050')?.body.profileVersion).toBe(3);
  expect(mock.seen.find((s) => s.id === 'op053')?.body.version).toBe(1);
  expect(mock.violations).toEqual([]);
});
test('练习先展示无答案试题，提交后才展示解析', async ({ page }) => {
  const mock = await mockContract(page);
  await login(page);
  await page.goto('/practice');
  await expect(page.getByRole('status').filter({ hasText: /^已完成$/ })).toBeVisible();
  await action(page, '开始练习').click();
  await submit(page, '开始练习').click();
  await expect(
    page.getByRole('heading', { name: '示例训练：选择正确的学习方法。', exact: true }),
  ).toBeVisible();
  await expect(page.getByText('参考答案', { exact: true })).toHaveCount(0);
  await action(page, '提交本题作答').click();
  await page.getByLabel('选项', { exact: false }).selectOption('A');
  await submit(page, '提交本题作答').click();
  await expect(page.getByText('参考答案', { exact: true })).toBeVisible();
  expect(mock.violations).toEqual([]);
});
test('模考自动保存、刷新恢复、交卷评分与答案保护', async ({ page }, testInfo) => {
  const mock = await mockContract(page);
  await login(page);
  await page.goto('/exams');
  await expect(page.getByRole('status').filter({ hasText: /^已完成$/ })).toBeVisible();
  await action(page, '开始限时模考').click();
  await submit(page, '开始限时模考').click();
  await expect(page.getByText('选择题目并作答，答案会自动保存；交卷后不可修改。')).toBeVisible();
  await expect(page.getByText('参考答案', { exact: true })).toHaveCount(0);
  await page.getByLabel('选项', { exact: false }).selectOption('A');
  await expect.poll(() => mock.seen.filter((s) => s.id === 'op060').length).toBe(1);
  await expect(page.getByRole('status').filter({ hasText: /^已完成$/ })).toBeVisible();
  await page.reload();
  await expect(page.getByText('剩余', { exact: false })).toBeVisible();
  await action(page, '交卷并查看成绩').click();
  await submit(page, '交卷并查看成绩').click();
  await expect(page.getByText('总分', { exact: true })).toBeVisible();
  expect(mock.seen.find((s) => s.id === 'op061')?.body.version).toBe(2);
  expect(mock.violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath('exam-result.png'), fullPage: true });
  await expect(page.getByRole('timer')).toContainText('已交卷');
  await action(page, '保存本题答案').click();
  await expect(submit(page, '保存本题答案')).toBeDisabled();
});
test('服务错误不会回退演示，重试保持同一请求与幂等键', async ({ page }) => {
  const mock = await mockContract(page, { conflict: true });
  await login(page);
  await page.goto('/planning');
  await expect(page.getByRole('status').filter({ hasText: /^已完成$/ })).toBeVisible();
  await action(page, '生成候选学习计划').click();
  await submit(page, '生成候选学习计划').click();
  await expect(page.getByRole('alert')).toContainText('服务暂不可用');
  await page.getByRole('button', { name: '重试上次操作', exact: true }).click();
  await expect(page.getByText('候选计划', { exact: true })).toBeVisible();
  const requests = mock.seen.filter((s) => s.id === 'op050');
  expect(requests).toHaveLength(2);
  expect(requests[0]?.key).toBe(requests[1]?.key);
  expect(requests[0]?.body).toEqual(requests[1]?.body);
  expect(mock.violations).toEqual([]);
});
test('生成中停止等待并按最新版本取消答疑消息', async ({ page }) => {
  const mock = await mockContract(page, { pending: true });
  await login(page);
  await page.goto('/qa');
  await submit(page, '开始新一轮答疑').click();
  await page.getByLabel('消息内容', { exact: false }).fill('请帮我分析条件');
  await submit(page, '继续提问').click();
  await expect(page.getByText('正在生成 · 20%', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '停止等待', exact: true }).click();
  await expect.poll(() => mock.seen.filter((s) => s.id === 'op042').length).toBe(1);
  expect(mock.violations).toEqual([]);
});
test('刷新真实模式恢复Cookie会话，无业务数据明确报错', async ({ page }) => {
  const mock = await mockContract(page, { emptyDashboard: true });
  await page.goto('/login');
  await page.evaluate(() => sessionStorage.setItem('kaoyan-mode', 'live'));
  await page.goto('/dashboard');
  await expect(page.getByRole('alert')).toContainText('服务返回的数据不符合约定');
  await expect(page.getByText('当前为前端演示。', { exact: false })).toHaveCount(0);
  expect(mock.seen.some((s) => s.id === 'op004')).toBe(true);
  expect(mock.violations).toEqual([]);
});

test('图片上传、识别、人工核对后发送，绝不冒充文字题', async ({ page }) => {
  const mock = await mockContract(page);
  await login(page);
  await page.goto('/qa');
  await submit(page, '开始新一轮答疑').click();
  await action(page, '上传私有图片或音频').click();
  await page.getByLabel('题目图片或回答录音', { exact: true }).setInputFiles({
    name: '测试题.png',
    mimeType: 'image/png',
    buffer: Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aG9sAAAAASUVORK5CYII=',
      'base64',
    ),
  });
  await submit(page, '上传私有图片或音频').click();
  await expect(page.getByRole('status').filter({ hasText: /^已完成$/ })).toBeVisible();
  await action(page, '识别题目图片').click();
  await submit(page, '识别题目图片').click();
  await expect(page.getByText('需要核对题干', { exact: true })).toBeVisible();
  await action(page, '继续提问').click();
  await page
    .getByRole('combobox', { name: '输入方式', exact: true })
    .selectOption({ label: '已确认的图片题目' });
  await page
    .getByLabel('图片识别记录', { exact: false })
    .selectOption('11111111-1111-4111-8111-111111111111');
  await page
    .getByLabel('已核对的题目文字', { exact: false })
    .fill('已核对题目：求函数的极限，请提供推导步骤。');
  await submit(page, '继续提问').click();
  await expect(page.getByText('分析步骤', { exact: true })).toBeVisible();
  const message = mock.seen.find((v) => v.id === 'op039')!.body;
  expect(message.recognitionId).toBe('11111111-1111-4111-8111-111111111111');
  expect(message.confirmedText).toContain('已核对题目');
  expect('text' in message).toBe(false);
  expect(mock.violations).toEqual([]);
});
test('英语作文辅导与复试训练使用各自结构化任务', async ({ page }) => {
  const mock = await mockContract(page);
  await login(page);
  await page.goto('/public-courses');
  await expect(page.getByRole('status').filter({ hasText: /^已完成$/ })).toBeVisible();
  await action(page, '英语专项辅导').click();
  await page.getByLabel('训练类型', { exact: false }).selectOption('composition');
  await page
    .locator('form.contract-form')
    .getByRole('textbox', { name: '学习内容', exact: false })
    .fill('Please review my introduction: I study software engineering.');
  await submit(page, '英语专项辅导').click();
  await expect(
    page.locator('.contract-output').getByText('作文批改', { exact: true }),
  ).toBeVisible();
  await page.goto('/interview');
  await page.getByLabel('同意本次复试模拟训练', { exact: false }).check();
  await submit(page, '开始复试模拟').click();
  await expect(page.getByText('请介绍一个你熟悉的专业知识点。', { exact: true })).toBeVisible();
  await page
    .getByLabel('消息内容', { exact: false })
    .fill('我熟悉数据库事务，通过一致性约束保障数据正确。');
  await submit(page, '提交回答并继续对练').click();
  await expect(page.getByText('下一问题', { exact: true })).toBeVisible();
  await action(page, '结束练习并生成总结').click();
  await submit(page, '结束练习并生成总结').click();
  await expect(page.getByText('明确观点、依据和实例。', { exact: true })).toBeVisible();
  expect(mock.seen.find((v) => v.id === 'op028')?.body.type).toBe('composition');
  expect(mock.seen.find((v) => v.id === 'op065')?.body.consent).toBe(true);
  expect(mock.violations).toEqual([]);
});
