import { expect, test } from '@playwright/test';

test('tenant role page creates a role and replaces grants with the displayed version', async ({ page }) => {
  const roles = [{ id: '42', code: 'QUALITY_REVIEWER', name: '质量审核', builtIn: false, status: 'ACTIVE', version: 0 }];
  const grants = new Map<string, { permissionCodes: string[]; menuCodes: string[]; dataScopes: object[] }>([
    ['42', { permissionCodes: ['iam:role:view'], menuCodes: ['ROLE_MANAGEMENT'],
      dataScopes: [{ resourceCode: 'supplier:master', scopeType: 'NONE', organizationIds: [] }] }],
  ]);
  let savedGrant: any;
  const ok = (data: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data }) });
  await page.addInitScript(() => {
    sessionStorage.setItem('ism.admin.accessToken', 'role-token');
    sessionStorage.setItem('ism.admin.actorId', '1');
  });
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    if (path === '/api/organizations/tree') return route.fulfill(ok([]));
    if (path === '/api/organizations/current') return route.fulfill(ok({ id: '100', code: 'HQ', name: '总部', type: 'HEADQUARTERS' }));
    if (path === '/api/navigation/menus') return route.fulfill(ok([{ id: '1', code: 'SYSTEM', name: '系统管理', route: '/system', component: 'Layout',
      children: [{ id: '2', code: 'ROLE_MANAGEMENT', name: '角色权限', route: '/system/roles', component: 'RolePage', children: [] }] }]));
    if (path === '/api/configuration/branding') return route.fulfill(ok({ systemName: '工业供应商管理系统', logoUrl: '', faviconUrl: '', footerText: '' }));
    if (path === '/api/access/role-grant-catalog') return route.fulfill(ok({ permissionCodes: ['iam:role:view', 'iam:role:manage'],
      menuCodes: ['ROLE_MANAGEMENT'], resourceCodes: ['supplier:master'], canManage: true }));
    if (path === '/api/access/roles' && method === 'GET') return route.fulfill(ok(roles));
    if (path === '/api/access/roles' && method === 'POST') {
      expect(route.request().headers()['idempotency-key']).toBeTruthy();
      const body = route.request().postDataJSON();
      const role = { id: '43', code: body.code, name: body.name, builtIn: false, status: 'ACTIVE', version: 0 };
      roles.push(role);
      grants.set(role.id, { permissionCodes: [], menuCodes: [], dataScopes: [] });
      return route.fulfill(ok(role));
    }
    const detail = path.match(/^\/api\/access\/roles\/(\d+)\/grants$/);
    if (detail && method === 'GET') return route.fulfill(ok({ role: roles.find(role => role.id === detail[1]), ...grants.get(detail[1]) }));
    if (detail && method === 'PUT') {
      expect(route.request().headers()['idempotency-key']).toBeTruthy();
      savedGrant = route.request().postDataJSON();
      const role = roles.find(role => role.id === detail[1])!;
      expect(savedGrant.version).toBe(role.version);
      role.version++;
      grants.set(role.id, savedGrant);
      return route.fulfill(ok(role));
    }
    return route.fulfill({ status: 404, body: '{}' });
  });
  await page.goto('/system/roles');
  await expect(page.getByRole('heading', { name: '角色权限' })).toBeVisible();
  await expect(page.getByText('质量审核').first()).toBeVisible();
  await page.getByRole('button', { name: '新建角色' }).click();
  await page.getByRole('dialog', { name: '新建租户角色' }).getByPlaceholder('例如 QUALITY_REVIEWER').fill('SITE_AUDITOR');
  await page.getByRole('dialog', { name: '新建租户角色' }).getByRole('textbox', { name: '角色名称' }).fill('场站审核');
  await page.getByRole('dialog', { name: '新建租户角色' }).getByRole('button', { name: '创建' }).click();
  await expect(page.getByText('场站审核').first()).toBeVisible();
  await page.getByRole('button', { name: '新增资源' }).click();
  await page.locator('.scope-row').first().getByRole('combobox').first().click();
  await page.getByRole('option', { name: 'supplier:master' }).click();
  await page.getByRole('button', { name: '保存授权' }).click();
  await page.getByRole('button', { name: '确定' }).click();
  await expect.poll(() => savedGrant?.dataScopes?.length).toBe(1);
  expect(savedGrant).toMatchObject({ version: 0, permissionCodes: [], menuCodes: [],
    dataScopes: [{ resourceCode: 'supplier:master', scopeType: 'NONE', organizationIds: [] }] });
  await expect(page.getByText('场站审核').first()).toBeVisible();
});

test('view-only role account cannot open mutation controls', async ({ page }) => {
  const ok = (data: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data }) });
  await page.addInitScript(() => sessionStorage.setItem('ism.admin.accessToken', 'viewer-token'));
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/access/roles') return route.fulfill(ok([
      { id: '42', code: 'AUDITOR', name: '只读审核', builtIn: false, status: 'ACTIVE', version: 1 },
    ]));
    if (path === '/api/access/role-grant-catalog') return route.fulfill(ok({
      permissionCodes: ['iam:role:view'], menuCodes: ['ROLE_MANAGEMENT'], resourceCodes: ['supplier:master'], canManage: false,
    }));
    if (path === '/api/access/roles/42/grants') return route.fulfill(ok({
      role: { id: '42', code: 'AUDITOR', name: '只读审核', builtIn: false, status: 'ACTIVE', version: 1 },
      permissionCodes: ['iam:role:view'], menuCodes: ['ROLE_MANAGEMENT'],
      dataScopes: [{ resourceCode: 'supplier:master', scopeType: 'NONE', organizationIds: [] }],
    }));
    if (path === '/api/organizations/current') return route.fulfill(ok({ id: '100', code: 'HQ', name: '总部', type: 'HEADQUARTERS' }));
    if (path === '/api/configuration/branding') return route.fulfill(ok({ systemName: '工业供应商管理系统', logoUrl: '', faviconUrl: '', footerText: '' }));
    return route.fulfill(ok([]));
  });
  await page.goto('/system/roles');
  await expect(page.getByText('当前账号仅可查看角色授权')).toBeVisible();
  await expect(page.getByRole('button', { name: '新建角色' })).toBeDisabled();
  await page.getByRole('cell', { name: '只读审核' }).click();
  await expect(page.getByRole('button', { name: '保存名称' })).toBeDisabled();
  await expect(page.getByRole('button', { name: '新增资源' })).toBeDisabled();
  await expect(page.getByRole('button', { name: '保存授权' })).toBeDisabled();
});
