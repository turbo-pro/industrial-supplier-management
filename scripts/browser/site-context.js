async page => {
  const fixture=__FIXTURE__,base=new URL(page.url()).origin;
  await page.getByLabel('租户编码').fill(fixture.code);await page.getByLabel('用户名',{exact:true}).fill('pref-admin');await page.getByLabel('密码',{exact:true}).fill('Site#Initial123');
  const login=page.waitForResponse(r=>r.url().endsWith('/api/auth/login')&&r.status()===200);await page.getByRole('button',{name:'登录系统',exact:true}).click();await (await login).json();
  await page.goto(base+'/system/site-preference');const card=page.locator('.site-context-preview');await card.waitFor();
  await card.getByRole('button',{name:'获取场站预览',exact:true}).click();await card.getByRole('alert').filter({hasText:'存在多个授权场站'}).waitFor();
  await card.locator('.el-select__wrapper').click();await page.getByRole('option',{name:'第一工厂（MAIN_SITE）',exact:true}).click();
  const issued=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/site-context'&&r.request().method()==='POST'&&r.status()===200);
  await card.getByRole('button',{name:'获取场站预览',exact:true}).click();const context=(await(await issued).json()).data.contextToken;
  await card.getByRole('button',{name:'校验场站预览',exact:true}).click();await card.getByRole('status').filter({hasText:'已校验场站：第一工厂'}).waitFor();
  const boundaries=await page.evaluate(async({context,headquarters})=>{
    const bearer=sessionStorage.getItem('ism.admin.accessToken');
    const rejected=await fetch('/api/organizations/current',{headers:{Authorization:`Bearer ${bearer}`,'X-Site-Context':context}});
    const legacy=await fetch('/api/organizations/current',{headers:{Authorization:`Bearer ${bearer}`}});const body=await legacy.json();
    return rejected.status===422&&legacy.status===200&&body.data.id===headquarters&&![...Object.values(sessionStorage),...Object.values(localStorage)].includes(context);
  },{context,headquarters:fixture.headquarters});
  if(!boundaries)throw new Error('Request context boundary or memory-only rule failed');
  await page.screenshot({path:'output/playwright/site-context/desktop.png',fullPage:true});await page.setViewportSize({width:375,height:812});await card.scrollIntoViewIfNeeded();
  await page.screenshot({path:'output/playwright/site-context/mobile.png',fullPage:true});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw new Error('Site context preview overflows mobile');
  await card.getByRole('button',{name:'清除预览',exact:true}).click();if(await card.getByRole('status').filter({hasText:'已校验场站：'}).count())throw new Error('Local clear retained validated preview');
  await card.getByRole('button',{name:'获取场站预览',exact:true}).click();await card.getByText('待校验场站：第一工厂（MAIN_SITE）',{exact:true}).waitFor();
}
