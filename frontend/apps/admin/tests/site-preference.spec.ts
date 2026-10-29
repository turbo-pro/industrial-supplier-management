import { expect, test } from '@playwright/test';

test('personal preference shows unavailable and readonly states across viewport sizes',async({page})=>{
  await page.addInitScript(()=>{sessionStorage.setItem('ism.admin.accessToken','mock-site-token');sessionStorage.setItem('ism.admin.actorId','1');});
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname;
    const data=path==='/api/site-preferences'?{preferredSite:null,unavailable:true,version:3,canManage:false}
      :path==='/api/site-preferences/options'?{items:[],total:0,page:0,size:20}
      :path==='/api/organizations/current'?{id:'100',code:'HQ',name:'总部',type:'HEADQUARTERS'}
      :path==='/api/configuration/branding'?{systemName:'工业供应商管理系统',logoUrl:'',faviconUrl:'',footerText:''}:[];
    await route.fulfill({contentType:'application/json',body:JSON.stringify({success:true,data,traceId:'site-ui',timestamp:new Date().toISOString()})});
  });
  await page.goto('/system/site-preference');
  await expect(page.getByText('原首选场站已不可用，请重新选择或清除。',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'保存首选场站',exact:true})).toBeDisabled();
  await expect(page.getByText('当前账号仅可查看个人首选场站。',{exact:true})).toBeVisible();
  for(const width of [375,768,1024,1440]){
    await page.setViewportSize({width,height:900});
    const refresh=page.getByRole('button',{name:'重新读取',exact:true});await refresh.focus();await expect(refresh).toBeFocused();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBeTruthy();
    if(width===375||width===1440)await page.screenshot({path:`../../../output/playwright/site-preference/mock-${width}.png`,fullPage:true});
  }
});

test('failed preference read can be retried without treating it as an unavailable stored site',async({page})=>{
  let reads=0;
  await page.addInitScript(()=>{sessionStorage.setItem('ism.admin.accessToken','mock-site-token');sessionStorage.setItem('ism.admin.actorId','1');});
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path==='/api/site-preferences'&&++reads===1){await route.fulfill({status:500,contentType:'application/json',body:JSON.stringify({success:false,error:{code:'TEST_READ_FAILURE',message:'读取暂时失败'},traceId:'site-ui'})});return;}
    const data=path==='/api/site-preferences'?{preferredSite:null,unavailable:false,version:0,canManage:true}
      :path==='/api/site-preferences/options'?{items:[],total:0,page:0,size:20}
      :path==='/api/organizations/current'?{id:'100',code:'HQ',name:'总部',type:'HEADQUARTERS'}
      :path==='/api/configuration/branding'?{systemName:'工业供应商管理系统',logoUrl:'',faviconUrl:'',footerText:''}:[];
    await route.fulfill({contentType:'application/json',body:JSON.stringify({success:true,data,traceId:'site-ui',timestamp:new Date().toISOString()})});
  });
  await page.goto('/system/site-preference');await expect(page.getByRole('alert').filter({hasText:'读取暂时失败'})).toBeVisible();
  await expect(page.getByText('原首选场站已不可用，请重新选择或清除。',{exact:true})).toHaveCount(0);
  await page.getByRole('button',{name:'重新读取',exact:true}).click();await expect(page.getByRole('button',{name:'保存首选场站',exact:true})).toBeEnabled();
});
