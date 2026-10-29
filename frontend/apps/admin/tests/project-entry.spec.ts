import { expect, test, type Page } from '@playwright/test';
const row={id:'501',organizationId:'101',supplierId:'201',supplierName:'供应商',supplierCode:'SUP',projectCode:'OLD-HQ',name:'历史总部项目',type:'MAINTENANCE',plannedStartDate:'2026-01-01',plannedEndDate:'2026-12-31',status:'PLANNED',version:0,updatedAt:new Date().toISOString(),managerId:'1'};
async function mock(page:Page,state:{failed?:boolean;legacy?:boolean;overview?:boolean;details?:boolean;denied?:boolean;hqOnly?:boolean}){
 await page.addInitScript(()=>{sessionStorage.setItem('ism.admin.accessToken','mock-entry');sessionStorage.setItem('ism.admin.actorId','1');});
 await page.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;
 if(path.startsWith('/api/projects'))expect(route.request().headers()['x-site-context']).toBeUndefined();
 const failure=(path==='/api/projects/access-mode'&&state.failed)||(path==='/api/projects'&&state.denied);
 const data=path==='/api/projects/access-mode'?{siteContextRequired:!state.legacy,legacyWritesAllowed:!!state.legacy,canEnterSiteWorkspace:!state.hqOnly,canViewOrganizationProjects:!!state.overview,canViewOrganizationProjectDetails:!!state.details}
 :path==='/api/projects'?{items:[row],total:1,page:0,size:20}:path==='/api/projects/501'?row
 :path==='/api/site-preferences/options'?{items:[],total:0,page:0,size:20}:path==='/api/organizations/current'?{id:'100',name:'总部',code:'HQ',type:'HEADQUARTERS'}
 :path==='/api/configuration/branding'?{systemName:'工业供应商管理系统',logoUrl:'',faviconUrl:'',footerText:''}:[];
 await route.fulfill({status:failure?(state.denied?403:500):200,contentType:'application/json',body:JSON.stringify(failure?{success:false,error:{code:'TEST',message:'入口或权限暂不可用'},traceId:'test'}:{success:true,data,traceId:'test',timestamp:new Date().toISOString()})});});await page.goto('/projects/ledger');
}
test('classified entry defaults to site and organization summary stays readonly',async({page})=>{
 const state={overview:true,details:false};await mock(page,state);await expect(page.getByRole('heading',{name:'场站项目工作区',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'组织项目总览（只读）',exact:true}).click();await expect(page.getByText('历史总部项目',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'查看详情',exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'新建项目',exact:true})).toHaveCount(0);
 for(const width of [375,768,1024,1440]){await page.setViewportSize({width,height:900});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBeTruthy();}
 await page.getByRole('textbox',{name:'组织项目关键词',exact:true}).focus();await expect(page.getByRole('textbox',{name:'组织项目关键词',exact:true})).toBeFocused();
 await page.screenshot({path:'../../../output/playwright/project-entry/mock-desktop.png',fullPage:true});await page.setViewportSize({width:375,height:812});await page.screenshot({path:'../../../output/playwright/project-entry/mock-mobile.png',fullPage:true});
});
test('policy failure has no legacy fallback and can be retried',async({page})=>{
 const state={failed:true,overview:false};await mock(page,state);await expect(page.getByRole('alert').filter({hasText:'入口或权限暂不可用'})).toBeVisible();await expect(page.getByRole('heading',{name:'项目台账',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'新建项目',exact:true})).toHaveCount(0);
 state.failed=false;await page.getByRole('button',{name:'重新读取项目入口',exact:true}).click();await expect(page.getByRole('heading',{name:'场站项目工作区',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'组织项目总览（只读）',exact:true})).toHaveCount(0);
});
test('authorized details are readable, revocation clears former data',async({page})=>{
 const state={overview:true,details:true,denied:false};await mock(page,state);await page.getByRole('button',{name:'组织项目总览（只读）',exact:true}).click();await page.getByRole('button',{name:'查看详情',exact:true}).click();await expect(page.locator('.overview-detail')).toContainText('历史总部项目');
 state.denied=true;await page.getByRole('button',{name:'查询组织项目',exact:true}).click();await expect(page.getByRole('alert').filter({hasText:'入口或权限暂不可用'})).toBeVisible();await expect(page.getByText('历史总部项目',{exact:true})).toHaveCount(0);
});
test('unclassified legacy still enters the established ledger',async({page})=>{
 await mock(page,{legacy:true});await expect(page.getByRole('heading',{name:'项目台账',exact:true})).toBeVisible();await expect(page.getByText('历史总部项目',{exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'场站项目工作区',exact:true})).toHaveCount(0);
});

test('organization-only reader opens readonly overview without a site permission error',async({page})=>{
 await mock(page,{overview:true,details:false,hqOnly:true});await expect(page.getByRole('heading',{name:'组织项目总览',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'场站项目',exact:true})).toHaveCount(0);await expect(page.getByRole('heading',{name:'场站项目工作区',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'查看详情',exact:true})).toBeDisabled();
});
