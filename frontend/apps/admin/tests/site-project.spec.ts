import { expect, test, type Page } from '@playwright/test';
const site={id:'101',name:'第一工厂',code:'SITE_A'};
const project={id:'501',organizationId:'101',supplierId:'201',supplierCode:'SUP',supplierName:'供应商',projectCode:'SITE-P',name:'检修项目',type:'MAINTENANCE',plannedStartDate:'2026-10-01',plannedEndDate:'2026-12-01',managerId:'1',status:'PLANNED',version:0,updatedAt:new Date().toISOString()};
async function mock(page:Page,state:{failed?:boolean;denied?:boolean;readonly?:boolean;empty?:boolean}){
 await page.addInitScript(()=>{sessionStorage.setItem('ism.admin.accessToken','mock-project-access');sessionStorage.setItem('ism.admin.actorId','1');});
 await page.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;
 const failure=path==='/api/site-projects'&&(state.failed||state.denied);
 if(path.startsWith('/api/site-projects'))expect(route.request().headers()['x-site-context']).toBe('memory-only-project-context');
 const data=path==='/api/site-preferences/options'?{items:[site],total:1,page:0,size:20}
 :path==='/api/site-context'?{site,contextToken:'memory-only-project-context',expiresAt:new Date(Date.now()+30000).toISOString(),selectionReason:'ONLY_SITE'}
 :path==='/api/site-projects/access'?{canManage:!state.readonly,canChangeStatus:!state.readonly}
 :path==='/api/site-projects'?{items:state.empty?[]:[project],total:state.empty?0:1,page:0,size:20}
 :path==='/api/site-projects/501'?project
 :path==='/api/organizations/current'?{id:'100',name:'总部',code:'HQ',type:'HEADQUARTERS'}
 :path==='/api/configuration/branding'?{systemName:'工业供应商管理系统',logoUrl:'',faviconUrl:'',footerText:''}:[];
 await route.fulfill({status:failure?(state.denied?403:500):200,contentType:'application/json',body:JSON.stringify(failure?{success:false,error:{code:'TEST',message:state.denied?'场站授权已失效':'临时读取失败'},traceId:'test'}:{success:true,data,traceId:'test',timestamp:new Date().toISOString()})});});
 await page.goto('/projects/site-ledger');await page.getByRole('button',{name:'进入场站',exact:true}).click();
}
test('read failure is recoverable, four widths and expiry clear the workspace',async({page})=>{
 await page.clock.install({time:new Date()});const state={failed:true,empty:true};await mock(page,state);
 await expect(page.getByRole('alert').filter({hasText:'临时读取失败'})).toBeVisible();await expect(page.getByText('读取失败，请重新查询',{exact:true})).toBeVisible();state.failed=false;
 await page.getByRole('button',{name:'查询项目',exact:true}).click();await expect(page.getByText('当前场站没有符合条件的项目',{exact:true})).toBeVisible();
 for(const width of [375,768,1024,1440]){await page.setViewportSize({width,height:900});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBeTruthy();}
 await page.getByRole('textbox',{name:'项目关键词',exact:true}).focus();await expect(page.getByRole('textbox',{name:'项目关键词',exact:true})).toBeFocused();
 await page.screenshot({path:'../../../output/playwright/site-project/mock-desktop.png',fullPage:true});await page.setViewportSize({width:375,height:812});await page.screenshot({path:'../../../output/playwright/site-project/mock-mobile.png',fullPage:true});
 expect(await page.evaluate(()=>[...Object.values(sessionStorage),...Object.values(localStorage)].includes('memory-only-project-context'))).toBeFalsy();
 await page.clock.fastForward(31000);await expect(page.getByRole('alert').filter({hasText:'场站会话已过期'})).toBeVisible();await expect(page.getByRole('button',{name:'新建场站项目',exact:true})).toHaveCount(0);
});
test('readonly actions stay disabled and authorization loss clears the old project',async({page})=>{
 const state={readonly:true,denied:false};await mock(page,state);await expect(page.getByText('检修项目',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'新建场站项目',exact:true})).toBeDisabled();
 await page.getByRole('button',{name:'详情',exact:true}).click();await expect(page.getByRole('button',{name:'编辑项目',exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'开工',exact:true})).toHaveCount(0);
 state.denied=true;await page.getByRole('button',{name:'查询项目',exact:true}).click();await expect(page.getByRole('alert').filter({hasText:'场站授权已失效'})).toBeVisible();await expect(page.getByText('检修项目',{exact:true})).toHaveCount(0);
});

test('a delayed former query cannot repopulate a freshly entered workspace',async({page})=>{
 const state={empty:false};await mock(page,state);await expect(page.getByText('检修项目',{exact:true})).toBeVisible();
 let release!:()=>void;const hold=new Promise<void>(resolve=>{release=resolve;});let started!:()=>void;const ready=new Promise<void>(resolve=>{started=resolve;});
 await page.route('**/api/site-projects?**',async route=>{if(new URL(route.request().url()).searchParams.get('keyword')!=='delay'){await route.fallback();return;}started();await hold;await route.fulfill({contentType:'application/json',body:JSON.stringify({success:true,data:{items:[{...project,name:'OLD delayed project'}],total:1,page:0,size:20},traceId:'stale'})});});
 await page.getByRole('textbox',{name:'项目关键词',exact:true}).fill('delay');await page.getByRole('button',{name:'查询项目',exact:true}).click();await ready;
 state.empty=true;await page.getByRole('button',{name:'进入场站',exact:true}).click();await expect(page.getByText('当前场站没有符合条件的项目',{exact:true})).toBeVisible();
 const response=page.waitForResponse(r=>new URL(r.url()).searchParams.get('keyword')==='delay');release();await (await response).finished();
 await expect(page.getByText('OLD delayed project',{exact:true})).toHaveCount(0);await expect(page.getByText('当前场站没有符合条件的项目',{exact:true})).toBeVisible();
});
