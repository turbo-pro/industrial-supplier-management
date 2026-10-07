import { expect, test } from '@playwright/test';

test('platform operator logs in through the isolated Console endpoint', async ({ page }) => {
  await page.route('**/api/console/auth/login', async route => {
    expect(route.request().postDataJSON()).toMatchObject({ username: 'platform-admin' });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      success: true,
      data: { accessToken: 'console-access', refreshToken: 'console-refresh', expiresIn: 900,
        user: { id: '1', username: 'platform-admin', displayName: '平台管理员',
          passwordChangeRequired: false, passwordChangeRecommended: false, permissions: ['platform:tenant:view'] } },
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

test('forced Console password change blocks workspace until new login', async ({ page }) => {
  await page.route('**/api/console/auth/login', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
    success: true, data: { accessToken: 'forced-token', refreshToken: 'refresh', expiresIn: 900,
      user: { id: '1', username: 'platform-admin', displayName: '平台管理员',
        passwordChangeRequired: true, passwordChangeRecommended: false, permissions: [] } },
  }) }));
  let workspaceRequests = 0;
  await page.route('**/api/console/packages', route => { workspaceRequests++; return route.abort(); });
  await page.route('**/api/console/tenants', route => { workspaceRequests++; return route.abort(); });
  await page.route('**/api/console/auth/change-password', async route => {
    expect(route.request().postDataJSON()).toEqual({ oldPassword: 'Console#Pass123', newPassword: 'Changed#Pass456' });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: null }) });
  });
  await page.goto('/');
  await page.getByLabel('平台用户名').fill('platform-admin');
  await page.getByLabel('密码').fill('Console#Pass123');
  await page.getByRole('button', { name: '进入 Console' }).click();
  await expect(page.getByRole('heading', { name: '需要修改密码' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: '需要修改密码' })).toBeVisible();
  expect(workspaceRequests).toBe(0);
  await page.getByLabel('当前密码').fill('Console#Pass123');
  await page.getByLabel('新密码', { exact: true }).fill('Changed#Pass456');
  await page.getByLabel('确认新密码').fill('Changed#Pass456');
  await page.getByRole('button', { name: '确认修改' }).click();
  await expect(page.getByRole('heading', { name: '平台人员登录' })).toBeVisible();
  await expect(page.getByRole('status')).toHaveText('密码已修改，请重新登录');
});

test('inactive Console password suggestion can be postponed', async ({ page }) => {
  await page.route('**/api/console/auth/login', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
    success: true, data: { accessToken: 'inactive-token', refreshToken: 'refresh', expiresIn: 900,
      user: { id: '1', username: 'platform-admin', displayName: '平台管理员',
        passwordChangeRequired: false, passwordChangeRecommended: true, permissions: [] } },
  }) }));
  await page.route('**/api/console/packages', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) }));
  await page.route('**/api/console/tenants', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) }));
  await page.goto('/');
  await page.getByLabel('平台用户名').fill('platform-admin');
  await page.getByLabel('密码').fill('Console#Pass123');
  await page.getByRole('button', { name: '进入 Console' }).click();
  await expect(page.getByRole('alert')).toContainText('长期未登录');
  await expect(page.getByRole('heading', { name: '套餐与模块' })).toBeVisible();
  await page.getByRole('button', { name: '稍后再说' }).click();
  await expect(page.getByRole('alert')).toHaveCount(0);
});
