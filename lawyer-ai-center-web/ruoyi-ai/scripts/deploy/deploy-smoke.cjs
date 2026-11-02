// Ordinary-user real API smoke. No administrator token, database copy or mocked API.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const base = (process.env.STAGE4_BASE_URL || 'http://127.0.0.1:6039').replace(/\/$/, '');
const out = path.resolve(process.env.STAGE4_EVIDENCE_DIR || '../../../scripts/development/stage4-deploy-evidence');
const clientid = process.env.STAGE4_CLIENT_ID || 'e5cd7e4891bf95d1d19206ce24a7b32e';
const tenantId = process.env.STAGE4_TENANT_ID || '000000';
const accountFile = process.env.STAGE4_SMOKE_ACCOUNT_FILE;
let token, reportId, caseId;
const summary = { operator: process.env.STAGE4_OPERATOR, startedAt: new Date().toISOString(), baseUrl: base, database: process.env.STAGE4_EXPECTED_DATABASE, checks: {}, syntheticDataOnly: true };
fs.mkdirSync(out, { recursive: true });
const record = () => fs.writeFileSync(path.join(out, 'deploy-smoke-' + new URL(base).port + '.json'), JSON.stringify(summary, null, 2));
async function api(url, method = 'GET', body) {
  const response = await fetch(base + url, { method, headers: { clientid, ...(token ? { Authorization: 'Bearer ' + token } : {}), 'content-type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(180000) });
  const data = await response.json();
  if (!response.ok || data.code !== 200) throw Error(url + ': ' + (data.msg || response.status));
  return data.data;
}
async function binary(url, body, filename) {
  const response = await fetch(base + url, { method: body ? 'POST' : 'GET', headers: { clientid, Authorization: 'Bearer ' + token, 'content-type': 'application/json' }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(90000) });
  const bytes = Buffer.from(await response.arrayBuffer());
  if (!response.ok || bytes.subarray(0, 2).toString() !== 'PK') throw Error(url + ': Word response invalid');
  fs.writeFileSync(path.join(out, filename), bytes);
  return bytes.length;
}
async function check(name, fn) {
  const started = Date.now();
  try { summary.checks[name] = { pass: true, ...await fn(), milliseconds: Date.now() - started }; }
  catch (error) { summary.checks[name] = { pass: false, error: error.message, milliseconds: Date.now() - started }; record(); throw error; }
  record(); console.log('PASS ' + name);
}
async function main() {
  for (let n = 0; n < 30; n++) {
    try { const response = await fetch(base + '/auth/tenant/list', { signal: AbortSignal.timeout(2000) }); if (response.ok && (await response.json()).code === 200) break; } catch {}
    if (n === 29) throw Error('Service did not become ready');
    await new Promise(resolve => setTimeout(resolve, 10000));
  }
  await check('loginOrdinaryUser', async () => {
    let account = process.env.STAGE4_SMOKE_USERNAME && { username: process.env.STAGE4_SMOKE_USERNAME, password: process.env.STAGE4_SMOKE_PASSWORD };
    if (!account && accountFile && fs.existsSync(accountFile)) account = JSON.parse(fs.readFileSync(accountFile, 'utf8'));
    if (!account) {
      if (!accountFile) throw Error('Provide ordinary-user credentials or a private STAGE4_SMOKE_ACCOUNT_FILE for registration');
      account = { username: 'deploy53_' + Date.now(), password: crypto.randomBytes(10).toString('hex') };
      await api('/auth/register', 'POST', { ...account, tenantId, clientId: clientid, grantType: 'password', userType: 'sys_user' });
      fs.mkdirSync(path.dirname(accountFile), { recursive: true }); fs.writeFileSync(accountFile, JSON.stringify(account), { mode: 0o600 });
    }
    token = (await api('/auth/login', 'POST', { ...account, tenantId, clientId: clientid, grantType: 'password' })).access_token;
    const info = await api('/system/user/getInfo');
    if (!token || String(info.user.userId) === '1' || !info.roles.includes('user') || info.roles.some(role => /admin|super/i.test(role)) || info.permissions.includes('*:*:*')) throw Error('Smoke requires an ordinary user; administrative identity rejected');
    return { username: account.username, userId: String(info.user.userId), roles: info.roles, tenantId, administrator: false };
  });
  await check('reportCreateAddGenerateDownload', async () => {
    const report = await api('/legal/report', 'POST', { title: '部署冒烟53：合成报告' }); reportId = String(report.id);
    await api('/legal/report/' + reportId + '/items', 'POST', { itemType: 'custom_text', snapshotTitle: '部署测试条目', snapshotContent: '仅用于部署冒烟，不涉及真实客户事实。' });
    const generated = await api('/legal/report/' + reportId + '/generate', 'POST');
    const bytes = await binary('/legal/report/' + reportId + '/download', null, 'search-report-' + new URL(base).port + '.docx');
    const detail = await api('/legal/report/' + reportId);
    if (detail.items.length !== 1 || detail.report.status !== 'generated') throw Error('Report generation not persisted');
    return { reportId, items: detail.items.length, status: detail.report.status, bytes, generated: !!generated };
  });
  await check('caseTaskChecklist', async () => {
    caseId = String(await api('/lawyer/case', 'POST', { title: '部署冒烟53：合成案件', status: '进行中', description: '仅测试任务和检查项，不包含客户数据' }));
    const task = await api('/lawyer/case/' + caseId + '/tasks', 'POST', { title: '部署测试任务' });
    const item = await api('/lawyer/case/task/' + task.id + '/checklist', 'POST', { content: '核对部署检查项' });
    await api('/lawyer/case/task/' + task.id + '/checklist/' + item.id + '/toggle', 'POST', { checked: true });
    const progress = await api('/lawyer/case/' + caseId + '/tasks');
    if (progress.checkedItems !== 1 || !progress.tasks[0].checklist[0].checked) throw Error('Checklist did not persist');
    return { caseId, taskId: String(task.id), checkedItems: progress.checkedItems, totalItems: progress.totalItems };
  });
  await check('searchWithMetadataAndFragment', async () => {
    const sessionId = await api('/system/session', 'POST', { sessionTitle: '案情检索 - 部署冒烟53' });
    const found = await api('/legal/fact-search', 'POST', { sessionId, text: '合同履行违约，对方无限期拖延交付，检索民法典关于违约责任、履行和损失赔偿的条款。', type: 'law' });
    const hit = found.hits.find(hit => hit.reference?.lawName && hit.reference?.articleNo && hit.reference?.status && hit.reference?.fragmentId);
    if (!hit) throw Error('No actual knowledge hit carrying lawName/articleNo/status/fragmentId');
    const detail = await api('/legal/citations/fragments/' + encodeURIComponent(hit.reference.fragmentId));
    if (!detail.content) throw Error('Actual fragment detail empty');
    return { sessionId: String(sessionId), hits: found.hits.length, extractionStatus: found.extractionStatus, retrievalMode: found.retrievalMode, reference: hit.reference, detailCharacters: detail.content.length };
  });
  await check('issuesReviewWordEndpoint', async () => {
    const sessionId = await api('/system/session', 'POST', { sessionTitle: '合同审查 - 部署冒烟53', reviewSession: true });
    const content = JSON.stringify({ contractType: '测试合同', summary: '部署测试：补充交付期限', issues: [{ id: 'D1', riskLevel: 'high', clauseTitle: '交付期限', quote: '乙方可无限期延迟交付', location: { positionText: '第二条' }, problem: '履行时间不明确', suggestion: '协商明确交付日期', lawRefs: [], caseRefs: [] }] });
    const bytes = await binary('/system/contract-review/report', { sessionId, content }, 'review-issues-' + new URL(base).port + '.docx');
    return { sessionId: String(sessionId), bytes, input: 'synthetic complete issues fixture; no model call' };
  });
  await check('opinionHistoryAndWordEndpoint', async () => {
    const sessionId = await api('/system/session', 'POST', { sessionTitle: '法律意见书 - 部署冒烟53' });
    await api('/legal/opinion/' + sessionId);
    const content = JSON.stringify({ title: '部署测试意见书', sections: ['timeline', 'issues', 'analysis', 'risks', 'strategy', 'references'].map(key => ({ key, content: '合成部署测试，无真实案情或法律结论。', citations: [] })) });
    const bytes = await binary('/legal/opinion/export', { sessionId, content, format: 'docx' }, 'opinion-' + new URL(base).port + '.docx');
    return { sessionId: String(sessionId), bytes, input: 'synthetic six-section fixture; no model call' };
  });
  await check('cleanupSyntheticRecords', async () => {
    if (reportId) await api('/legal/report/' + reportId, 'DELETE');
    if (caseId) await api('/lawyer/case/' + caseId, 'DELETE');
    return { reportSoftDeleted: !!reportId, caseSoftDeleted: !!caseId, sessionsAndGeneratedOss: 'retained under existing retention policy', smokeAccount: 'ordinary user retained for repeatable verification' };
  });
  summary.pass = true; summary.finishedAt = new Date().toISOString(); record();
  await api('/auth/logout', 'POST');
}
main().catch(error => { summary.pass = false; summary.error = error.message; summary.finishedAt = new Date().toISOString(); record(); console.error(error.message); process.exitCode = 1; });
