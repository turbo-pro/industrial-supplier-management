async (page) => {
  const fixture=__FIXTURE__,base=new URL(page.url()).origin;
  async function login(username){
    await page.getByLabel('租户编码').fill(fixture.code);await page.getByLabel('用户名',{exact:true}).fill(username);await page.getByLabel('密码',{exact:true}).fill('Site#Initial123');
    const response=page.waitForResponse(r=>r.url().endsWith('/api/auth/login')&&r.status()===200);
    await page.getByRole('button',{name:'登录系统',exact:true}).click();await (await response).json();
    await page.goto(base+'/system/site-preference');await page.getByRole('heading',{name:'首选场站',exact:true}).waitFor();
  }
  await login('pref-admin');
  const select=page.getByRole('combobox',{name:'首选场站',exact:true});await select.waitFor();
  await page.waitForFunction(()=>{const input=document.querySelector('.site-preference-page input[role="combobox"]');return input instanceof HTMLInputElement&&!input.disabled;});
  await page.locator('.site-preference-page .el-select__wrapper').click();await page.getByRole('option',{name:'第一工厂（MAIN_SITE）',exact:true}).click();
  const external=await page.evaluate(async f=>{const r=await fetch('/api/site-preferences',{method:'PUT',headers:{Authorization:`Bearer ${sessionStorage.getItem('ism.admin.accessToken')}`,'Content-Type':'application/json','Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({siteId:f.second,version:0})});return r.status;},fixture);
  if(external!==200)throw new Error('Unable to create stale preference');
  const conflict=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/site-preferences'&&r.request().method()==='PUT');
  await page.getByRole('button',{name:'保存首选场站',exact:true}).click();if((await conflict).status()!==409)throw new Error('Stale preference not rejected');
  await page.getByRole('alert').filter({hasText:/版本|变化|冲突/}).waitFor();
  await page.getByRole('button',{name:'重新读取',exact:true}).click();
  await page.locator('.el-select').filter({hasText:'第二工厂（SECOND-SITE）'}).waitFor();
  await page.waitForFunction(()=>{const input=document.querySelector('.site-preference-page input[role="combobox"]');return input instanceof HTMLInputElement&&!input.disabled;});
  await page.locator('.site-preference-page .el-select__wrapper').click();await page.getByRole('option',{name:'第一工厂（MAIN_SITE）',exact:true}).click();
  const attempts=[];
  await page.route('**/api/site-preferences',async route=>{if(route.request().method()!=='PUT'){await route.continue();return;}attempts.push({key:route.request().headers()['idempotency-key'],body:route.request().postData()});const response=await route.fetch();if(attempts.length===1){if(response.status()!==200)throw new Error('Lost response did not commit');await route.abort('failed');}else await route.fulfill({response});});
  await page.getByRole('button',{name:'保存首选场站',exact:true}).click();
  await page.getByRole('button',{name:'使用原请求重试',exact:true}).waitFor();if(await select.isEnabled())throw new Error('Unknown preference was editable');
  await page.getByRole('button',{name:'使用原请求重试',exact:true}).click();await page.getByRole('status').filter({hasText:'个人首选场站已保存'}).waitFor();
  if(attempts.length!==2||JSON.stringify(attempts[0])!==JSON.stringify(attempts[1]))throw new Error('Retry changed key or body');
  await page.unroute('**/api/site-preferences');
  await page.reload();await page.locator('.el-select').filter({hasText:'第一工厂（MAIN_SITE）'}).waitFor();
  await page.screenshot({path:'output/playwright/site-preference/desktop.png',fullPage:true});
  await page.setViewportSize({width:375,height:812});await page.getByRole('heading',{name:'首选场站',exact:true}).scrollIntoViewIfNeeded();
  await page.screenshot({path:'output/playwright/site-preference/mobile.png',fullPage:true});
  if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw new Error('Preference page overflows mobile viewport');
  await page.getByRole('button',{name:'清除选择',exact:true}).click();await page.getByRole('button',{name:'保存首选场站',exact:true}).click();await page.getByRole('status').filter({hasText:'个人首选场站已保存'}).waitFor();
  // Use a fresh login, without sharing the first actor's session storage.
  await page.evaluate(()=>sessionStorage.clear());await page.goto(base+'/');await login('pref-reader');
  await page.getByText('当前账号仅可查看个人首选场站。',{exact:true}).waitFor();
  if(await page.getByRole('button',{name:'保存首选场站',exact:true}).isEnabled())throw new Error('Read-only actor can save');
}
