import { expect, test, type Page } from '@playwright/test';
async function mock(page:Page,revoke=false){
  await page.addInitScript(()=>{sessionStorage.setItem('ism.admin.accessToken','mock-context-access');sessionStorage.setItem('ism.admin.actorId','1');});
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path==='/api/site-context'&&route.request().method()==='GET'){
      expect(route.request().headers()['x-site-context']).toBe('opaque-preview-context');
      if(revoke){await route.fulfill({status:403,contentType:'application/json',body:JSON.stringify({success:false,error:{code:'IAM_SITE_CONTEXT_UNAVAILABLE',message:'当前场站授权已失效，请重新选取'},traceId:'preview'})});return;}
    }
    const site={id:'101',code:'SITE_A',name:'预览工厂'};
    const data=path==='/api/site-preferences'?{preferredSite:null,unavailable:false,version:0,canManage:true}
      :path==='/api/site-preferences/options'?{items:[site],total:1,page:0,size:20}
      :path==='/api/site-context'?(route.request().method()==='POST'?{site,contextToken:'opaque-preview-context',expiresAt:new Date(Date.now()+30000).toISOString(),selectionReason:'ONLY_SITE'}:{site,expiresAt:new Date(Date.now()+30000).toISOString()})
      :path==='/api/organizations/current'?{id:'100',code:'HQ',name:'总部',type:'HEADQUARTERS'}
      :path==='/api/configuration/branding'?{systemName:'工业供应商管理系统',logoUrl:'',faviconUrl:'',footerText:''}:[];
    await route.fulfill({contentType:'application/json',body:JSON.stringify({success:true,data,traceId:'preview',timestamp:new Date().toISOString()})});
  });
  await page.goto('/system/site-preference');await expect(page.getByText('场站选择预览',{exact:true})).toBeVisible();
}
test('preview verifies with a request-only header and clears locally',async({page})=>{
  await mock(page);await page.getByRole('button',{name:'获取场站预览',exact:true}).click();
  await expect(page.getByText('待校验场站：预览工厂（SITE_A）',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'校验场站预览',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'已校验场站：预览工厂'})).toBeVisible();
  expect(await page.evaluate(()=>[...Object.values(sessionStorage),...Object.values(localStorage)].includes('opaque-preview-context'))).toBeFalsy();
  await page.getByRole('button',{name:'清除预览',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'已校验场站：'})).toHaveCount(0);await expect(page.getByRole('button',{name:'校验场站预览',exact:true})).toBeDisabled();
});
test('revoked preview clears the former validated context',async({page})=>{
  await mock(page,true);await page.getByRole('button',{name:'获取场站预览',exact:true}).click();await expect(page.getByRole('button',{name:'校验场站预览',exact:true})).toBeEnabled();
  await page.getByRole('button',{name:'校验场站预览',exact:true}).click();await expect(page.getByRole('alert').filter({hasText:'当前场站授权已失效'})).toBeVisible();
  await expect(page.getByRole('status').filter({hasText:'已校验场站：'})).toHaveCount(0);await expect(page.getByRole('button',{name:'校验场站预览',exact:true})).toBeDisabled();
});
test('expired preview is discarded by the page timer',async({page})=>{
  await page.clock.install({time:new Date()});
  await mock(page);await page.getByRole('button',{name:'获取场站预览',exact:true}).click();
  await expect(page.getByText('待校验场站：预览工厂（SITE_A）',{exact:true})).toBeVisible();
  await page.clock.fastForward(31000);
  await expect(page.getByRole('alert').filter({hasText:'场站预览已过期，请重新获取'})).toBeVisible();
  await expect(page.getByRole('button',{name:'校验场站预览',exact:true})).toBeDisabled();
});
