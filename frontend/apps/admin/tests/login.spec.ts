import { expect, test } from '@playwright/test';

test('tenant administrator can log in with the generated API client', async ({ page }) => {
  await page.route('**/api/auth/login', async route => {
    const request = route.request();
    expect(request.postDataJSON()).toMatchObject({ tenantCode: 'demo', username: 'admin' });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      success: true,
      data: { accessToken: 'a', refreshToken: 'r', expiresIn: 900,
        user: { id: '1', tenantId: '10', username: 'admin', displayName: '演示管理员', passwordChangeRequired: false, passwordChangeRecommended: true } },
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
  await page.route('**/api/navigation/menus', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
    success: true, data: [{ id: '300', code: 'SYSTEM_MANAGEMENT', name: '系统管理', route: '/system', component: 'Layout', icon: 'setting',
      children: [{ id: '301', code: 'ORGANIZATION_MANAGEMENT', name: '组织管理', route: '/system/organizations', component: 'OrganizationPage', icon: 'organization', children: [] }] }],
    traceId: 'menus', timestamp: new Date().toISOString(),
  }) }));
  await page.route('**/api/suppliers**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
    success: true, data: { total: 1, page: 0, size: 20, items: [{ id: '900', organizationId: '100', code: 'SUP-001', name: '华东设备制造', type: 'MANUFACTURER', status: 'ACTIVE', riskLevel: 'LOW', version: 0, updatedAt: new Date().toISOString() }] }, traceId: 'suppliers', timestamp: new Date().toISOString(),
  }) }));
  await page.goto('/');
  await page.getByLabel('租户编码').fill('demo');
  await page.getByLabel('用户名').fill('admin');
  await page.getByLabel('密码').fill('Demo-password-1');
  await page.getByRole('button', { name: '登录系统', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '修改登录密码' })).toContainText('长期未登录');
  await page.getByRole('button', { name: '稍后再说' }).click();
  await expect(page.getByRole('heading', { name: '供应商档案' })).toBeVisible();
  await expect(page.locator('strong').filter({ hasText: '华东设备制造' })).toBeVisible();
  await page.getByText('系统管理').click();
  await expect(page.getByRole('menuitem', { name: '组织管理' })).toBeVisible();
  await page.getByRole('menuitem', { name: '组织管理' }).click();
  await expect(page).toHaveURL(/\/coming-soon$/);
  await page.reload();
  await expect(page.getByRole('button', { name: '退出' })).toBeVisible();
});

test('first login requires password change before loading the workspace', async ({ page }) => {
  await page.route('**/api/auth/login', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
    success: true, data: { accessToken: 'initial-token', refreshToken: 'refresh', expiresIn: 900,
      user: { id: '1', tenantId: '10', username: 'admin', displayName: '管理员',
        passwordChangeRequired: true, passwordChangeRecommended: false } },
  }) }));
  let workspaceRequests = 0;
  await page.route('**/api/organizations/**', route => { workspaceRequests++; return route.abort(); });
  await page.route('**/api/navigation/menus', route => { workspaceRequests++; return route.abort(); });
  await page.route('**/api/auth/change-password', async route => {
    expect(route.request().postDataJSON()).toEqual({ oldPassword: 'Initial#Pass123', newPassword: 'Changed#Pass456' });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: null }) });
  });
  await page.goto('/');
  await page.getByRole('button', { name: '登录系统' }).click();
  await expect(page.getByRole('dialog', { name: '修改登录密码' })).toBeVisible();
  expect(workspaceRequests).toBe(0);
  await page.getByLabel('当前密码').fill('Initial#Pass123');
  await page.getByLabel('新密码', { exact: true }).fill('Changed#Pass456');
  await page.getByLabel('确认新密码').fill('Changed#Pass456');
  await page.getByRole('button', { name: '确认修改' }).click();
  await expect(page.getByRole('button', { name: '登录系统' })).toBeVisible();
});
