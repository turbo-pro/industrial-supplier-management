// Mocked UI acceptance only; run after exit-portal-ui-fixture.js.
async page=>{
  const flow=page.getByRole('dialog',{name:'门户邀请界面测试供应商 · 退出流程',exact:true});
  await flow.getByRole('button',{name:'批准',exact:true}).click();
  const prompt=page.getByRole('dialog',{name:'独立退出审批',exact:true});
  await prompt.waitFor();await page.locator('body').ariaSnapshot();
  await prompt.getByText(/停用全部本地门户账号、撤销其会话及有效未消费邀请/).waitFor();
  await prompt.getByRole('button',{name:'确定',exact:true}).click();
  await prompt.getByText('审批意见不能为空',{exact:true}).waitFor();
  await page.setViewportSize({width:375,height:812});
  await page.screenshot({path:'output/playwright/exit-portal-confirm-mobile.png',fullPage:true});
  await prompt.getByRole('button',{name:'取消',exact:true}).click();
  await page.setViewportSize({width:1440,height:1000});
  await flow.getByRole('button',{name:'批准',exact:true}).click();
  await prompt.getByRole('textbox').fill('已确认本地门户一并回收');
  await prompt.getByRole('button',{name:'确定',exact:true}).click();
  await flow.getByRole('cell',{name:'本地业务已关闭',exact:true}).waitFor();
  await flow.getByRole('button',{name:'展开当前行',exact:true}).click();
  await page.locator('body').ariaSnapshot();
  await flow.getByText(/本地门户回收 · 操作人 1/).waitFor();
  await flow.getByText(/处理账号 101，撤销会话记录 2，撤销有效未消费邀请 1/).waitFor();
  await flow.getByText('完成范围：本地业务。外部访问回收：未核验。',{exact:true}).waitFor();
  await page.screenshot({path:'output/playwright/exit-portal-timeline.png',fullPage:true});
  return {mocked:true,confirmation:true,requiredReason:true,timelineCounts:true,externalNotVerified:true};
}
