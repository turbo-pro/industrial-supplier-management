// Playwright CLI run-code fixture. Mocked API UI checks, NOT backend E2E.
async page => {
  let registered = false;
  let logoutFails = true;
  const user = { accountId: '101', tenantId: '201', supplierId: '301', username: 'supplier.demo', displayName: '供应商联系人' };
  await page.route('**/api/portal/auth/**', async route => {
    const request = route.request();
    const action = new URL(request.url()).pathname.split('/').pop();
    const body = request.postData() ? request.postDataJSON() : null;
    const reply = (status, data) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ code: status === 200 ? 'SUCCESS' : 'ERROR', data }) });
    if (action === 'register') {
      if (Object.keys(body).sort().join(',') !== 'invitationToken,password,username') throw new Error('Registration binding fields leaked');
      registered = true;
      return reply(200, { accountId: '101', tenantId: '201', supplierId: '301', username: body.username });
    }
    if (action === 'login') {
      if (!registered || body.password !== 'Supplier#Pass123') return reply(401, null);
      return reply(200, { user, accessToken: 'p'.repeat(43), expiresAt: '2026-10-22T01:00:00Z' });
    }
    if (request.headers().authorization !== `Bearer ${'p'.repeat(43)}`) throw new Error('Missing portal token');
    if (action === 'me') return reply(200, user);
    if (action === 'logout') { if (logoutFails) { logoutFails = false; return reply(500, null); } return reply(200, null); }
    throw new Error('Unexpected portal endpoint');
  });
  await page.getByRole('button', { name: '有邀请凭证？注册账号' }).click();
  await page.locator('body').ariaSnapshot();
  await page.getByRole('textbox', { name: '邀请凭证' }).fill('i'.repeat(43));
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill('supplier.demo');
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('Supplier#Pass123');
  await page.getByRole('textbox', { name: '确认密码', exact: true }).fill('wrong');
  await page.getByRole('button', { name: '创建门户账号' }).click();
  await page.getByRole('alert').waitFor();
  if (!(await page.getByRole('alert').evaluate(el => el === document.activeElement))) throw new Error('Error summary not focused');
  await page.getByRole('textbox', { name: '确认密码', exact: true }).fill('Supplier#Pass123');
  await page.getByRole('button', { name: '创建门户账号' }).click();
  await page.getByRole('status').filter({ hasText: '注册成功' }).waitFor();
  await page.locator('body').ariaSnapshot();
  await page.getByRole('textbox', { name: '租户编码' }).fill('TENANT-DEMO');
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('Wrong#Password123');
  await page.getByRole('button', { name: '登录门户' }).click();
  await page.getByRole('alert').filter({ hasText: '凭证无效' }).waitFor();
  if (await page.getByRole('textbox', { name: '密码', exact: true }).inputValue()) throw new Error('Password retained');
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('Supplier#Pass123');
  await page.getByRole('button', { name: '登录门户' }).click();
  await page.getByRole('heading', { name: '当前登录身份' }).waitFor();
  await page.locator('body').ariaSnapshot();
  await page.getByRole('button', { name: '重新确认身份' }).click();
  await page.getByRole('status').filter({ hasText: '重新确认' }).waitFor();
  await page.getByRole('button', { name: '退出当前会话' }).click();
  await page.getByRole('alert').filter({ hasText: '退出未确认' }).waitFor();
  await page.getByRole('heading', { name: '当前登录身份' }).waitFor();
  await page.getByRole('button', { name: '退出当前会话' }).click();
  await page.getByRole('heading', { name: '账号登录' }).waitFor();
  await page.setViewportSize({ width: 375, height: 812 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Mobile overflow');
  await page.screenshot({ path: 'output/playwright/portal-auth-mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({ path: 'output/playwright/portal-auth-desktop.png', fullPage: true });
  if (await page.evaluate(() => Object.keys(sessionStorage).length || Object.keys(localStorage).length)) throw new Error('Unexpected browser credential storage');
  await page.reload();
  await page.getByRole('heading', { name: '账号登录' }).waitFor();
}
