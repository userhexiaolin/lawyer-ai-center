const { chromium } = require('playwright');
const { expect } = require('@playwright/test');
const fs = require('node:fs/promises');
const path = require('node:path');
const output = path.resolve('../artifacts/template-field-workflow-20261026');
let browser;
(async()=>{
 await fs.mkdir(path.join(output,'screenshots'),{recursive:true});
 browser=await chromium.launch({channel:'chrome',headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:900}});const errors=[];const results=[];
 page.on('pageerror',error=>errors.push(error.message));
 const visit=async(query='')=>{await page.goto('http://127.0.0.1:5198/'+query);await expect(page.getByRole('heading',{name:'模板管理',exact:true})).toBeVisible();};
 const row=()=>page.getByRole('row').filter({hasText:'合同补充协议模板'});
 const shot=async(name)=>page.screenshot({path:path.join(output,'screenshots',name+'.png'),fullPage:true,animations:'disabled'});
 for(const [width,height] of [[1366,768],[1440,900],[1920,1080]]){
  await page.setViewportSize({width,height});await visit();await expect(row()).toBeVisible();await shot(`list-${width}x${height}`);
  const geometry=await page.evaluate(()=>({width:innerWidth,document:document.documentElement.scrollWidth}));if(geometry.document>width)throw new Error('Whole page overflow '+JSON.stringify(geometry));
  await row().getByRole('button',{name:'AI解析字段',exact:true}).click();
  await expect(page.locator('.ant-modal-wrap:visible')).toHaveCount(0);
  await expect(row().getByText(/正在.*解析|正在.*识别/).first()).toBeVisible({timeout:4500});await shot(`parsing-${width}x${height}`);
  await expect(row().getByText('规则候选待核对',{exact:true})).toBeVisible({timeout:10000});
  await row().getByRole('button',{name:'编辑模板字段',exact:true}).click();
  const dialog=page.getByRole('dialog');await expect(dialog.getByText('编辑模板字段',{exact:true})).toBeVisible();
  await dialog.getByRole('button',{name:/^确\s*定$/}).click();
  await expect(page.locator('.ant-message')).toContainText('请核对填写字段或段落，并勾选确认后保存');
  const confirmation=dialog.getByRole('checkbox',{name:'已核对启用字段的位置、含义、类型及单位',exact:true});
  await expect(confirmation).toBeFocused();await expect(confirmation).not.toBeChecked();await expect(dialog.locator('#field-confirmation-error')).toBeInViewport();await shot(`confirmation-error-${width}x${height}`);
  await page.waitForTimeout(3300);await expect(page.locator('.ant-message-notice')).toHaveCount(0);
  await dialog.getByRole('button',{name:/^确\s*定$/}).click();await expect(page.locator('.ant-message')).toContainText('请核对填写字段');await expect(confirmation).toBeFocused();
  await confirmation.check();await dialog.getByRole('button',{name:/^确\s*定$/}).click();await expect(page.locator('.ant-modal-wrap:visible')).toHaveCount(0);await expect(row().getByText('字段已确认',{exact:true})).toBeVisible();
  results.push({viewport:`${width}x${height}`,geometry,flow:'PASS',reviewValidation:'PASS',repeatMessage:'PASS',focus:'PASS'});
 }
 await visit('?scenario=parse-error');await row().getByRole('button',{name:'AI解析字段',exact:true}).click();await expect(row().getByText('解析失败',{exact:true})).toBeVisible({timeout:10000});await expect(row().getByRole('button',{name:'AI解析字段',exact:true})).toBeEnabled();await shot('parse-failure');results.push({failure:'parse error and retry action',result:'PASS'});
 await visit('?scenario=field-conflict');await row().getByRole('button',{name:'编辑模板字段',exact:true}).click();await expect(page.getByText('模板版本已变化，请刷新后重试，原字段未被覆盖',{exact:true})).toBeVisible();await expect(page.locator('.ant-modal-wrap:visible')).toHaveCount(0);await shot('version-conflict');results.push({failure:'version conflict',result:'PASS'});
 if(errors.length)throw new Error(errors.join('\n'));await fs.writeFile(path.join(output,'ui-results.json'),JSON.stringify({results,errors},null,2));console.log(JSON.stringify({result:'PASS',checks:results.length,errors}));
})().catch(async(error)=>{console.error(error);process.exitCode=1;}).finally(async()=>{if(browser)await browser.close();});
