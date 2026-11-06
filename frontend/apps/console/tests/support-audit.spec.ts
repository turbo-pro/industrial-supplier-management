import { test, expect } from '@playwright/test';
for (const allowed of [true,false]) test(`audit permission ${allowed}`,async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('ism.console.accessToken','audit-test'));
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let data:unknown=[];
  if(path.endsWith('/auth/me'))data={id:'1',displayName:'审计员',passwordChangeRequired:false,permissions:allowed?['platform:support:audit']:[]};
  if(path.endsWith('/support-audit/sessions'))data={items:[{id:'20',requestId:'10',operatorId:'2',tenantId:'3',state:'ACTIVE',expiresAt:'2099-01-01T00:00:00'}],hasMore:false};
  if(path.endsWith('/access-logs'))data={items:[{id:'40',objectId:'50',result:'READ',surface:'SUPPORT_WORKSPACE',traceId:'trace-1',occurredAt:'2026-11-07T00:00:00'}],hasMore:false};
  await route.fulfill({json:{success:true,data}});
 });
 await page.goto('/');
 const panel=page.getByRole('region',{name:'独立支持审计'});
 if(!allowed){await expect(panel).toHaveCount(0);return;}
 await panel.getByRole('button',{name:'读取会话审计',exact:true}).click();
 await panel.getByRole('button',{name:'查看访问记录',exact:true}).click();
 await expect(panel.getByText('trace-1',{exact:true})).toBeVisible();
 await expect(page.getByRole('region',{name:'强制终止支持会话'})).toHaveCount(0);
 await expect(page.getByRole('button',{name:'支持申请工作台',exact:true})).toHaveCount(0);
});
