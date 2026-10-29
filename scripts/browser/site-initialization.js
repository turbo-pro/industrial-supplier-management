async (page) => {
  const fixture=__FIXTURE__;
  const base=new URL(page.url()).origin;
  await page.getByLabel('平台用户名').fill('site-console-0');
  await page.getByLabel('密码').fill('Console#Pass123');
  await page.getByRole('button',{name:'进入 Console',exact:true}).click();
  await page.getByRole('heading',{name:'租户开通',exact:true}).waitFor();
  const attempts=[];
  await page.route('**/api/console/tenants/*/initialize',async route=>{
    if(!route.request().url().includes(fixture.single)){await route.continue();return;}
    attempts.push({key:route.request().headers()['idempotency-key'],body:route.request().postData()});
    const result=await route.fetch();
    if(attempts.length===1)await route.abort('failed');else await route.fulfill({response:result});
  });
  for(const [id,multi] of [[fixture.single,false],[fixture.multi,true]]){
    // Locate the fixture row by its server-generated tenant ID through the returned list.
    const response=await page.evaluate(async()=>{const r=await fetch('/api/console/tenants',{headers:{Authorization:`Bearer ${sessionStorage.getItem('ism.console.accessToken')}`}});return (await r.json()).data;});
    const tenant=response.find(t=>t.id===id);if(!tenant)throw new Error('Missing fixture tenant');
    const row=page.getByRole('row').filter({has:page.getByRole('cell',{name:tenant.code,exact:true})});
    await row.getByRole('button',{name:'初始化租户',exact:true}).click();
    const form=page.getByRole('form',{name:`初始化租户：${tenant.name}`});
    await form.getByRole('combobox').selectOption(multi?'MULTI_SITE':'SINGLE_SITE');
    if(multi)await form.getByLabel('总部名称',{exact:true}).fill('浏览器总部');
    await form.getByLabel('场站称谓',{exact:true}).fill(multi?'门店':'工厂');
    await form.getByLabel('首个场站名称',{exact:true}).fill('浏览器首场站');
    await form.getByLabel('管理员用户名',{exact:true}).fill('browser-site-admin');
    await form.getByLabel('管理员姓名',{exact:true}).fill('浏览器管理员');
    await form.getByLabel('管理员初始密码',{exact:true}).fill('Site#Initial123');
    await form.getByRole('button',{name:'确认初始化',exact:true}).click();
    if(!multi){
      await form.getByRole('button',{name:'使用原请求重试',exact:true}).waitFor();
      if(await form.getByLabel('首个场站名称',{exact:true}).isEnabled())throw new Error('Unknown attempt changed body');
      await form.getByRole('button',{name:'使用原请求重试',exact:true}).click();
    }
    await form.waitFor({state:'detached'});
    await row.getByRole('cell',{name:'READY',exact:true}).waitFor();
    await row.getByRole('cell',{name:multi?'多场站（门店）':'单场站（工厂）',exact:true}).waitFor();
  }
  if(attempts.length!==2||JSON.stringify(attempts[0])!==JSON.stringify(attempts[1]))throw new Error('Retry did not preserve exact request');
  await page.screenshot({path:'output/playwright/site-initialization/desktop.png',fullPage:true});
  await page.getByRole('button',{name:'退出登录',exact:true}).click();
  await page.getByLabel('平台用户名').fill('site-console-1');
  await page.getByLabel('密码').fill('Console#Pass123');
  await page.getByRole('button',{name:'进入 Console',exact:true}).click();
  await page.getByRole('heading',{name:'租户开通',exact:true}).waitFor();
  if(await page.getByRole('button',{name:'初始化租户',exact:true}).count())throw new Error('Read-only user can initialize');
}
