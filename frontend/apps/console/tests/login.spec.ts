import { expect, test } from '@playwright/test';

test('platform operator logs in through the isolated Console endpoint', async ({ page }) => {
  await page.route('**/api/console/auth/me', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: {
    id: '1', username: 'platform-admin', displayName: '平台管理员', passwordChangeRequired: false,
    passwordChangeRecommended: false, permissions: ['platform:tenant:view'],
  } }) }));
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
  await page.route('**/api/console/auth/me', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: {
    id: '1', username: 'platform-admin', displayName: '平台管理员', passwordChangeRequired: false,
    passwordChangeRecommended: false, permissions: [],
  } }) }));
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

test('platform administrator creates and disables a Console account', async ({ page }) => {
  const permissions = ['platform:tenant:view', 'platform:user:view', 'platform:user:manage'];
  const admin = { id: '1', username: 'platform-admin', displayName: '平台管理员',
    passwordChangeRequired: false, passwordChangeRecommended: false, permissions };
  let accounts = [{ id: '1', username: 'platform-admin', displayName: '平台管理员', status: 'ACTIVE',
    passwordChangeRequired: false, manualLocked: false, manualLockReason: null as string | null,
    automaticLockedUntil: null as string | null, version: 0, roleCodes: ['PLATFORM_ADMIN'] }];
  const ok = (data: unknown) => ({ status: 200, contentType: 'application/json',
    body: JSON.stringify({ success: true, data }) });
  await page.route('**/api/console/auth/login', route => route.fulfill(ok({
    accessToken: 'console-access', refreshToken: 'console-refresh', expiresIn: 900, user: admin,
  })));
  await page.route('**/api/console/auth/me', route => route.fulfill(ok(admin)));
  await page.route('**/api/console/packages', route => route.fulfill(ok([])));
  await page.route('**/api/console/tenants', route => route.fulfill(ok([])));
  await page.route('**/api/console/users', async route => {
    if (route.request().method() === 'GET') return route.fulfill(ok(accounts));
    expect(route.request().headers()['idempotency-key']).toBeTruthy();
    expect(route.request().postDataJSON()).toEqual({ username: 'platform-support', displayName: '支持人员',
      initialPassword: 'Temporary#Pass123', roleCode: 'PLATFORM_SUPPORT' });
    accounts = [...accounts, { id: '2', username: 'platform-support', displayName: '支持人员', status: 'ACTIVE',
      passwordChangeRequired: true, manualLocked: false, manualLockReason: null,
      automaticLockedUntil: null, version: 0, roleCodes: ['PLATFORM_SUPPORT'] }];
    return route.fulfill(ok(accounts[1]));
  });
  await page.route('**/api/console/users/2/status', async route => {
    expect(route.request().headers()['idempotency-key']).toBeTruthy();
    expect(route.request().postDataJSON()).toEqual({ status: 'DISABLED', version: 0 });
    accounts[1] = { ...accounts[1], status: 'DISABLED', version: 1 };
    await route.fulfill(ok(accounts[1]));
  });
  await page.route('**/api/console/users/2/role', async route => {
    expect(route.request().headers()['idempotency-key']).toBeTruthy();
    expect(route.request().postDataJSON()).toEqual({ roleCode: 'PLATFORM_ADMIN', version: 1 });
    accounts[1] = { ...accounts[1], roleCodes: ['PLATFORM_ADMIN'], version: 2 };
    await route.fulfill(ok(accounts[1]));
  });
  await page.route('**/api/console/users/2/role-impact?**', async route => {
    const url = new URL(route.request().url());
    expect(url.searchParams.get('roleCode')).toBe('PLATFORM_ADMIN');
    expect(url.searchParams.get('version')).toBe('1');
    await route.fulfill(ok({ userId: '2', username: 'platform-support', version: 1,
      currentRoleCodes: ['PLATFORM_SUPPORT'], proposedRoleCode: 'PLATFORM_ADMIN',
      addedPermissions: ['platform:user:manage'], removedPermissions: [], canApply: true, blockers: [] }));
  });
  await page.route('**/api/console/users/2/password-reset', async route => {
    expect(route.request().headers()['idempotency-key']).toBeTruthy();
    expect(route.request().postDataJSON()).toEqual({ temporaryPassword: 'NewTemp#Pass123', version: 2 });
    accounts[1] = { ...accounts[1], passwordChangeRequired: true, version: 3 };
    await route.fulfill(ok(accounts[1]));
  });
  await page.route('**/api/console/users/2/login-lock', async route => {
    expect(route.request().headers()['idempotency-key']).toBeTruthy();
    const body = route.request().postDataJSON();
    expect(body).toMatchObject({ locked: !accounts[1].manualLocked, version: accounts[1].version });
    expect(body.reason).toBeTruthy();
    accounts[1] = { ...accounts[1], manualLocked: body.locked, manualLockReason: body.locked ? body.reason : null,
      version: accounts[1].version + 1 };
    await route.fulfill(ok(accounts[1]));
  });
  await page.goto('/');
  await page.getByLabel('平台用户名').fill('platform-admin');
  await page.getByLabel('密码', { exact: true }).fill('Console#Pass123');
  await page.getByRole('button', { name: '进入 Console' }).click();
  await expect(page.getByRole('heading', { name: '平台账号' })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'platform-admin' }).getByRole('button', { name: '停用' })).toBeDisabled();
  await page.getByLabel('平台用户名').last().fill('platform-support');
  await page.getByLabel('显示名称').fill('支持人员');
  await page.getByLabel('初始密码').fill('Temporary#Pass123');
  await page.getByRole('button', { name: '创建平台账号' }).click();
  await expect(page.getByRole('row').filter({ hasText: 'platform-support' })).toBeVisible();
  await page.getByRole('row').filter({ hasText: 'platform-support' }).getByRole('button', { name: '停用' }).click();
  await expect(page.getByRole('row').filter({ hasText: 'platform-support' }).getByRole('button', { name: '恢复' })).toBeVisible();
  await page.getByRole('row').filter({ hasText: 'platform-support' }).getByRole('combobox', { name: 'platform-support 平台角色' }).selectOption('PLATFORM_ADMIN');
  await page.getByRole('row').filter({ hasText: 'platform-support' }).getByRole('button', { name: '预览影响' }).click();
  await expect(page.getByRole('region', { name: '角色影响预览' })).toContainText('platform:user:manage');
  await page.getByRole('button', { name: '确认保存角色' }).click();
  await expect(page.getByRole('row').filter({ hasText: 'platform-support' })).toContainText('运营管理员');
  await page.getByRole('row').filter({ hasText: 'platform-support' }).getByRole('button', { name: '重置密码' }).click();
  await page.getByLabel('临时密码').fill('NewTemp#Pass123');
  await page.getByRole('button', { name: '确认重置' }).click();
  await expect(page.getByRole('status')).toContainText('临时密码已设置');
  await expect(page.getByRole('row').filter({ hasText: 'platform-admin' }).getByRole('button', { name: '锁定登录' })).toBeDisabled();
  await page.getByRole('row').filter({ hasText: 'platform-support' }).getByRole('button', { name: '锁定登录' }).click();
  await page.getByLabel('操作原因').fill('安全调查');
  await page.getByRole('button', { name: '确认', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: 'platform-support' })).toContainText('人工锁定');
  await page.getByRole('row').filter({ hasText: 'platform-support' }).getByRole('button', { name: '解除锁定' }).click();
  await page.getByLabel('操作原因').fill('调查结束');
  await page.getByRole('button', { name: '确认', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: 'platform-support' })).not.toContainText('人工锁定');
});
