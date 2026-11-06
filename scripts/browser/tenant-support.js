async(page)=>{
 const f=__FIXTURE__;
 // The proxy probe pins the temporary certificate. Only this browser context accepts it.
 if(f.proxy)page=await (await page.context().browser().newContext({ignoreHTTPSErrors:true})).newPage();
 // Use real tenant login; navigation and policy calls pass through the production HTTP stack.
 await page.goto(f.adminUrl);await page.getByLabel('租户编码',{exact:true}).fill(f.tenantCode);await page.getByLabel('用户名',{exact:true}).fill('pref-admin');await page.getByLabel('密码',{exact:true}).fill('Site#Initial123');await page.getByRole('button',{name:'登录系统',exact:true}).click();await page.getByText('登录成功',{exact:true}).waitFor();await page.goto(f.adminUrl+'/system/support-authorization');
 await page.getByRole('heading',{name:'支持授权',exact:true}).waitFor();await page.getByRole('button',{name:'保存支持策略',exact:true}).click({trial:true});await page.locator('.tenant-support .el-switch').click();await page.locator('.tenant-support .el-select__wrapper').click();await page.getByRole('option').filter({hasText:'租户管理员'}).click();await page.getByRole('button',{name:'保存支持策略',exact:true}).click();await page.getByRole('status').filter({hasText:'操作回执已收到'}).waitFor();await page.getByRole('button',{name:'重新读取支持授权',exact:true}).click();await page.getByRole('button',{name:'查看租户审批',exact:true}).click();await page.getByRole('button',{name:'批准租户授权',exact:true}).click();await page.getByLabel('租户处理意见',{exact:true}).fill('租户明确批准，只读基础字段');
 let drop=true;const calls=[];await page.route('**/api/tenant-support/requests/*/approve',async route=>{calls.push({key:route.request().headers()['idempotency-key'],body:route.request().postDataJSON()});if(drop){drop=false;const r=await route.fetch();if(r.status()!==200)throw Error('Tenant consent rejected');await route.abort();}else await route.continue();});await page.getByRole('button',{name:'确认租户决定',exact:true}).click();await page.getByRole('alert').filter({hasText:'结果尚未确认'}).waitFor();await page.getByRole('button',{name:'使用原请求重试支持授权',exact:true}).click();await page.getByRole('status').filter({hasText:'操作回执已收到'}).waitFor();if(calls.length!==2||JSON.stringify(calls[0])!==JSON.stringify(calls[1]))throw Error('Consent replay changed');await page.screenshot({path:'output/playwright/tenant-support/tenant-approved.png',fullPage:true});
 const admin=page;const consolePage=await page.context().newPage();await consolePage.goto(f.consoleUrl);await consolePage.getByLabel('平台用户名').fill('site-console-1');await consolePage.getByLabel('密码',{exact:true}).fill('Console#Pass123');await consolePage.getByRole('button',{name:'进入 Console',exact:true}).click();await consolePage.getByRole('heading',{name:'套餐与模块',exact:true}).waitFor();await consolePage.getByRole('button',{name:'支持申请工作台',exact:true}).click();await consolePage.getByRole('button',{name:'查看支持申请',exact:true}).first().click();await consolePage.getByRole('button',{name:'检查租户授权交集',exact:true}).click();await consolePage.getByRole('status').filter({hasText:f.supplierId?'授权交集已满足':'授权交集已满足，支持会话仍未启用'}).waitFor();await consolePage.getByRole('region',{name:'支持申请详情',exact:true}).screenshot({path:'output/playwright/tenant-support/console-ready.png'});
 const token=await consolePage.evaluate(()=>sessionStorage.getItem('ism.console.accessToken'));if(!token)throw Error('Missing real Console session');const denied=await consolePage.request.get(f.backendUrl+'/api/suppliers',{headers:{Authorization:'Bearer '+token}});if(![401,403].includes(denied.status()))throw Error('Console obtained tenant business access');let workspaceToWithdraw;if(f.workspace){
   const direct=await page.context().newPage();
   await direct.goto(f.adminUrl+'/support-workspace.html');
   await direct.getByText('请从 Console 的支持申请打开此工作台。',{exact:true}).waitFor();
   if(await direct.getByLabel('供应商 ID').count())throw Error('Direct entry exposes supplier lookup');
   await direct.close();
   const panel=consolePage.getByRole('region',{name:'供应商只读支持会话'});
   const issued=consolePage.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/workspace-entry-token'));
   const popupReady=consolePage.waitForEvent('popup');
   await panel.getByRole('button',{name:'打开管理端支持工作台'}).click();
   const popup=await popupReady;
   const issue=await issued;
   if(!issue.ok()||!issue.headers()['cache-control']?.includes('no-store'))throw Error('Workspace entry issue failed or was cacheable');
   const {data:{entryToken}}=await issue.json();
   const browserSecret=issue.request().postDataJSON().browserSecret;
   await popup.getByText(/租户：.*操作人：.*截止：/).waitFor();
   if(await popup.evaluate(()=>window.opener!==null))throw Error('Workspace retained opener after handoff');
   await popup.getByLabel('供应商 ID').fill(f.supplierId);
   const readResponse=popup.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/read'));
   await popup.getByLabel('供应商 ID').press('Enter');
   const read=await readResponse;
   if(!read.headers()['cache-control']?.includes('no-store'))throw Error('Workspace read was cacheable');
   const {accessSecret}=read.request().postDataJSON();
   await popup.getByText('浏览器验收供应商',{exact:true}).waitFor();
   if(await popup.getByText('PRIVATE',{exact:true}).count())throw Error('Private field rendered');
   for(const windowPage of [popup,consolePage]){
     const surfaces=await windowPage.evaluate(()=>[location.href,document.documentElement.innerHTML,document.cookie,JSON.stringify(localStorage),JSON.stringify(sessionStorage)]);
     if(surfaces.some(value=>[entryToken,browserSecret,accessSecret].some(secret=>value.includes(secret))))throw Error('Workspace credential leaked');
   }
   for(const width of [375,768,1024,1440]){
     await popup.setViewportSize({width,height:640});
     if(await popup.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Workspace page overflow '+width);
   }
   await popup.setViewportSize({width:1024,height:820});
   await popup.screenshot({path:'output/playwright/tenant-support/workspace-active.png'});
   await popup.getByRole('button',{name:'撤销会话并退出'}).click();
   await popup.getByText('会话已撤销，可关闭此窗口。',{exact:true}).waitFor();
   await panel.getByText('会话已撤销。',{exact:true}).waitFor();
   if(await popup.getByText('浏览器验收供应商',{exact:true}).count())throw Error('Workspace retained data after exit');
   const replay=await popup.request.post(read.url(),{data:{accessSecret}});
   if(replay.status()!==403)throw Error('Workspace credential usable after exit');
   await popup.close();
   const orphan=await consolePage.request.post(f.backendUrl+'/api/console/support-requests/'+f.id+'/sessions',
     {headers:{Authorization:'Bearer '+token}});
   if(!orphan.ok())throw Error('Could not seed a pending session from a separate request');
   const {data:{sessionId}}=await orphan.json();
   await consolePage.reload();
   await consolePage.getByRole('heading',{name:'套餐与模块',exact:true}).waitFor();
   await consolePage.getByRole('button',{name:'支持申请工作台',exact:true}).click();
   await consolePage.getByRole('button',{name:'查看支持申请',exact:true}).first().click();
   await consolePage.getByRole('button',{name:'检查租户授权交集',exact:true}).click();
   await panel.getByRole('button',{name:'打开管理端支持工作台'}).waitFor();
   const history=consolePage.getByRole('region',{name:'支持会话记录'});
   await history.getByRole('button',{name:'读取最新会话记录'}).click();
   const pending=history.getByRole('row').filter({hasText:sessionId});
   await pending.getByRole('button',{name:'撤销此会话'}).click();
   await history.getByRole('button',{name:'确认撤销会话'}).click();
   await pending.getByText('已撤销',{exact:true}).waitFor();
   const auditedSession=new URL(read.url()).pathname.split('/')[3];
   const audited=history.getByRole('row').filter({hasText:auditedSession});
   await audited.getByRole('button',{name:'查看访问日志'}).click();
   const logs=history.getByRole('region',{name:'会话访问日志'});
   const auditRow=logs.getByRole('row').filter({hasText:f.supplierId});
   await auditRow.getByText('读取成功',{exact:true}).waitFor();
   if(await logs.getByText('浏览器验收供应商',{exact:true}).count())throw Error('Log exposed supplier field');

   const second=consolePage.waitForEvent('popup');
   await panel.getByRole('button',{name:'打开管理端支持工作台'}).click();
   workspaceToWithdraw=await second;
   await workspaceToWithdraw.getByLabel('供应商 ID').fill(f.supplierId);
   await workspaceToWithdraw.getByRole('button',{name:'读取供应商'}).click();
   await workspaceToWithdraw.getByText('浏览器验收供应商',{exact:true}).waitFor();
 }else if(f.supplierId){
   const panel=consolePage.getByRole('region',{name:'供应商只读支持会话'});
   const exchanged=consolePage.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/exchange'));
   await panel.getByRole('button',{name:'开启只读会话'}).click();
   const exchange=await exchanged;
   if(!exchange.ok()||!exchange.headers()['cache-control']?.includes('no-store'))throw Error('Invalid exchange');
   const {data:{accessSecret}}=await exchange.json();
   await panel.getByLabel('供应商 ID').fill(f.supplierId);
   const readResponse=consolePage.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/read'));
   await panel.getByRole('button',{name:'读取供应商'}).click();
   const read=await readResponse;
   await panel.getByText('浏览器验收供应商',{exact:true}).waitFor();
   if(await panel.getByText('PRIVATE',{exact:true}).count())throw Error('Private field rendered');
   const artifacts=await consolePage.evaluate(()=>[location.href,document.documentElement.innerHTML,document.cookie,JSON.stringify(localStorage),JSON.stringify(sessionStorage)]);
   if(artifacts.some(value=>value.includes(accessSecret)))throw Error('Session credential leaked');
   await consolePage.route('**/api/console/support-sessions/*/revoke',async route=>{
     const response=await route.fetch();
     if(!response.ok())throw Error('First revoke failed');
     await route.abort();
   },{times:1});
   await panel.getByRole('button',{name:'撤销会话并退出'}).click();
   await panel.getByText('撤销结果未确认。已停止读取，请重试撤销。',{exact:true}).waitFor();
   if(await panel.getByRole('button',{name:'读取供应商',exact:true}).count())throw Error('Read available after uncertain revoke');
   await panel.getByRole('button',{name:'重试撤销会话'}).click();
   await panel.getByText('会话已撤销。',{exact:true}).waitFor();
   if(await panel.getByText('浏览器验收供应商',{exact:true}).count())throw Error('Stale result after revoke');
   const replay=await consolePage.request.post(read.url(),{headers:{Authorization:'Bearer '+token},data:{accessSecret}});
   if(replay.status()!==403)throw Error('Revoked credential still usable');
 }else{
   const sessionDenied=await consolePage.request.post(f.backendUrl+'/api/console/support-requests/'+f.id+'/sessions',{headers:{Authorization:'Bearer '+token},data:{}});
   if(![403,404,405].includes(sessionDenied.status()))throw Error('Disabled support session did not deny');
   if(await consolePage.getByRole('button',{name:'开启只读会话'}).count())throw Error('Disabled session entry visible');
 }
 await admin.getByRole('button',{name:'撤回租户授权',exact:true}).click();await admin.getByLabel('租户处理意见',{exact:true}).fill('结束支持，立即撤回');await admin.getByRole('button',{name:'确认租户决定',exact:true}).click();await admin.getByRole('status').filter({hasText:'操作回执已收到'}).waitFor();if(workspaceToWithdraw){
   await workspaceToWithdraw.getByText('会话已失效，已清除读取结果。',{exact:true}).waitFor();
   if(await workspaceToWithdraw.getByText('浏览器验收供应商',{exact:true}).count()||await workspaceToWithdraw.getByLabel('供应商 ID').count())throw Error('Withdrawn workspace retained data or controls');
   await consolePage.getByRole('region',{name:'供应商只读支持会话'}).getByText('会话已撤销。',{exact:true}).waitFor();
   await workspaceToWithdraw.close();
 }
 await consolePage.getByRole('button',{name:'检查租户授权交集',exact:true}).click();await consolePage.getByRole('status').filter({hasText:'租户已撤回'}).waitFor();await consolePage.getByRole('region',{name:'支持申请详情',exact:true}).screenshot({path:'output/playwright/tenant-support/console-revoked.png'});
 for(const width of [375,768,1024,1440]){await admin.setViewportSize({width,height:900});if(await admin.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Tenant page overflow '+width);}await admin.screenshot({path:'output/playwright/tenant-support/tenant-final.png',fullPage:true});await consolePage.close();
}
