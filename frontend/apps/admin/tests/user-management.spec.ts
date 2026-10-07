import { expect, test } from '@playwright/test';

const response = (data: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data }) });

test('tenant user page creates and disables a member with versioned request', async ({ page }) => {
  let members = [
    { id: '1', username: 'admin', displayName: '管理员', status: 'ACTIVE', passwordChangeRequired: false, version: 0, roleIds: ['10'] },
    { id: '2', username: 'member', displayName: '成员', status: 'ACTIVE', passwordChangeRequired: false, version: 0, roleIds: ['11'] },
  ];
  let createdBody: Record<string, unknown> | undefined;
  await page.route('**/api/auth/login', route => route.fulfill(response({ accessToken: 'user-token', refreshToken: 'refresh', expiresIn: 900,
    user: { id: '1', tenantId: '10', username: 'admin', displayName: '管理员', passwordChangeRequired: false, passwordChangeRecommended: false } })));
  await page.route('**/api/organizations/tree', route => route.fulfill(response([{ id: '100', code: 'HQ', name: '总部', type: 'HEADQUARTERS', status: 'ACTIVE', children: [] }])));
  await page.route('**/api/organizations/current', route => route.fulfill(response({ id: '100', code: 'HQ', name: '总部', type: 'HEADQUARTERS' })));
  await page.route('**/api/navigation/menus', route => route.fulfill(response([])));
  await page.route('**/api/configuration/branding', route => route.fulfill(response({ systemName: '', logoUrl: '', faviconUrl: '', footerText: '' })));
  await page.route('**/api/access/roles', route => route.fulfill(response([
    { id: '10', code: 'TENANT_ADMIN', name: '租户管理员', builtIn: true, status: 'ACTIVE', version: 0 },
    { id: '11', code: 'MEMBER', name: '普通成员', builtIn: false, status: 'ACTIVE', version: 0 },
  ])));
  await page.route('**/api/access/users', async route => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      createdBody = body;
      expect(route.request().headers()['idempotency-key']).toBeTruthy();
      members.push({ id: '3', username: 'newmember', displayName: '新成员', status: 'ACTIVE', passwordChangeRequired: true, version: 0, roleIds: ['11'] });
      await route.fulfill(response(members[2]));
    } else await route.fulfill(response(members));
  });
  await page.route('**/api/access/users/2/status', async route => {
    expect(route.request().postDataJSON()).toEqual({ status: 'DISABLED', version: 0 });
    expect(route.request().headers()['idempotency-key']).toBeTruthy();
    members = members.map(member => member.id === '2' ? { ...member, status: 'DISABLED', version: 1 } : member);
    await route.fulfill(response(members[1]));
  });
  await page.route('**/api/access/users/2/roles', async route => {
    expect(route.request().postDataJSON()).toEqual({ roleIds: ['11', '10'], version: 1 });
    expect(route.request().headers()['idempotency-key']).toBeTruthy();
    members = members.map(member => member.id === '2' ? { ...member, roleIds: ['11', '10'], version: 2 } : member);
    await route.fulfill(response(members[1]));
  });
  await page.goto('/');
  await page.getByRole('button', { name: '登录系统' }).click();
  await page.goto('/system/users');
  await expect(page.getByText('用户管理', { exact: true }).first()).toBeVisible();
  const adminRow = page.getByRole('row').filter({ hasText: 'admin' });
  await expect(adminRow.getByRole('button', { name: '停用' })).toBeDisabled();
  await page.getByRole('row').filter({ hasText: 'member' }).getByRole('button', { name: '停用' }).click();
  await page.getByRole('button', { name: '确定' }).click();
  await expect(page.getByRole('row').filter({ hasText: 'member' }).getByRole('button', { name: '恢复' })).toBeVisible();
  await page.getByRole('row').filter({ hasText: 'member' }).getByRole('button', { name: '编辑角色' }).click();
  await page.getByRole('dialog').locator('.el-select').click();
  await page.getByRole('option', { name: '租户管理员' }).click();
  await page.keyboard.press('Escape');
  await page.getByRole('dialog').getByRole('button', { name: '保存角色' }).click();
  await expect(page.getByRole('row').filter({ hasText: 'member' })).toContainText('租户管理员');
  await page.getByRole('button', { name: '新建用户' }).click();
  await page.getByRole('textbox', { name: '用户名' }).fill('newmember');
  await page.getByRole('textbox', { name: '姓名' }).fill('新成员');
  await page.getByRole('textbox', { name: '初始密码' }).fill('Initial#Pass123');
  await page.getByRole('combobox', { name: '主组织' }).click();
  await page.getByText('总部', { exact: true }).last().click();
  await page.getByRole('dialog').locator('.el-form-item').filter({ hasText: '角色' }).locator('.el-select').click();
  await page.getByText('普通成员', { exact: true }).last().click();
  await page.keyboard.press('Escape');
  await page.getByRole('dialog').getByRole('button', { name: '创建' }).click();
  await expect.poll(() => createdBody).toMatchObject({ username: 'newmember', primaryOrganizationId: '100', roleIds: ['11'] });
  await expect(page.getByRole('row').filter({ hasText: 'newmember' })).toBeVisible();
});
