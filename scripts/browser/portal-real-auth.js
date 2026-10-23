async page => {
  await page.getByRole('button', { name: '有邀请凭证？注册账号' }).click();
  await page.locator('body').ariaSnapshot();
  await page.getByRole('textbox', { name: '邀请凭证', exact: true }).fill('__INVITATION__');
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill('browser.user');
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('Portal#Pass123');
  await page.getByRole('textbox', { name: '确认密码', exact: true }).fill('Portal#Pass123');
  await page.getByRole('button', { name: '创建门户账号' }).click();
  await page.getByRole('status').filter({ hasText: '注册成功' }).waitFor();
  await page.locator('body').ariaSnapshot();
  await page.getByRole('textbox', { name: '租户编码' }).fill('PORTAL-BROWSER');
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('Wrong#Password123');
  await page.getByRole('button', { name: '登录门户' }).click();
  await page.getByRole('alert').filter({ hasText: '凭证无效' }).waitFor();
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('Portal#Pass123');
  const pending = page.waitForResponse(r => r.url().endsWith('/portal/auth/login') && r.status() === 200);
  await page.getByRole('button', { name: '登录门户' }).click();
  const session = (await (await pending).json()).data;
  if (session.user.tenantId !== '21001' || session.user.supplierId !== '21005') throw new Error('Wrong identity binding');
  await page.getByRole('heading', { name: '当前登录身份' }).waitFor();
  await page.getByRole('heading', { name: '本企业档案' }).waitFor();
  await page.getByText('浏览器供应商', { exact: true }).waitFor();
  await page.getByText('化工制造', { exact: true }).waitFor();
  await page.getByText('化工原料', { exact: false }).waitFor();
  if (await page.getByText('PRIVATE_', { exact: false }).count()) throw new Error('Private supplier fields leaked into the portal');
  const ownProfile = await page.request.get(new URL('/api/portal/supplier-profile?tenantId=999999&supplierId=21020',page.url()).href, {
    headers: { Authorization: `Bearer ${session.accessToken}`, 'X-Tenant-Id': '999999' },
  });
  const profile = (await ownProfile.json()).data;
  if (ownProfile.status() !== 200 || profile.supplier.id !== '21005' || profile.purchaseCategories.length !== 1
      || JSON.stringify(profile).includes('PRIVATE_') || ownProfile.headers()['cache-control'] !== 'no-store') throw new Error('Portal profile binding or field allowlist failed');
  await page.route('**/api/portal/supplier-profile', route => route.abort('failed'), { times: 1 });
  await page.getByRole('button', { name: '刷新企业档案' }).click();
  await page.getByRole('alert').filter({ hasText: '网络异常' }).waitFor();
  if (await page.getByText('浏览器供应商', { exact: true }).count()) throw new Error('Failed refresh retained an unconfirmed profile');
  await page.getByRole('button', { name: '刷新企业档案' }).click();
  await page.getByText('浏览器供应商', { exact: true }).waitFor();
  await page.setViewportSize({ width: 375, height: 812 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Profile mobile overflow');
  await page.screenshot({ path: 'output/playwright/portal-real-profile-mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.screenshot({ path: 'output/playwright/portal-real-profile-desktop.png', fullPage: true });
  await page.locator('body').ariaSnapshot();
  await page.getByRole('button', { name: '重新确认身份' }).click();
  await page.getByRole('status').filter({ hasText: '重新确认' }).waitFor();
  const spoofed = await page.request.get(new URL('/api/portal/auth/me', page.url()).href, {
    headers: { Authorization: `Bearer ${session.accessToken}`, 'X-Tenant-Id': '999999' },
  });
  const bound = (await spoofed.json()).data;
  if (spoofed.status() !== 200 || bound.tenantId !== '21001' || bound.supplierId !== '21005') throw new Error('Client tenant header changed binding');
  for (const path of ['/api/suppliers', '/api/console/tenants']) {
    const response = await page.request.get(new URL(path, page.url()).href, { headers: { Authorization: `Bearer ${session.accessToken}` } });
    if (response.status() !== 401) throw new Error('Portal token crossed authentication realms');
  }
  await page.getByRole('button', { name: '退出当前会话' }).click();
  await page.getByRole('status').filter({ hasText: '已退出当前会话' }).waitFor();
  const replay = await page.request.get(new URL('/api/portal/auth/me', page.url()).href, { headers: { Authorization: `Bearer ${session.accessToken}` } });
  if (replay.status() !== 401) throw new Error('Revoked token replay accepted');
  const profileReplay = await page.request.get(new URL('/api/portal/supplier-profile', page.url()).href, { headers: { Authorization: `Bearer ${session.accessToken}` } });
  if (profileReplay.status() !== 401 || await page.getByRole('heading', { name: '本企业档案' }).count()) throw new Error('Profile remained accessible after logout');
  if (await page.evaluate(() => Object.keys(localStorage).length + Object.keys(sessionStorage).length)) throw new Error('Credential storage leak');
  await page.setViewportSize({ width: 375, height: 812 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Mobile overflow');
  await page.screenshot({ path: 'output/playwright/portal-real-auth.png', fullPage: true });
}
