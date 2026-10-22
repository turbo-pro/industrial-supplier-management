async page => {
  const api = path => new URL('/api/portal/auth/' + path, page.url()).href;
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('Portal#Pass123');
  const loggingIn = page.waitForResponse(r => r.url().endsWith('/portal/auth/login') && r.status() === 200);
  await page.getByRole('button', { name: '登录门户' }).click();
  const first = (await (await loggingIn).json()).data.accessToken;
  await page.getByRole('heading', { name: '当前登录身份' }).waitFor();
  const extra = await page.request.post(api('login'), { data: { tenantCode: 'PORTAL-BROWSER', username: 'browser.user', password: 'Portal#Pass123' } });
  if (extra.status() !== 200) throw new Error('Second session login failed');
  const second = (await extra.json()).data.accessToken;
  await page.getByRole('button', { name: '修改我的密码' }).click();
  await page.locator('body').ariaSnapshot();
  const fill = async (old, next, confirmation = next) => {
    await page.getByRole('textbox', { name: '原密码', exact: true }).fill(old);
    await page.getByRole('textbox', { name: '新密码', exact: true }).fill(next);
    await page.getByRole('textbox', { name: '确认新密码', exact: true }).fill(confirmation);
  };
  await fill('Portal#Pass123', 'Changed#Pass456', 'Different#Pass789');
  await page.getByRole('button', { name: '确认修改并退出所有会话' }).click();
  await page.getByRole('alert').filter({ hasText: '两次新密码不一致' }).waitFor();
  await fill('Incorrect#Pass123', 'Changed#Pass456');
  const rejected = page.waitForResponse(r => r.url().endsWith('/portal/auth/password'));
  await page.getByRole('button', { name: '确认修改并退出所有会话' }).click();
  if ((await rejected).status() !== 422) throw new Error('Incorrect current password accepted');
  await page.getByRole('alert').filter({ hasText: '原密码不正确' }).waitFor();
  if (await page.getByRole('textbox', { name: '原密码', exact: true }).inputValue()) throw new Error('Password not cleared after submission');
  await page.setViewportSize({ width: 375, height: 812 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Password form mobile overflow');
  await page.screenshot({ path: 'output/playwright/portal-real-password-form.png', fullPage: true });
  await fill('Portal#Pass123', 'Changed#Pass456');
  const changed = page.waitForResponse(r => r.url().endsWith('/portal/auth/password'));
  await page.getByRole('button', { name: '确认修改并退出所有会话' }).click();
  if ((await changed).status() !== 200) throw new Error('Password change failed');
  await page.getByRole('status').filter({ hasText: '所有设备的旧会话均已撤销' }).waitFor();
  for (const token of [first, second]) {
    const replay = await page.request.get(api('me'), { headers: { Authorization: `Bearer ${token}` } });
    if (replay.status() !== 401) throw new Error('Old session survived password change');
  }
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('Portal#Pass123');
  await page.getByRole('button', { name: '登录门户' }).click();
  await page.getByRole('alert').filter({ hasText: '凭证无效' }).waitFor();
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('Changed#Pass456');
  await page.getByRole('button', { name: '登录门户' }).click();
  await page.getByRole('heading', { name: '当前登录身份' }).waitFor();
  await page.getByRole('button', { name: '退出当前会话' }).click();
  await page.getByRole('status').filter({ hasText: '已退出当前会话' }).waitFor();
  if (await page.evaluate(() => Object.keys(localStorage).length + Object.keys(sessionStorage).length)) throw new Error('Credential storage leak');
  await page.screenshot({ path: 'output/playwright/portal-real-password-finished.png', fullPage: true });
}
