// Run after invitation-ui-fixture.js; UI-only mocks, not an exit backend E2E.
async page => {
  let application={id:'910',supplierId:'900',type:'NORMAL',reason:'合作结束',evidenceFileId:'920',status:'SUBMITTED',createdBy:'2',createdAt:'2026-10-22T00:00:00',version:0,localReady:true,items:[],entityTotal:0,entities:[],events:[]};
  await page.route('**/api/suppliers/900/exit-applications**',async route=>{
    const path=new URL(route.request().url()).pathname;
    let data={total:1,page:0,size:20,canApply:false,items:[application]};
    if(path.endsWith('/review')){
      const command=route.request().postDataJSON();
      if(command.version!==0||command.decision!=='APPROVE'||!command.comment.trim())throw new Error('Invalid approval');
      application={...application,status:'BUSINESS_CLOSED',version:1,result:{id:'930',completionScope:'LOCAL_BUSINESS',accessRecoveryStatus:'NOT_VERIFIED'},events:[{id:'940',action:'LOCAL_PORTAL_RECOVERY',comment:'本地门户回收：处理账号 101，撤销会话记录 2，撤销有效未消费邀请 1；其他外部访问仍未核验',actorId:'1',createdAt:'2026-10-22T00:01:00'}]};
      data=application;
    }else if(path.endsWith('/reminder-failures'))data={total:0,page:0,size:20,items:[]};
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({success:true,data})});
  });
  if(await page.getByRole('button',{name:'登录系统',exact:true}).count())await page.getByRole('button',{name:'登录系统',exact:true}).click();
  await page.getByRole('button',{name:'登录系统',exact:true}).waitFor({state:'hidden'});
  const close=page.getByRole('button',{name:'关闭',exact:true});
  if(await close.count())await close.click();
  await page.locator('body').ariaSnapshot();
  const direct=page.getByRole('button',{name:'退出流程',exact:true});
  if(await direct.count())await direct.click();
  else{await page.getByRole('button',{name:'门户邀请界面测试供应商的更多操作',exact:true}).click();await page.getByRole('menuitem',{name:'退出流程',exact:true}).click();}
  await page.getByRole('dialog',{name:'门户邀请界面测试供应商 · 退出流程',exact:true}).waitFor();
}
