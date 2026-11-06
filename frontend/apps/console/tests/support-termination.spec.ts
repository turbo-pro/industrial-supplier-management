import { test, expect } from '@playwright/test';
for (const allowed of [true, false]) {
  test(`termination permission ${allowed}`, async ({ page }) => {
    let writes = 0;
    await page.addInitScript(() => sessionStorage.setItem('ism.console.accessToken', 'termination-test'));
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname;
      let data: unknown = [];
      if (path.endsWith('/auth/me')) data = { id:'1', displayName:'安全管理员', passwordChangeRequired:false,
        permissions:['platform:support:view', ...(allowed ? ['platform:support:terminate'] : [])] };
      if (path.endsWith('/support-requests')) data = { total:0, items:[], page:0, size:10 };
      if (path.endsWith('/terminate')) {
        writes++;
        expect(route.request().postDataJSON()).toEqual({reason:'安全事件人工终止'});
        data = {status:'REVOKED'};
      }
      await route.fulfill({json:{success:true,data}});
    });
    await page.goto('/');
    await page.getByRole('button',{name:'支持申请工作台',exact:true}).click();
    const panel = page.getByRole('region',{name:'强制终止支持会话'});
    if (!allowed) { await expect(panel).toHaveCount(0); return; }
    await panel.getByLabel('支持会话 ID').fill('123');
    await panel.getByLabel('终止原因').fill('安全事件人工终止');
    await panel.getByRole('button',{name:'核对终止操作'}).click();
    expect(writes).toBe(0);
    await panel.getByRole('button',{name:'取消',exact:true}).click();
    expect(writes).toBe(0);
    await panel.getByRole('button',{name:'核对终止操作'}).click();
    await panel.getByRole('button',{name:'确认强制终止',exact:true}).click();
    await expect(panel.getByRole('status')).toContainText('会话已终止');
    expect(writes).toBe(1);
  });
}
