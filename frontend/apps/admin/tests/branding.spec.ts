import { expect, test } from '@playwright/test';

test('tenant branding is read, edited and reset without leaking after logout', async ({ page }) => {
  const settings = new Map([
    ['branding.systemName', { key: 'branding.systemName', valueType: 'STRING', value: '甲租户平台', version: 0 }],
    ['branding.logoUrl', { key: 'branding.logoUrl', valueType: 'URL', value: '/tenant-logo.png', version: 0 }],
    ['branding.faviconUrl', { key: 'branding.faviconUrl', valueType: 'URL', value: '/tenant-icon.png', version: 0 }],
    ['branding.footerText', { key: 'branding.footerText', valueType: 'STRING', value: '甲租户专用', version: 0 }],
  ]);
  await page.route('**/tenant-logo.png', route => route.fulfill({ status: 200, contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg"/>' }));
  await page.route('**/tenant-icon.png', route => route.fulfill({ status: 200, contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg"/>' }));
  await page.route('**/api/auth/login', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
    success: true, data: { accessToken: 'brand-token', refreshToken: 'r', expiresIn: 900,
      user: { id: '1', tenantId: '10', username: 'admin', displayName: '管理员',
        passwordChangeRequired: false, passwordChangeRecommended: false } },
  }) }));
  await page.route('**/api/organizations/tree', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) }));
  await page.route('**/api/organizations/current', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: { id: '100', code: 'HQ', name: '总部', type: 'HEADQUARTERS' } }) }));
  await page.route('**/api/navigation/menus', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) }));
  await page.route('**/api/configuration/branding', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
    success: true, data: {
      systemName: settings.get('branding.systemName')?.value,
      logoUrl: settings.get('branding.logoUrl')?.value,
      faviconUrl: settings.get('branding.faviconUrl')?.value,
      footerText: settings.get('branding.footerText')?.value,
    },
  }) }));
  await page.route('**/api/configuration/settings', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [...settings.values()] }) }));
  await page.route('**/api/configuration/settings/branding.systemName', async route => {
    const body = route.request().postDataJSON();
    expect(body).toEqual({ value: '乙租户平台', version: 0 });
    expect(route.request().headers()['idempotency-key']).toBeTruthy();
    const updated = { key: 'branding.systemName', valueType: 'STRING', value: body.value, version: 1 };
    settings.set(updated.key, updated);
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: updated }) });
  });
  await page.goto('/');
  await page.getByRole('button', { name: '登录系统' }).click();
  await expect(page.locator('.sidebar .logo')).toContainText('甲租户平台');
  await expect(page).toHaveTitle('甲租户平台');
  await expect(page.locator('#ism-tenant-favicon')).toHaveAttribute('href', /tenant-icon\.png/);
  await expect(page.locator('.sidebar .logo img')).toHaveAttribute('src', '/tenant-logo.png');
  await page.goto('/system/settings');
  await page.getByRole('textbox', { name: '系统名称' }).fill('乙租户平台');
  await page.locator('form').first().getByRole('button', { name: '保存' }).first().click();
  await expect(page).toHaveTitle('乙租户平台');
  await expect(page.locator('.sidebar .logo')).toContainText('乙租户平台');
  await expect(page.getByText('甲租户专用')).toBeVisible();
  await page.getByRole('button', { name: '退出' }).click();
  await expect(page).toHaveTitle('工业供应商管理系统');
  await expect(page.locator('#ism-tenant-favicon')).toHaveCount(0);
});
