import { expect, test } from '@playwright/test';

test('platform operator logs in through the isolated Console endpoint', async ({ page }) => {
  await page.route('**/api/console/auth/login', async route => {
    expect(route.request().postDataJSON()).toMatchObject({ username: 'platform-admin' });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      success: true,
      data: { accessToken: 'console-access', refreshToken: 'console-refresh', expiresIn: 900,
        user: { id: '1', username: 'platform-admin', displayName: '平台管理员',
          passwordChangeRequired: false, permissions: ['platform:tenant:view'] } },
      traceId: 'console-login', timestamp: new Date().toISOString(),
    }) });
  });
  await page.goto('/');
  await page.getByLabel('平台用户名').fill('platform-admin');
  await page.getByLabel('密码').fill('Console#Pass123');
  await page.getByRole('button', { name: '进入 Console' }).click();
  await expect(page.getByRole('status')).toHaveText('欢迎进入平台控制台，平台管理员');
});
