import { expect, test } from '@playwright/test';

test('platform operator logs in through the isolated Console endpoint', async ({ page }) => {
  await page.route('**/api/console/auth/login', async route => {
    expect(route.request().postDataJSON()).toMatchObject({ username: 'platform-admin' });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      success: true,
      data: { accessToken: 'console-access', refreshToken: 'console-refresh', expiresIn: 900,
        user: { id: '1', username: 'platform-admin', displayName: '平台管理员',
          passwordChangeRequired: false, permissions: ['platform:tenant:view'] } },
      traceId: 'console-login', timestamp: new Date().toISOString(),
    }) });
  });
  await page.route('**/api/console/packages', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
    success: true, data: [{ id: '100', code: 'CHEMICAL', name: '化工企业版', status: 'DRAFT', version: 0, versions: [] }],
    traceId: 'packages', timestamp: new Date().toISOString(),
  }) }));
  await page.route('**/api/console/tenants', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
    success: true, data: [{ id: '200', code: 'CHEM-001', name: '华东化工集团', status: 'ACTIVE',
      initializationStatus: 'READY', timezone: 'Asia/Shanghai', locale: 'zh-CN', packageVersionId: '101', version: 1 }],
    traceId: 'tenants', timestamp: new Date().toISOString(),
  }) }));
  await page.goto('/');
  await page.getByLabel('平台用户名').fill('platform-admin');
  await page.getByLabel('密码').fill('Console#Pass123');
  await page.getByRole('button', { name: '进入 Console' }).click();
  await expect(page.getByRole('status')).toHaveText('欢迎进入平台控制台，平台管理员');
  await expect(page.getByRole('heading', { name: '套餐与模块' })).toBeVisible();
  await expect(page.getByText('化工企业版')).toBeVisible();
  await expect(page.getByText('华东化工集团')).toBeVisible();
  await page.reload();
  await expect(page.getByRole('status')).toHaveText('已恢复当前登录会话');
  await expect(page.getByText('化工企业版')).toBeVisible();
});
