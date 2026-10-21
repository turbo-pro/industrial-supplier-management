// Run after invitation-ui-fixture.js on the admin Vite app at port 4176. Mocked API only.
async page => {
  let account = { id: '901', username: 'portal.contact', displayName: '供应商联系人', status: 'ACTIVE', version: 0, liveSessions: 2, lastLoginAt: '2026-10-21T01:00:00' };
  let canManage = true, failRead = false, conflict = false, writes = 0;
  await page.route('**/api/suppliers/900/portal-accounts**', async route => {
    const request = route.request(), url = new URL(request.url());
    const reply = (status, data, message) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ success: status === 200, data, error: message ? { message } : undefined }) });
    if (url.pathname.endsWith('/ui-mode')) {
      const body = request.postDataJSON(); canManage = body.canManage ?? canManage; failRead = body.failRead ?? failRead; conflict = body.conflict ?? conflict;
      return reply(200, null);
    }
    if (request.method() === 'GET') {
      if (failRead) return reply(503, null, '模拟账号读取失败');
      return reply(200, { canManage, accounts: { page: Number(url.searchParams.get('page')), size: 20, total: 1, items: [account] } });
    }
    writes++;
    const command = request.postDataJSON();
    if (!command.reason?.trim() || command.version !== account.version) throw new Error('Missing reason or stale version submitted');
    if (conflict) { conflict = false; account = { ...account, version: account.version + 1 }; return reply(409, null, '账号已变化'); }
    account = { ...account, version: account.version + 1, liveSessions: 0, status: command.status || account.status };
    return reply(200, account);
  });
  await page.getByRole('button', { name: '登录系统' }).click();
  const panel = page.getByRole('region', { name: '供应商门户账号', exact: true });
  await panel.waitFor(); await page.locator('body').ariaSnapshot();
  await panel.getByRole('button', { name: '门户账号管理', exact: true }).click();
  await panel.getByRole('cell', { name: 'portal.contact', exact: true }).waitFor();
  await page.locator('body').ariaSnapshot();
  await panel.getByRole('button', { name: '停用账号', exact: true }).click();
  await panel.getByRole('button', { name: '确认停用账号', exact: true }).click();
  await panel.getByRole('alert').filter({ hasText: '请填写操作原因' }).waitFor();
  if (writes !== 0 || !(await panel.getByRole('alert').evaluate(el => el === document.activeElement))) throw new Error('Invalid submit or missing error focus');
  await panel.getByRole('textbox', { name: '门户账号操作原因' }).fill('联系人已离职');
  await panel.getByRole('button', { name: '确认停用账号', exact: true }).click();
  await panel.getByRole('status').filter({ hasText: '停用账号成功' }).waitFor();
  await panel.getByRole('cell', { name: '停用', exact: true }).waitFor();
  await panel.getByRole('button', { name: '恢复账号', exact: true }).click();
  await panel.getByRole('textbox', { name: '门户账号操作原因' }).fill('重新授权');
  await panel.getByRole('button', { name: '确认恢复账号', exact: true }).click();
  await panel.getByRole('status').filter({ hasText: '恢复账号成功' }).waitFor();
  await panel.getByRole('button', { name: '撤销全部会话', exact: true }).click();
  await panel.getByRole('textbox', { name: '门户账号操作原因' }).fill('安全核查');
  await panel.getByRole('button', { name: '确认撤销全部会话', exact: true }).click();
  await panel.getByRole('status').filter({ hasText: '撤销全部会话成功' }).waitFor();
  const mode = body => page.evaluate(body => fetch('/api/suppliers/900/portal-accounts/ui-mode', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(() => {}), body);
  await mode({ conflict: true });
  await panel.getByRole('button', { name: '停用账号', exact: true }).click();
  await panel.getByRole('textbox', { name: '门户账号操作原因' }).fill('并发检查');
  await panel.getByRole('button', { name: '确认停用账号', exact: true }).click();
  await panel.getByRole('alert').filter({ hasText: '账号已变化' }).waitFor();
  if (await panel.getByRole('button', { name: '确认停用账号', exact: true }).count()) throw new Error('Stale confirmation retained');
  await page.screenshot({ path: 'output/playwright/portal-accounts-conflict.png', fullPage: true });
  await mode({ canManage: false });
  await panel.getByRole('button', { name: '刷新门户账号' }).click();
  await panel.getByText('当前账号只读，无权调整门户账号或撤销会话。', { exact: true }).waitFor();
  if (await panel.getByRole('button', { name: '停用账号', exact: true }).count()) throw new Error('Readonly mutation exposed');
  await page.setViewportSize({ width: 375, height: 812 });
  await panel.scrollIntoViewIfNeeded();
  await page.screenshot({ path: 'output/playwright/portal-accounts-mobile.png', fullPage: true });
  await mode({ failRead: true });
  await panel.getByRole('button', { name: '刷新门户账号' }).click();
  await panel.getByRole('alert').filter({ hasText: '模拟账号读取失败' }).waitFor();
  if (await panel.getByText('当前供应商暂无门户账号', { exact: true }).count()) throw new Error('Failure shown as empty');
  await mode({ failRead: false });
  await panel.getByRole('button', { name: '刷新门户账号' }).click();
  await panel.getByRole('cell', { name: 'portal.contact', exact: true }).waitFor();
  return { mocked: true, writes, reasonValidation: true, lifecycle: true, conflict: true, readonly: true, readRecovery: true };
}
