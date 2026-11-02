// Run from ruoyi-ai. Secrets are environment-only and never included in evidence.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '../..');
const command = process.argv[2];
const env = { ...process.env };
const local = process.argv.includes('--local');
const requireEnv = key => { if (!env[key]) throw Error('Missing environment variable: ' + key); return env[key]; };
if (local) {
  const config = fs.readFileSync(path.join(root, 'ruoyi-admin/src/main/resources/application-dev.yml'), 'utf8');
  for (const [key, setting] of Object.entries({ STAGE4_JDBC_URL: 'url', STAGE4_DB_USER: 'username', STAGE4_DB_PASSWORD: 'password' })) {
    const match = config.match(new RegExp('^          ' + setting + ': (.+)$', 'm'));
    if (!match) throw Error('Missing local master setting: ' + setting);
    env[key] = match[1].trim();
  }
  const url = new URL(env.STAGE4_JDBC_URL.replace(/^jdbc:/, ''));
  if (!['127.0.0.1', 'localhost'].includes(url.hostname)) throw Error('--local only permits a local original database');
}
const java = env.STAGE4_JAVA || 'D:/devoloper_tools/Java/jdk-17/bin/java.exe';
const connector = env.STAGE4_JDBC_DRIVER || path.join(env.USERPROFILE || env.HOME, '.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar');
const evidence = path.resolve(requireEnv('STAGE4_EVIDENCE_DIR'));
fs.mkdirSync(evidence, { recursive: true });
env.STAGE4_EVIDENCE_DIR = evidence;
const hash = file => {
  const digest = crypto.createHash('sha256'), buffer = Buffer.alloc(65536), fd = fs.openSync(file, 'r');
  try { for (let bytes; (bytes = fs.readSync(fd, buffer, 0, buffer.length, null)) > 0;) digest.update(buffer.subarray(0, bytes)); }
  finally { fs.closeSync(fd); }
  return digest.digest('hex');
};
function runJava(action) {
  const result = spawnSync(java, ['-Dfile.encoding=UTF-8', '-cp', connector, 'scripts/deploy/Stage4Migration.java', action], { cwd: root, env, encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.status !== 0) { if (result.stderr) process.stderr.write(result.stderr); throw Error('Migration operation failed: ' + action); }
}
if (command === 'create-smoke-user') {
  if (!local) throw Error('Automatic account provisioning is local-only; production must supply an approved ordinary test account');
  const file = path.resolve(requireEnv('STAGE4_SMOKE_ACCOUNT_FILE'));
  if (fs.existsSync(file)) throw Error('Private smoke account already exists; reuse it instead of creating another account');
  const account = { username: 'deploy53_' + Date.now(), password: crypto.randomBytes(10).toString('hex') };
  env.STAGE4_NEW_SMOKE_USERNAME = account.username; env.STAGE4_NEW_SMOKE_PASSWORD = account.password;
  const deps = [connector, ...['crypto', 'core'].map(name => path.join(env.USERPROFILE || env.HOME, '.m2/repository/cn/hutool/hutool-' + name + '/5.8.40/hutool-' + name + '-5.8.40.jar'))].join(path.delimiter);
  const result = spawnSync(java, ['-Dfile.encoding=UTF-8', '-cp', deps, 'scripts/deploy/Stage4SmokeUser.java'], { cwd: root, env, encoding: 'utf8' });
  if (result.status !== 0) { if (result.stderr) process.stderr.write(result.stderr); throw Error('Local ordinary smoke account provisioning failed'); }
  fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, JSON.stringify(account), { mode: 0o600 });
  fs.writeFileSync(path.join(evidence, 'smoke-account.json'), JSON.stringify({ username: account.username, database: requireEnv('STAGE4_EXPECTED_DATABASE'), role: 'user', administrator: false, registrationSettingChanged: false, operator: requireEnv('STAGE4_OPERATOR'), time: new Date().toISOString() }, null, 2));
  console.log('Synthetic ordinary account created in original local database; credentials retained only in private runtime file.');
} else if (command === 'backup') {
  const expected = requireEnv('STAGE4_EXPECTED_DATABASE');
  const url = new URL(requireEnv('STAGE4_JDBC_URL').replace(/^jdbc:/, ''));
  if (decodeURIComponent(url.pathname.slice(1)) !== expected || !/^[a-zA-Z0-9_-]+$/.test(expected)) throw Error('Explicit database does not match connection');
  const backupDir = path.resolve(requireEnv('STAGE4_BACKUP_DIR'));
  fs.mkdirSync(backupDir, { recursive: true });
  const file = path.join(backupDir, expected + '-' + Date.now() + '.sql');
  const dump = requireEnv('STAGE4_MYSQLDUMP');
  const args = ['--host=' + url.hostname, '--port=' + (url.port || '3306'), '--user=' + requireEnv('STAGE4_DB_USER'), '--single-transaction', '--quick', '--routines', '--triggers', '--events', '--no-tablespaces', '--set-gtid-purged=OFF', '--column-statistics=0', '--hex-blob', '--result-file=' + file, expected];
  const result = spawnSync(dump, args, { env: { ...env, MYSQL_PWD: requireEnv('STAGE4_DB_PASSWORD') }, encoding: 'utf8' });
  if (result.status !== 0 || !fs.existsSync(file) || fs.statSync(file).size < 100) throw Error('Backup failed; no migrations executed. Check local mysqldump privileges/version.');
  const record = { database: expected, operator: requireEnv('STAGE4_OPERATOR'), time: new Date().toISOString(), file, bytes: fs.statSync(file).size, sha256: hash(file) };
  fs.writeFileSync(path.join(evidence, 'backup.json'), JSON.stringify(record, null, 2));
  console.log(JSON.stringify(record));
} else if (command === 'migrate') {
  const backup = JSON.parse(fs.readFileSync(path.join(evidence, 'backup.json'), 'utf8'));
  if (backup.database !== requireEnv('STAGE4_EXPECTED_DATABASE')) throw Error('Backup target mismatch');
  env.STAGE4_BACKUP_FILE = backup.file; env.STAGE4_BACKUP_SHA256 = backup.sha256;
  runJava('apply');
} else if (command === 'verify-smoke') {
  const record = JSON.parse(fs.readFileSync(requireEnv('STAGE4_SMOKE_RECORD_FILE'), 'utf8'));
  if (record.pass !== true || record.database !== requireEnv('STAGE4_EXPECTED_DATABASE')) throw Error('Successful smoke for this target database required');
  runJava(command);
} else if (['audit', 'rehearse', 'self-test'].includes(command)) {
  runJava(command);
} else throw Error('Expected audit, backup, rehearse, migrate, verify-smoke or self-test');
