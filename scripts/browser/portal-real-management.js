async page => {
  const admin = await page.context().newPage();
  try {
    await admin.goto('__ADMIN_URL__/suppliers/master?supplierId=21005');
    await admin.locator('body').ariaSnapshot();
    await admin.getByRole('textbox', { name: '租户编码', exact: true }).fill('PORTAL-BROWSER');
    await admin.getByRole('textbox', { name: '用户名', exact: true }).fill('browser-admin');
    await admin.getByRole('textbox', { name: '密码', exact: true }).fill('Employee#Pass123');
    await admin.getByRole('button', { name: '登录系统', exact: true }).click();
    // Routing may normalize the query during login; navigate only after authenticated workspace.
    await admin.getByRole('button', { name: '登录系统', exact: true }).waitFor({state:'hidden'});
    await admin.goto('__ADMIN_URL__/suppliers/master?supplierId=21005');
    const panel=admin.getByRole('region',{name:'供应商门户账号',exact:true});
    await panel.waitFor(); await admin.locator('body').ariaSnapshot();
    await panel.getByRole('button',{name:'门户账号管理',exact:true}).click();
    await panel.getByRole('cell',{name:'browser.user',exact:true}).waitFor();
    const login=async()=>{
      await page.getByRole('textbox',{name:'租户编码',exact:true}).fill('PORTAL-BROWSER');
      await page.getByRole('textbox',{name:'用户名',exact:true}).fill('browser.user');
      await page.getByRole('textbox',{name:'密码',exact:true}).fill('Portal#Pass123');
      const pending=page.waitForResponse(r=>r.url().endsWith('/portal/auth/login')&&r.status()===200);
      await page.getByRole('button',{name:'登录门户',exact:true}).click();
      const token=(await(await pending).json()).data.accessToken;
      await page.getByRole('heading',{name:'当前登录身份'}).waitFor();
      await page.locator('body').ariaSnapshot();return token;
    };
    const invalidate=async()=>{
      await page.getByRole('button',{name:'重新确认身份'}).click();
      await page.getByRole('alert').filter({hasText:'会话已失效'}).waitFor();
      await page.locator('body').ariaSnapshot();
    };
    const change=async action=>{
      await panel.getByRole('button',{name:action,exact:true}).click();
      await admin.locator('body').ariaSnapshot();
      await panel.getByRole('textbox',{name:'门户账号操作原因'}).fill(`真实跨端验收：${action}`);
      await panel.getByRole('button',{name:`确认${action}`,exact:true}).click();
      await panel.getByRole('status').filter({hasText:`${action}成功`}).waitFor();
    };
    const old=await login();
    await change('停用账号'); await invalidate();
    await page.getByRole('textbox',{name:'密码',exact:true}).fill('Portal#Pass123');
    await page.getByRole('button',{name:'登录门户',exact:true}).click();
    await page.getByRole('alert').filter({hasText:'凭证无效'}).waitFor();
    await change('恢复账号');
    const replay=await page.request.get(new URL('/api/portal/auth/me',page.url()).href,{headers:{Authorization:`Bearer ${old}`}});
    if(replay.status()!==401)throw new Error('Restore revived old session');
    await login(); await change('撤销全部会话'); await invalidate();
    await login();
    await page.getByRole('button',{name:'退出当前会话'}).click();
    await page.getByRole('status').filter({hasText:'已退出当前会话'}).waitFor();
    await admin.screenshot({path:'output/playwright/portal-real-management.png',fullPage:true});
    // A second employee with real read-only grants cannot mutate this account.
    const reader=await page.context().newPage();
    try{
      await reader.goto('__ADMIN_URL__/');await reader.locator('body').ariaSnapshot();
      await reader.getByRole('textbox',{name:'租户编码',exact:true}).fill('PORTAL-BROWSER');
      await reader.getByRole('textbox',{name:'用户名',exact:true}).fill('browser-reader');
      await reader.getByRole('textbox',{name:'密码',exact:true}).fill('Employee#Pass123');
      await reader.getByRole('button',{name:'登录系统',exact:true}).click();
      await reader.getByRole('button',{name:'登录系统',exact:true}).waitFor({state:'hidden'});
      await reader.goto('__ADMIN_URL__/suppliers/master?supplierId=21005');
      const readonly=reader.getByRole('region',{name:'供应商门户账号',exact:true});
      await readonly.waitFor();await reader.locator('body').ariaSnapshot();
      await readonly.getByRole('button',{name:'门户账号管理',exact:true}).click();
      await readonly.getByText('当前账号只读，无权调整门户账号或撤销会话。',{exact:true}).waitFor();
      if(await readonly.getByRole('button',{name:'停用账号',exact:true}).count())throw new Error('Read-only action visible');
      const token=await reader.evaluate(()=>sessionStorage.getItem('ism.admin.accessToken'));
      const accounts=await reader.request.get('__ADMIN_URL__/api/suppliers/21005/portal-accounts',{headers:{Authorization:`Bearer ${token}`}});
      const item=(await accounts.json()).data.accounts.items[0];
      const denied=await reader.request.post(`__ADMIN_URL__/api/suppliers/21005/portal-accounts/${item.id}/status`,{headers:{Authorization:`Bearer ${token}`},data:{version:item.version,status:'DISABLED',reason:'只读越权验证'}});
      if(denied.status()!==403)throw new Error('Read-only employee could mutate');
    }finally{await reader.close();}
  } finally {await admin.close();}
}
