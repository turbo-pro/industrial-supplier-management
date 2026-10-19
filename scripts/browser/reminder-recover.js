async page => {
  const panel = page.getByRole('region', { name: '订阅到期提醒', exact: true });
  await panel.getByRole('cell', { name: '发送失败', exact: true }).waitFor();
  const send = async expected => {
    const pending = page.waitForResponse(r => r.url().endsWith('/reminders/dispatch') && r.request().method() === 'POST');
    await panel.getByRole('button', { name: '检查并发送到期提醒' }).click();
    const response = await pending;
    const body = await response.json();
    if (response.status() !== 200 || body.data.status !== expected) throw new Error(`Expected ${expected}`);
    if (expected === 'SENT') {
      const failedKey = await page.evaluate(() => sessionStorage.getItem('ism.test.failedReminderKey'));
      const currentKey = response.request().headers()['idempotency-key'];
      if (!failedKey || !currentKey || failedKey === currentKey) throw new Error('Recovery must use a new idempotency key');
    }
  };
  await send('SENT');
  await panel.getByRole('status').filter({ hasText: '已向 2 位租户管理员发送站内提醒' }).waitFor();
  await panel.getByRole('cell', { name: '已发送', exact: true }).waitFor();
  await page.locator('body').ariaSnapshot();
  await page.screenshot({ path: 'output/playwright/reminder-recovery-sent.png', fullPage: true });
  await send('SKIPPED');
  await panel.getByRole('status').filter({ hasText: '不会重复投递' }).waitFor();
  await page.getByRole('button', { name: '退出登录', exact: true }).click();
  await page.getByRole('textbox', { name: '平台用户名' }).waitFor();
  await page.locator('body').ariaSnapshot();
  await page.getByRole('textbox', { name: '平台用户名' }).fill('browser-reader');
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('Console#Pass123');
  await page.getByRole('button', { name: '进入 Console' }).click();
  await page.getByRole('button', { name: '查看订阅计划' }).waitFor();
  await page.locator('body').ariaSnapshot();
  await page.getByRole('button', { name: '查看订阅计划' }).click();
  await page.getByRole('button', { name: '到期提醒记录' }).waitFor();
  await page.locator('body').ariaSnapshot();
  await page.getByRole('button', { name: '到期提醒记录' }).click();
  await panel.getByRole('cell', { name: '已发送', exact: true }).waitFor();
  if (await panel.getByRole('button', { name: '检查并发送到期提醒' }).count()) throw new Error('Readonly mutation exposed');
  const status = await page.evaluate(async () => (await fetch('/api/console/tenants/20602/subscriptions/20603/reminders/dispatch', {
    method: 'POST', headers: { Authorization: `Bearer ${sessionStorage.getItem('ism.console.accessToken')}`, 'Idempotency-Key': 'recovery-forged-write' },
  })).status);
  if (status !== 403) throw new Error(`Expected 403, got ${status}`);
  await page.screenshot({ path: 'output/playwright/reminder-recovery-readonly.png', fullPage: true });
  return { recovered: true, duplicateSkipped: true, readonlyWriteStatus: status };
}
