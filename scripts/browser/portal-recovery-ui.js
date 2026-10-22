// Run after invitation-ui-fixture.js; mocked UI evidence, not a backend acceptance substitute.
async page => {
  await page.evaluate(()=>sessionStorage.clear());await page.reload();
  let version=0, state=null, failed=false, canRecover=true, writes=0;
  const account=()=>({id:'901',username:'portal.contact',displayName:'联系人',status:'ACTIVE',version,liveSessions:0});
  await page.route('**/api/suppliers/900/portal-accounts**',async route=>{
    const request=route.request(),path=new URL(request.url()).pathname;
    const reply=(status,data,message)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify({success:status===200,data,error:message?{message}:undefined})});
    if(path.endsWith('/ui-mode')){const body=request.postDataJSON();failed=body.failed??failed;canRecover=body.canRecover??canRecover;return reply(200,null);}
    if(path.endsWith('/password-recovery')&&request.method()==='GET')return failed?reply(503,null,'模拟恢复状态读取失败'):reply(200,state);
    if(request.method()==='GET')return reply(200,{canManage:true,canRecover,accounts:{page:1,size:20,total:1,items:[account()]}});
    const body=request.postDataJSON();if(body.version!==version||!body.reason?.trim())throw new Error('Invalid recovery write');
    writes++;version++;
    if(path.endsWith('/revoke')){state={...state,status:'REVOKED'};return reply(200,null);}
    if(body.identityVerified!==true)throw new Error('Identity confirmation missing');
    state={id:String(version),status:'PENDING',expiresAt:'2026-12-01T00:15:00',createdAt:'2026-12-01T00:00:00',createdBy:'1'};
    return reply(200,{token:'A'.repeat(43),expiresAt:state.expiresAt,accountVersion:version});
  });
  await page.getByRole('button',{name:'登录系统'}).click();
  const panel=page.getByRole('region',{name:'供应商门户账号',exact:true});
  await panel.getByRole('button',{name:'门户账号管理',exact:true}).click();
  await panel.getByRole('button',{name:'恢复门户密码',exact:true}).click();
  const recovery=page.getByRole('region',{name:'门户密码恢复',exact:true});
  await recovery.getByText('尚未签发恢复凭证。',{exact:true}).waitFor();
  await recovery.getByRole('button',{name:'确认签发或替换恢复凭证',exact:true}).click();
  await recovery.getByRole('alert').waitFor();
  if(writes || !await recovery.getByRole('alert').evaluate(el=>el===document.activeElement))throw new Error('Invalid form submitted or error not focused');
  await recovery.getByRole('textbox',{name:'密码恢复操作原因'}).fill('模拟身份核实');
  await recovery.getByText('已通过可信渠道核实账号持有人身份',{exact:true}).click();
  await recovery.getByRole('button',{name:'确认签发或替换恢复凭证',exact:true}).click();
  await recovery.getByRole('status').filter({hasText:'仅本次响应可见'}).waitFor();
  await recovery.getByText('最近凭证：',{exact:false}).waitFor();
  const [response]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/password-recovery/revoke')),recovery.getByRole('button',{name:'确认撤销恢复凭证',exact:true}).click()]);
  if(response.status()!==200)throw new Error('Mock revoke failed');
  await recovery.waitFor({state:'hidden'});
  const mode=body=>page.evaluate(body=>fetch('/api/suppliers/900/portal-accounts/ui-mode',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}).then(()=>{}),body);
  await mode({failed:true});await panel.getByRole('button',{name:'恢复门户密码',exact:true}).click();
  await recovery.getByRole('alert').filter({hasText:'模拟恢复状态读取失败'}).waitFor();
  if(await recovery.getByRole('button',{name:'确认签发或替换恢复凭证',exact:true}).isEnabled())throw new Error('Failed read allowed issuance');
  await page.setViewportSize({width:375,height:812});await recovery.scrollIntoViewIfNeeded();
  await page.screenshot({path:'output/playwright/portal-recovery-ui-failure.png',fullPage:true});
  await mode({failed:false});await recovery.getByRole('button',{name:'重读恢复状态'}).click();
  await recovery.getByText('最近凭证：',{exact:false}).waitFor();
  await recovery.getByRole('button',{name:'关闭密码恢复'}).click();
  await mode({canRecover:false});await panel.getByRole('button',{name:'刷新门户账号'}).click();
  if(await panel.getByRole('button',{name:'恢复门户密码',exact:true}).count())throw new Error('Readonly recovery button exposed');
}
