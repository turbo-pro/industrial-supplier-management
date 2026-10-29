async page => {
  const card=page.locator('.site-context-preview');await card.getByRole('button',{name:'校验场站预览',exact:true}).click();
  await card.getByRole('alert').filter({hasText:'当前场站授权已失效'}).waitFor();
  if(await card.getByRole('status').filter({hasText:'已校验场站：'}).count())throw new Error('Revoked context still appeared validated');
  if(await card.getByRole('button',{name:'校验场站预览',exact:true}).isEnabled())throw new Error('Revoked context was not discarded');
  await page.screenshot({path:'output/playwright/site-context/revoked.png',fullPage:true});
}
