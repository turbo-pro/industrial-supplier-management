import {expect,test} from '@playwright/test';
test('workspace navigation, bounded tables and mobile drawer preserve keyboard access',async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('ism.console.accessToken','layout-test'));
 await page.route('**/api/**',async route=>{const p=new URL(route.request().url()).pathname;let data:unknown=[];
 if(p.endsWith('/branding'))data={systemName:'工业供应商管理系统 Console',logoUrl:'',faviconUrl:'',footerText:'平台运营控制台'};
 else if(p.endsWith('/auth/me'))data={id:'1',displayName:'运营管理员',permissions:['platform:package:view','platform:tenant:view','platform:user:view']};
 else if(p.endsWith('/tenants'))data=[{id:'1',code:'CHEM_LONG_CODE',name:'华东新材料制造与化工供应链集团股份有限公司',status:'ACTIVE',initializationStatus:'READY',operationMode:'MULTI_SITE'}];
 await route.fulfill({json:{success:true,data,traceId:'layout',timestamp:new Date().toISOString()}});
 });await page.goto('/');await page.getByRole('menuitem',{name:'租户管理',exact:true}).click();await expect(page.getByRole('heading',{name:'租户开通',exact:true})).toBeFocused();
 for(const width of [375,768,1024,1440]){await page.setViewportSize({width,height:900});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBeTruthy();}
 await page.setViewportSize({width:375,height:900});await page.getByRole('button',{name:'打开平台导航',exact:true}).click();await expect(page.getByRole('dialog',{name:'平台导航',exact:true})).toBeVisible();await page.keyboard.press('Escape');await expect(page.getByRole('dialog',{name:'平台导航',exact:true})).toBeHidden();await expect(page.getByRole('button',{name:'打开平台导航',exact:true})).toBeFocused();
 await page.getByRole('button',{name:'打开平台导航',exact:true}).click();await page.getByRole('menuitem',{name:'平台账号',exact:true}).click();await expect(page.getByRole('dialog',{name:'平台导航',exact:true})).toBeHidden();await expect(page.getByRole('heading',{name:'平台账号',exact:true})).toBeFocused();await page.screenshot({path:'../../../output/playwright/console-ui/mock-mobile.png',fullPage:true});
 await page.setViewportSize({width:1440,height:1000});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:'../../../output/playwright/console-ui/mock-desktop.png',fullPage:true});
});
