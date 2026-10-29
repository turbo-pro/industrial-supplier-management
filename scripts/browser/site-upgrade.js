async (page) => {
  const fixture=__FIXTURE__;
  await page.getByLabel('平台用户名').fill('site-console-0');
  await page.getByLabel('密码',{exact:true}).fill('Console#Pass123');
  await page.getByRole('button',{name:'进入 Console',exact:true}).click();
  await page.getByRole('heading',{name:'租户开通',exact:true}).waitFor();
  const tenants=await page.evaluate(async()=>{const r=await fetch('/api/console/tenants',{headers:{Authorization:`Bearer ${sessionStorage.getItem('ism.console.accessToken')}`}});return (await r.json()).data;});
  const tenant=tenants.find(t=>t.id===fixture.tenant);if(!tenant)throw new Error('Missing target tenant');
  const row=page.getByRole('row').filter({has:page.getByRole('cell',{name:tenant.code,exact:true})});
  await row.getByRole('button',{name:'升级多场站',exact:true}).click();
  const form=page.getByRole('form',{name:`升级多场站：${tenant.name}`});
  await form.getByLabel('新总部名称',{exact:true}).fill('浏览器总部');
  if(!(await form.textContent()).includes(fixture.site))throw new Error('Original site not shown');
  await form.screenshot({path:'output/playwright/site-upgrade/confirmation.png'});
  const versionChange=await page.evaluate(async tenant=>{
    const r=await fetch(`/api/console/tenants/${tenant.id}`,{method:'PUT',headers:{Authorization:`Bearer ${sessionStorage.getItem('ism.console.accessToken')}`,'Content-Type':'application/json','Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({name:tenant.name,timezone:tenant.timezone,locale:tenant.locale,version:tenant.version})});return r.status;
  },tenant);
  if(versionChange!==200)throw new Error('Unable to create real stale version');
  const conflict=page.waitForResponse(r=>r.url().endsWith('/upgrade-to-multi-site'));
  await form.getByRole('button',{name:'确认升级为多场站',exact:true}).click();
  if((await conflict).status()!==409)throw new Error('Stale upgrade was not rejected');
  await form.getByRole('alert').waitFor();
  const refreshed=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/console/tenants'&&r.request().method()==='GET');
  await form.getByRole('button',{name:'取消并刷新',exact:true}).click();await refreshed;await form.waitFor({state:'detached'});
  const attempts=[];
  await page.route('**/api/console/tenants/*/upgrade-to-multi-site',async route=>{
    attempts.push({key:route.request().headers()['idempotency-key'],body:route.request().postData()});
    const response=await route.fetch();
    if(attempts.length===1){if(response.status()!==200)throw new Error('Lost-response fixture did not commit');await route.abort('failed');}else await route.fulfill({response});
  });
  await row.getByRole('button',{name:'升级多场站',exact:true}).click();
  await form.getByLabel('新总部名称',{exact:true}).fill('浏览器总部');
  await form.getByRole('button',{name:'确认升级为多场站',exact:true}).click();
  await form.getByRole('button',{name:'使用原请求重试',exact:true}).waitFor();
  if(await form.getByLabel('新总部名称',{exact:true}).isEnabled())throw new Error('Unknown request body was not frozen');
  await form.getByRole('button',{name:'使用原请求重试',exact:true}).click();
  await form.waitFor({state:'detached'});await row.getByRole('cell',{name:'多场站（工厂）',exact:true}).waitFor();
  if(attempts.length!==2||JSON.stringify(attempts[0])!==JSON.stringify(attempts[1]))throw new Error('Retry changed key or body');
  await page.screenshot({path:'output/playwright/site-upgrade/desktop.png',fullPage:true});
  await page.getByRole('button',{name:'退出登录',exact:true}).click();
  await page.getByLabel('平台用户名').fill('site-console-1');await page.getByLabel('密码',{exact:true}).fill('Console#Pass123');
  await page.getByRole('button',{name:'进入 Console',exact:true}).click();
  await page.getByRole('heading',{name:'租户开通',exact:true}).waitFor();
  if(await page.getByRole('button',{name:'升级多场站',exact:true}).count())throw new Error('Read-only user has upgrade control');
}
