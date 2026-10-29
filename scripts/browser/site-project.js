async page => {
 const fixture=__FIXTURE__,base=new URL(page.url()).origin;
 await page.getByLabel('租户编码').fill(fixture.code);await page.getByLabel('用户名',{exact:true}).fill('pref-admin');await page.getByLabel('密码',{exact:true}).fill('Site#Initial123');
 const login=page.waitForResponse(r=>r.url().endsWith('/api/auth/login')&&r.status()===200);await page.getByRole('button',{name:'登录系统',exact:true}).click();await login;
 await page.goto(base+'/projects/site-ledger');const area=page.locator('.site-project-page');
 await area.locator('.site-choice .el-select__wrapper').click();await page.getByRole('option',{name:'第一工厂（MAIN_SITE）',exact:true}).click();
 const issued=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/site-context'&&r.status()===200);await page.getByRole('button',{name:'进入场站',exact:true}).click();const token=(await(await issued).json()).data.contextToken;
 await area.getByText('当前场站没有符合条件的项目',{exact:true}).waitFor();await page.getByRole('button',{name:'新建场站项目',exact:true}).click();
 await page.getByRole('textbox',{name:'项目编码',exact:true}).fill('BROWSER-SITE');await page.getByRole('textbox',{name:'项目名称',exact:true}).fill('浏览器检修');
 const form=page.getByRole('dialog');await form.locator('.el-select__wrapper').first().click();await page.getByRole('option',{name:'场站供应商（SITE-SUP）',exact:true}).click();
 await page.getByLabel('计划开始',{exact:true}).fill('2026-10-01');await page.getByLabel('计划结束',{exact:true}).fill('2026-12-01');
 await page.getByRole('button',{name:'保存项目',exact:true}).click();await area.getByText('BROWSER-SITE',{exact:true}).waitFor();
 await area.getByRole('button',{name:'详情',exact:true}).click();await area.getByRole('button',{name:'编辑项目',exact:true}).click();await page.getByRole('textbox',{name:'项目名称',exact:true}).fill('浏览器检修更新');await page.getByRole('button',{name:'保存项目',exact:true}).click();
 await area.getByText('浏览器检修更新',{exact:true}).waitFor();await area.getByRole('button',{name:'详情',exact:true}).click();await area.getByRole('button',{name:'开工',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await area.locator('.project-detail').getByText('进行中',{exact:true}).waitFor();
 const boundaries=await page.evaluate(async(token)=>{const bearer=sessionStorage.getItem('ism.admin.accessToken');const without=await fetch('/api/site-projects',{headers:{Authorization:`Bearer ${bearer}`}});const legacy=await fetch('/api/projects',{headers:{Authorization:`Bearer ${bearer}`,'X-Site-Context':token}});return without.status===400&&legacy.status===422&&![...Object.values(sessionStorage),...Object.values(localStorage)].includes(token);},token);if(!boundaries)throw new Error('Project workspace context boundary failed');
 await page.screenshot({path:'output/playwright/site-project/desktop.png',fullPage:true});
 for(const width of [375,768,1024,1440]){await page.setViewportSize({width,height:900});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw new Error('Project workspace overflow at '+width);}
 await page.setViewportSize({width:375,height:812});await page.screenshot({path:'output/playwright/site-project/mobile.png',fullPage:true});
 await area.locator('.site-choice .el-select__wrapper').click();await page.getByRole('option',{name:'第二工厂（SECOND-SITE）',exact:true}).click();await page.getByRole('button',{name:'进入场站',exact:true}).click();await area.getByText('当前场站没有符合条件的项目',{exact:true}).waitFor();if(await area.getByText('浏览器检修更新',{exact:true}).count())throw new Error('Previous site project remained visible');
 await page.screenshot({path:'output/playwright/site-project/second-site.png',fullPage:true});
}
