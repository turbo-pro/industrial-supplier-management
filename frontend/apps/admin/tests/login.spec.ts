import { expect, test } from '@playwright/test';

test('tenant administrator can log in with the generated API client', async ({ page }) => {
  await page.route('**/api/auth/login', async route => {
    const request = route.request();
    expect(request.postDataJSON()).toMatchObject({ tenantCode: 'demo', username: 'admin' });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      success: true,
      data: { accessToken: 'a', refreshToken: 'r', expiresIn: 900,
        user: { id: '1', tenantId: '10', username: 'admin', displayName: '演示管理员', passwordChangeRequired: false } },
      traceId: 'e2e-login', timestamp: new Date().toISOString(),
    }) });
  });
  await page.route('**/api/organizations/tree', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
    success: true, data: [{ id: '100', parentId: null, code: 'HEADQUARTERS', name: '集团总部', type: 'HEADQUARTERS', status: 'ACTIVE', sortOrder: 0, version: 0,
      children: [{ id: '101', parentId: '100', code: 'SITE_A', name: '华东化工基地', type: 'SITE', status: 'ACTIVE', sortOrder: 10, version: 0, children: [] }] }],
    traceId: 'org-tree', timestamp: new Date().toISOString(),
  }) }));
  await page.route('**/api/organizations/current', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
    success: true, data: { id: '100', code: 'HEADQUARTERS', name: '集团总部', type: 'HEADQUARTERS' },
    traceId: 'current-org', timestamp: new Date().toISOString(),
  }) }));
  await page.goto('/');
  await page.getByLabel('租户编码').fill('demo');
  await page.getByLabel('用户名').fill('admin');
  await page.getByLabel('密码').fill('Demo-password-1');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('欢迎，演示管理员');
  await expect(page.getByRole('heading', { name: '集团总部' })).toBeVisible();
  await expect(page.getByLabel('当前组织')).toHaveValue('100');
});
