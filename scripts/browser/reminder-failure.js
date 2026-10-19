async page => {
  await page.getByRole('textbox', { name: '平台用户名' }).fill('browser-admin');
  await page.getByRole('textbox', { name: '密码', exact: true }).fill('Console#Pass123');
  await page.getByRole('button', { name: '进入 Console' }).click();
  await page.getByRole('button', { name: '查看订阅计划' }).waitFor();
  await page.locator('body').ariaSnapshot();
  await page.getByRole('button', { name: '查看订阅计划' }).click();
  await page.getByRole('button', { name: '到期提醒记录' }).waitFor();
  await page.locator('body').ariaSnapshot();
  await page.getByRole('button', { name: '到期提醒记录' }).click();
  const panel = page.getByRole('region', { name: '订阅到期提醒', exact: true });
  await panel.getByRole('cell', { name: '尚无提醒记录', exact: true }).waitFor();
  await page.locator('body').ariaSnapshot();
  const responsePromise = page.waitForResponse(r => r.url().endsWith('/reminders/dispatch') && r.request().method() === 'POST');
  await panel.getByRole('button', { name: '检查并发送到期提醒' }).click();
  const response = await responsePromise;
  const body = await response.json();
  if (response.status() !== 200 || body.data.status !== 'FAILED') throw new Error('Expected real FAILED dispatch');
  const failedKey = response.request().headers()['idempotency-key'];
  if (!failedKey) throw new Error('Missing idempotency key');
  await page.evaluate(key => sessionStorage.setItem('ism.test.failedReminderKey', key), failedKey);
  await panel.getByRole('alert').filter({ hasText: '请修复收件人或消息模板后重新检查' }).waitFor();
  await panel.getByRole('cell', { name: '发送失败', exact: true }).waitFor();
  await page.screenshot({ path: 'output/playwright/reminder-recovery-failed.png', fullPage: true });
  return { failed: true, errorCode: body.data.errorCode };
}
