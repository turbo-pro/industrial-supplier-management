import { test, expect } from '@playwright/test';
for (const allowed of [true,false]) test(`audit permission ${allowed}`,async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('ism.console.accessToken','audit-test'));
 let detailCalls=0;
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;let data:unknown=[];
  if(path.endsWith('/auth/me'))data={id:'1',displayName:'审计员',passwordChangeRequired:false,permissions:allowed?['platform:support:audit']:[]};
  if(path.endsWith('/support-audit/sessions'))data={items:[{id:'20',requestId:'10',operatorId:'2',tenantId:'3',state:'ACTIVE',expiresAt:'2099-01-01T00:00:00'}],hasMore:false};
  if(path.endsWith('/access-logs'))data={items:[{id:'40',objectId:'50',result:'READ',surface:'SUPPORT_WORKSPACE',traceId:'trace-1',occurredAt:'2026-11-07T00:00:00'}],hasMore:false};
  if(path.endsWith('/lifecycle'))data={items:[{id:'60',operatorId:'7',action:'SUPPORT_SESSION_TERMINATE',traceId:'trace-terminate',occurredAt:'2026-11-07T00:01:00'}],hasMore:false};
  if(path.endsWith('/support-audit/sessions/20')){
   if(++detailCalls===1){await route.fulfill({status:503,json:{success:false}});return;}
   data={id:'20',requestId:'10',operatorId:'2',tenantId:'3',decisionId:'4',roleId:'5',persistedState:'REVOKED',state:'REVOKED',createdAt:'2026-11-07T00:00:00',expiresAt:'2026-11-07T01:00:00',revokedAt:null,frozenScope:{tenantAll:false,organizations:['ORG-ONE'],fields:['code','name']}};
  }
  await route.fulfill({json:{success:true,data}});
 });
 await page.goto('/');
 const panel=page.getByRole('region',{name:'独立支持审计'});
 if(!allowed){await expect(panel).toHaveCount(0);return;}
 await panel.getByRole('button',{name:'读取会话审计',exact:true}).click();
 await panel.getByRole('button',{name:'查看访问记录',exact:true}).click();
 await expect(panel.getByText('trace-1',{exact:true})).toBeVisible();
 await panel.getByRole('button',{name:'查看生命周期',exact:true}).click();
 await expect(panel.getByText('管理员强制终止',{exact:true})).toBeVisible();
 await expect(panel.getByText('trace-terminate',{exact:true})).toBeVisible();
 await expect(panel.getByRole('region',{name:'审计访问记录'})).toHaveCount(0);
 await panel.getByRole('button',{name:'查看详情',exact:true}).click();
 await expect(panel.getByRole('alert')).toContainText('会话详情读取失败');
 await panel.getByRole('button',{name:'重新读取详情',exact:true}).click();
 const detail=panel.getByRole('region',{name:'支持会话详情'});
 await expect(detail).toContainText('不代表当前有效访问权限');
 await expect(detail).toContainText('ORG-ONE');
 await expect(detail).toContainText('code、name');
 await panel.getByRole('button',{name:'查看生命周期',exact:true}).click();
 await expect(detail).toHaveCount(0);
 await expect(page.getByRole('region',{name:'强制终止支持会话'})).toHaveCount(0);
 await expect(page.getByRole('button',{name:'支持申请工作台',exact:true})).toHaveCount(0);
});
