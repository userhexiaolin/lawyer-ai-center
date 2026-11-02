const { chromium } = require('playwright');
const { expect } = require('@playwright/test');
const fs = require('node:fs/promises');
const path = require('node:path');
const output = path.resolve('../artifacts/template-type-20261026');
const origin = 'http://127.0.0.1:5197';
let browser;
(async () => {
 browser = await chromium.launch({ channel:'chrome', headless:true });
 const page = await browser.newPage({viewport:{width:1440,height:900}});
 const errors = []; page.on('pageerror',error=>errors.push(error.message));
 const results = [];
 async function shot(name) { await expect(page.locator('.ant-modal-wrap:visible')).toHaveCount(0); await page.screenshot({path:path.join(output,'screenshots',name+'.png'),fullPage:true,animations:'disabled'}); }
 async function visit(query='') { await page.goto(origin+query); await expect(page.getByRole('heading',{name:'模板类型',exact:true})).toBeVisible(); }
 async function ready() { await expect(page.getByRole('cell',{name:'劳动争议文书',exact:true})).toBeVisible(); }
 async function dump(name) { await fs.writeFile(path.join(output,name+'.dom.txt'),await page.locator('body').innerText()); }
 for (const [width,height] of [[1366,768],[1440,900],[1920,1080]]) {
   await page.setViewportSize({width,height}); await visit(); await ready(); await shot(`list-${width}x${height}`);
   const geometry=await page.evaluate(()=>({width:innerWidth,documentWidth:document.documentElement.scrollWidth,bodyWidth:document.body.scrollWidth}));
   if(geometry.documentWidth>width) throw new Error('Document overflow '+JSON.stringify(geometry));
   await expect(page.getByRole('button',{name:'新增类型',exact:true})).toBeInViewport();
   results.push({viewport:`${width}x${height}`,geometry,result:'PASS'});
   await visit('?scenario=list-error'); await expect(page.getByText('模板类型加载失败',{exact:true})).toBeVisible(); await shot(`list-error-${width}x${height}`);
   await page.getByRole('button',{name:'重新加载',exact:true}).click(); await ready();
 }
 await page.setViewportSize({width:1440,height:900}); await visit(); await ready();
 // Main path: create -> query with Enter -> edit -> preserve dirty data -> save -> stop -> delete.
 await page.getByRole('button',{name:'新增类型',exact:true}).click();
 await page.locator('#type-name').fill('   ');
 await page.getByRole('button',{name:'保存类型',exact:true}).click();
 await expect(page.getByText('请填写类型名称',{exact:true})).toBeVisible();
 await page.locator('#type-name').fill('合成验收分类');
 await page.locator('#type-remark').fill('只在隔离夹具中保存');
 await page.getByRole('button',{name:'保存类型',exact:true}).click();
 await expect(page.getByRole('cell',{name:'合成验收分类',exact:true})).toBeVisible();
 await page.locator('#filter-type-name').fill('合成验收'); await page.locator('#filter-type-name').press('Enter');
 await expect(page.locator('tbody tr.ant-table-row')).toHaveCount(1);
 await page.getByRole('button',{name:'编辑',exact:true}).click();
 await page.locator('#type-name').fill('合成验收分类（已编辑）');
 await page.getByRole('button',{name:/^取\s*消$/}).click();
 await page.getByRole('button',{name:'继续编辑',exact:true}).click();
 await expect(page.locator('#type-name')).toHaveValue('合成验收分类（已编辑）');
 await shot('editor-1440x900');
 await page.getByRole('button',{name:'保存类型',exact:true}).click();
 await expect(page.getByRole('cell',{name:'合成验收分类（已编辑）',exact:true})).toBeVisible();
 await expect(page.locator('#filter-type-name')).toHaveValue('合成验收');
 await page.getByRole('button',{name:'停用',exact:true}).click();
 await page.getByRole('button',{name:'确认停用',exact:true}).click();
 await expect(page.locator('tbody .ant-tag').filter({hasText:'停用'})).toBeVisible();
 await dump('main-path');
 await page.getByRole('button',{name:'删除',exact:true}).click(); await page.getByRole('button',{name:'确认删除',exact:true}).click();
 await expect(page.getByText('没有匹配的模板类型',{exact:true})).toBeVisible();
 results.push({path:'create-query-edit-discard-save-toggle-delete',result:'PASS'});
 // Save timeout preserves all inputs and retry completes.
 await visit('?scenario=save-error'); await ready();
 await page.getByRole('row').filter({hasText:'劳动争议文书'}).getByRole('button',{name:'编辑',exact:true}).click();
 await page.locator('#type-name').fill('劳动争议文书（失败后输入保留）');
 await page.getByRole('button',{name:'保存类型',exact:true}).click();
 await expect(page.getByText('类型尚未保存',{exact:true})).toBeVisible();
 await expect(page.locator('#type-name')).toHaveValue('劳动争议文书（失败后输入保留）');
 await shot('save-error-1440x900'); await dump('save-error');
 await page.getByRole('button',{name:'保存类型',exact:true}).click();
 await expect(page.getByRole('cell',{name:'劳动争议文书（失败后输入保留）',exact:true})).toBeVisible();
 results.push({path:'save-timeout-preserve-retry',result:'PASS'});
 // Associated records prevent deletion and keep row visible.
 await visit(); await ready();
 await page.getByRole('row').filter({hasText:'有关联模板的分类'}).getByRole('button',{name:'删除',exact:true}).click();
 await page.getByRole('button',{name:'确认删除',exact:true}).click();
 await expect(page.getByText('该类型下仍有模板，请先删除模板或调整模板类型',{exact:true})).toBeVisible();
 await expect(page.getByRole('cell',{name:'有关联模板的分类',exact:true})).toBeVisible(); await shot('delete-rejected-1440x900');await dump('delete-rejected');
 results.push({path:'associated-template-delete-rejected',result:'PASS'});
 await visit('?scenario=conflict'); await ready();
 await page.getByRole('row').filter({hasText:'劳动争议文书'}).getByRole('button',{name:'编辑',exact:true}).click();
 await page.locator('#type-name').fill('冲突时仍保留'); await page.getByRole('button',{name:'保存类型',exact:true}).click();
 await expect(page.getByText('类型尚未保存',{exact:true})).toBeVisible(); await expect(page.locator('#type-name')).toHaveValue('冲突时仍保留'); await dump('conflict');
 results.push({path:'409-keeps-input',result:'PASS'});
 // Existing restrictions, empty state, keyboard and dark tokens.
 await visit(); await ready();
 await page.getByRole('row').filter({hasText:'民事起诉状'}).getByRole('button',{name:'编辑',exact:true}).click();
 await expect(page.locator('#type-name')).toBeDisabled(); await page.keyboard.press('Escape');
 await expect(page.locator('.ant-drawer-open')).toHaveCount(0);
 await expect(page.getByRole('row').filter({hasText:'民事起诉状'}).getByRole('button',{name:'编辑',exact:true})).toBeFocused();
 await page.locator('#filter-type-name').focus(); await page.keyboard.press('Tab');
 const active=await page.evaluate(()=>({tag:document.activeElement.tagName,id:document.activeElement.id,outline:getComputedStyle(document.activeElement).outlineStyle}));
 results.push({path:'builtin-name-disabled-escape-focus-return-tab',active,result:'PASS'});
 await visit('?scenario=readonly'); await ready(); await expect(page.getByRole('button',{name:'新增类型',exact:true})).toHaveCount(0); await expect(page.getByRole('button',{name:'编辑',exact:true})).toHaveCount(0);await dump('readonly');
 await visit('?scenario=empty');await expect(page.getByText('暂无模板类型',{exact:true})).toBeVisible();await shot('empty-1440x900');
 await visit('?theme=dark');await ready();await shot('dark-1440x900');
 if(errors.length)throw new Error('Browser errors: '+errors.join('\n'));
 await fs.writeFile(path.join(output,'ui-results.json'),JSON.stringify({browser:await browser.version(),results,pageErrors:errors},null,2));
 await browser.close(); console.log(JSON.stringify(results,null,2));
})().catch(async error=>{console.error(error);await browser?.close();process.exitCode=1;});
