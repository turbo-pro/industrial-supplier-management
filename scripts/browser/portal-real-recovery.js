async page => {
  const admin = await page.context().newPage();
  const loginEmployee = async (tab, username) => {
    await tab.goto('__ADMIN_URL__/'); await tab.locator('body').ariaSnapshot();
    await tab.getByRole('textbox', { name: '租户编码', exact: true }).fill('PORTAL-BROWSER');
    await tab.getByRole('textbox', { name: '用户名', exact: true }).fill(username);
    await tab.getByRole('textbox', { name: '密码', exact: true }).fill('Employee#Pass123');
    await tab.getByRole('button', { name: '登录系统', exact: true }).click();
    await tab.getByRole('button', { name: '登录系统', exact: true }).waitFor({ state: 'hidden' });
    await tab.goto('__ADMIN_URL__/suppliers/master?supplierId=21005');
    const panel = tab.getByRole('region', { name: '供应商门户账号', exact: true });
    await panel.waitFor(); await panel.getByRole('button', { name: '门户账号管理', exact: true }).click();
    await panel.getByRole('cell', { name: 'browser.user', exact: true }).waitFor(); return panel;
  };
  const api = path => new URL('/api/portal/auth/' + path, page.url()).href;
  try {
    await page.getByRole('textbox', { name: '密码', exact: true }).fill('Portal#Pass123');
    const pending = page.waitForResponse(r => r.url().endsWith('/portal/auth/login') && r.status() === 200);
    await page.getByRole('button', { name: '登录门户' }).click();
    const old = (await (await pending).json()).data.accessToken;
    await page.getByRole('heading', { name: '当前登录身份' }).waitFor();
    const extra = await page.request.post(api('login'), { data: { tenantCode: 'PORTAL-BROWSER', username: 'browser.user', password: 'Portal#Pass123' } });
    if (extra.status() !== 200) throw new Error('Second portal session failed');
    const other = (await extra.json()).data.accessToken;
    const panel = await loginEmployee(admin, 'browser-admin');
    await panel.getByRole('button', { name: '恢复门户密码', exact: true }).click();
    const recovery = admin.getByRole('region', { name: '门户密码恢复', exact: true });
    await recovery.getByText('尚未签发恢复凭证。', { exact: true }).waitFor();
    await recovery.getByRole('button', { name: '确认签发或替换恢复凭证', exact: true }).click();
    await recovery.getByRole('alert').filter({ hasText: '必须通过可信渠道核实' }).waitFor();
    await recovery.getByRole('textbox', { name: '密码恢复操作原因' }).fill('真实隔离验收：通过合同联系人核实身份');
    await recovery.getByText('已通过可信渠道核实账号持有人身份', { exact: true }).click();
    if (!await recovery.getByRole('checkbox', { name: '已通过可信渠道核实身份' }).isChecked()) throw new Error('Identity verification was not confirmed');
    const issue = async () => {
      const response = admin.waitForResponse(r => r.url().endsWith('/password-recovery') && r.request().method() === 'POST');
      const metadata = admin.waitForResponse(r => r.url().endsWith('/password-recovery') && r.request().method() === 'GET');
      await recovery.getByRole('button', { name: '确认签发或替换恢复凭证', exact: true }).click();
      const result = await response; if (result.status() !== 200) throw new Error('Recovery issuance failed');
      if ((await metadata).status() !== 200) throw new Error('Recovery metadata reload failed');
      await recovery.getByRole('status').filter({ hasText: '仅本次响应可见' }).waitFor();
      return (await result.json()).data.token;
    };
    const first = await issue(); const second = await issue();
    const rejected = await page.request.post(api('reset-password'), { data: { recoveryToken: first, newPassword: 'Recovered#Pass456' } });
    if (rejected.status() !== 422) throw new Error('Replaced recovery credential accepted');
    const [cancelled] = await Promise.all([admin.waitForResponse(r => r.url().endsWith('/password-recovery/revoke')),recovery.getByRole('button', { name: '确认撤销恢复凭证', exact: true }).click()]);
    if (cancelled.status() !== 200) throw new Error('Recovery cancellation failed');
    await recovery.waitFor({ state: 'hidden' });
    if ((await page.request.post(api('reset-password'), { data: { recoveryToken: second, newPassword: 'Recovered#Pass456' } })).status() !== 422) throw new Error('Cancelled recovery credential accepted');
    await panel.getByRole('button', { name: '恢复门户密码', exact: true }).click();
    await recovery.getByRole('textbox', { name: '密码恢复操作原因' }).fill('重新核实本人后恢复');
    await recovery.getByText('已通过可信渠道核实账号持有人身份', { exact: true }).click();
    if (!await recovery.getByRole('checkbox', { name: '已通过可信渠道核实身份' }).isChecked()) throw new Error('Identity verification was not confirmed');
    const token = await issue();
    await admin.setViewportSize({ width: 375, height: 812 });
    await admin.screenshot({ path: 'output/playwright/portal-real-recovery-admin.png', fullPage: true });
    await recovery.getByRole('button', { name: '关闭密码恢复', exact: true }).click();
    await page.getByRole('button', { name: '重新确认身份' }).click();
    await page.getByRole('status').filter({ hasText: '重新确认' }).waitFor(); // Issuance alone did not revoke.
    await page.reload(); // Drop only the in-memory token; leave the server session for recovery to revoke.
    await page.getByRole('button', { name: '忘记密码？使用恢复凭证', exact: true }).click();
    await page.locator('body').ariaSnapshot();
    await page.getByRole('textbox', { name: '恢复凭证', exact: true }).fill(token);
    await page.getByRole('textbox', { name: '密码', exact: true }).fill('Recovered#Pass456');
    await page.getByRole('textbox', { name: '确认密码', exact: true }).fill('Recovered#Pass456');
    await page.getByRole('button', { name: '设置新密码并撤销旧会话', exact: true }).click();
    await page.getByRole('status').filter({ hasText: '密码恢复成功' }).waitFor();
    for (const bearer of [old, other]) if ((await page.request.get(api('me'), { headers: { Authorization: `Bearer ${bearer}` } })).status() !== 401) throw new Error('Recovery failed to revoke a session');
    if ((await page.request.post(api('reset-password'), { data: { recoveryToken: token, newPassword: 'Another#Pass789' } })).status() !== 422) throw new Error('Recovery replay accepted');
    await page.getByRole('textbox', { name: '租户编码' }).fill('PORTAL-BROWSER');
    await page.getByRole('textbox', { name: '用户名' }).fill('browser.user');
    await page.getByRole('textbox', { name: '密码', exact: true }).fill('Recovered#Pass456');
    await page.getByRole('button', { name: '登录门户' }).click();
    await page.getByRole('heading', { name: '当前登录身份' }).waitFor();
    await page.getByRole('button', { name: '退出当前会话' }).click();
    await page.getByRole('status').filter({ hasText: '已退出当前会话' }).waitFor();
    const reader = await page.context().newPage();
    try {
      const readonly = await loginEmployee(reader, 'browser-reader');
      if (await readonly.getByRole('button', { name: '恢复门户密码', exact: true }).count()) throw new Error('Recovery control exposed to reader');
      const bearer = await reader.evaluate(() => sessionStorage.getItem('ism.admin.accessToken'));
      const accounts = await reader.request.get('__ADMIN_URL__/api/suppliers/21005/portal-accounts', { headers: { Authorization: `Bearer ${bearer}` } });
      const account = (await accounts.json()).data.accounts.items[0];
      const denied = await reader.request.post(`__ADMIN_URL__/api/suppliers/21005/portal-accounts/${account.id}/password-recovery`, { headers: { Authorization: `Bearer ${bearer}` }, data: { version: account.version, reason: '只读拒绝验证', identityVerified: true } });
      if (denied.status() !== 403) throw new Error('Reader recovery mutation accepted');
    } finally { await reader.close(); }
    await page.setViewportSize({ width: 375, height: 812 });
    if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Portal recovery overflow');
    await page.screenshot({ path: 'output/playwright/portal-real-recovery-finished.png', fullPage: true });
  } catch(error) { await admin.screenshot({path:'output/playwright/portal-real-recovery-failure.png',fullPage:true});throw error; }
  finally { await admin.close(); }
}
