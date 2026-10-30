import { expect, test, type Page } from '@playwright/test';
async function mock(page:Page,state:{readonly?:boolean;empty?:boolean;fail?:boolean;drop?:boolean;created?:boolean;denyRetry?:boolean;createOnly?:boolean}={}){
 const requests:{key:string|undefined;body:unknown}[]=[];const option={id:'101',packageCode:'CHEM',packageName:'化工企业版',versionNo:1,versionName:'正式版'};
 const tenant={id:'201',code:'NEW-CHEM',name:'新建化工企业',status:'PROVISIONING',initializationStatus:'PENDING',timezone:'Asia/Shanghai',locale:'zh-CN',packageVersionId:'101',version:0};
 await page.addInitScript(()=>sessionStorage.setItem('ism.console.accessToken','mock-creation'));
 await page.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname,method=route.request().method();
  if(path==='/api/console/tenants'&&method==='GET'&&state.createOnly)throw new Error('Create-only operator must not request the tenant list');
  if(path==='/api/console/packages')throw new Error('Tenant creation must not request the full package catalog');
  if(path==='/api/console/tenants/package-options'&&state.fail){await route.fulfill({status:500,contentType:'application/json',body:JSON.stringify({success:false,error:{code:'TEST',message:'候选读取失败'}})});return;}
  if(path==='/api/console/tenants'&&method==='POST'){requests.push({key:route.request().headers()['idempotency-key'],body:route.request().postDataJSON()});state.created=true;if(state.denyRetry&&requests.length>1){await route.fulfill({status:403,contentType:'application/json',body:JSON.stringify({success:false,error:{code:'FORBIDDEN',message:'访问已撤回'}})});return;}if(state.drop){state.drop=false;await route.abort('failed');return;}}
  const data=path==='/api/console/auth/me'?{id:'1',username:'creator',displayName:'开通人员',passwordChangeRequired:false,passwordChangeRecommended:false,permissions:state.createOnly?['platform:tenant:create']:state.readonly?['platform:tenant:view']:['platform:tenant:create','platform:tenant:view','platform:tenant:initialize']}
  :path==='/api/console/tenants/package-options'?{total:state.empty?0:1,page:0,size:20,items:state.empty?[]:[option]}
  :path==='/api/console/tenants'?(method==='POST'?tenant:state.created?[tenant]:[]):[];
  await route.fulfill({contentType:'application/json',body:JSON.stringify({success:true,data,traceId:'creation',timestamp:new Date().toISOString()})});
 });await page.goto('/');await expect(page.getByRole('heading',{name:'套餐与模块',exact:true})).toBeVisible();return requests;
}
test('lost create response replays identical request then offers initialization',async({page})=>{
 const requests=await mock(page,{drop:true});await page.getByRole('button',{name:'新建租户',exact:true}).click();await page.getByLabel('租户编码',{exact:true}).fill('NEW-CHEM');await page.getByLabel('租户名称',{exact:true}).fill('新建化工企业');await page.getByLabel('初始套餐版本',{exact:true}).selectOption('101');await page.getByRole('button',{name:'创建并继续开通',exact:true}).click();
 await expect(page.getByRole('alert').filter({hasText:'创建结果尚未确认'})).toBeVisible();await expect(page.getByLabel('租户名称',{exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'取消新建',exact:true})).toBeDisabled();
 await page.getByRole('button',{name:'使用原请求重试',exact:true}).click();await expect(page.getByRole('heading',{name:'初始化租户：新建化工企业',exact:true})).toBeVisible();expect(requests).toHaveLength(2);expect(requests[1]).toEqual(requests[0]);expect(requests[0].key).toBeTruthy();
});
test('creation candidates recover from failure, support empty results and fit four widths',async({page})=>{
 const state={fail:true,empty:false};await mock(page,state);await page.getByRole('button',{name:'新建租户',exact:true}).click();await expect(page.getByRole('alert').filter({hasText:'候选读取失败'})).toBeVisible();state.fail=false;state.empty=true;await page.getByRole('button',{name:'查询可选套餐',exact:true}).click();await expect(page.getByText('本次查询无可选的已发布版本，请调整查询或联系套餐管理员。',{exact:true})).toBeVisible();
 state.empty=false;await page.getByRole('button',{name:'查询可选套餐',exact:true}).click();await expect(page.getByRole('option',{name:'化工企业版（CHEM） · V1 正式版',exact:true})).toHaveCount(1);
 for(const width of [375,768,1024,1440]){await page.setViewportSize({width,height:900});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBeTruthy();}
 await page.getByLabel('租户编码',{exact:true}).focus();await expect(page.getByLabel('租户编码',{exact:true})).toBeFocused();await page.screenshot({path:'../../../output/playwright/tenant-create/mock-desktop.png',fullPage:true});await page.setViewportSize({width:375,height:812});await page.screenshot({path:'../../../output/playwright/tenant-create/mock-mobile.png',fullPage:true});
});
test('readonly operator cannot open creation and remains logged in without catalog permission',async({page})=>{
 await mock(page,{readonly:true});await expect(page.getByRole('button',{name:'新建租户',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'退出登录',exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'套餐版本',exact:true})).toHaveCount(0);
});

test('a forbidden retry does not erase an earlier unknown creation result',async({page})=>{
 const state={drop:true,denyRetry:true};const requests=await mock(page,state);await page.getByRole('button',{name:'新建租户',exact:true}).click();await page.getByLabel('租户编码',{exact:true}).fill('NEW-CHEM');await page.getByLabel('租户名称',{exact:true}).fill('新建化工企业');await page.getByLabel('初始套餐版本',{exact:true}).selectOption('101');await page.getByRole('button',{name:'创建并继续开通',exact:true}).click();await expect(page.getByRole('alert').filter({hasText:'创建结果尚未确认'})).toBeVisible();
 await page.getByRole('button',{name:'使用原请求重试',exact:true}).click();await expect(page.getByRole('alert').filter({hasText:'访问权限已变化，原创建结果仍未确认'})).toBeVisible();await expect(page.getByLabel('租户名称',{exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'取消新建',exact:true})).toBeDisabled();expect(requests[1]).toEqual(requests[0]);state.denyRetry=false;await page.getByRole('button',{name:'使用原请求重试',exact:true}).click();await expect(page.getByRole('heading',{name:'初始化租户：新建化工企业',exact:true})).toBeVisible();expect(requests[2]).toEqual(requests[0]);
});

test('create-only operator can submit without catalog or tenant-list privileges',async({page})=>{
 await mock(page,{createOnly:true});await page.getByRole('button',{name:'新建租户',exact:true}).click();await page.getByLabel('租户编码',{exact:true}).fill('NEW-CHEM');await page.getByLabel('租户名称',{exact:true}).fill('新建化工企业');await page.getByLabel('初始套餐版本',{exact:true}).selectOption('101');await page.getByRole('button',{name:'创建并继续开通',exact:true}).click();await expect(page.getByRole('status')).toContainText('租户 NEW-CHEM 已创建，待有初始化权限的管理员继续开通');await expect(page.getByRole('heading',{name:'初始化租户：新建化工企业',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'退出登录',exact:true})).toBeVisible();
});
