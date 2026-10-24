#!/usr/bin/env node
import { spawn } from 'node:child_process';
import { createWriteStream, mkdtempSync, mkdirSync, readFileSync, existsSync, writeFileSync, rmSync, copyFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'node:net';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
if(process.argv.slice(2).length>1 || process.argv.slice(2).some(arg=>!['--portal','--portal-management','--portal-exit','--portal-password','--portal-recovery','--portal-changes','--portal-certificates','--portal-submissions'].includes(arg)))
  throw new Error('Usage: node scripts/verify-reminder-e2e.mjs [--portal | --portal-management | --portal-exit | --portal-password | --portal-recovery | --portal-changes | --portal-certificates | --portal-submissions]');
const submissions = process.argv.includes('--portal-submissions');
const certificates = process.argv.includes('--portal-certificates');
const changes = process.argv.includes('--portal-changes');
const recovery = process.argv.includes('--portal-recovery');
const password = process.argv.includes('--portal-password');
const exit = process.argv.includes('--portal-exit');
const management = process.argv.includes('--portal-management') || exit || recovery || changes || submissions;
const portal = process.argv.includes('--portal') || management || password || certificates;
const scenario = submissions ? 'portal-submissions' : certificates ? 'portal-certificates' : changes ? 'portal-changes' : recovery ? 'portal-recovery' : password ? 'portal-password' : exit ? 'portal-exit' : management ? 'portal-management' : portal ? 'portal' : 'reminder';
const fixture = portal ? 'PortalBrowserFixture' : 'ConsoleReminderBrowserFixture';
const control = mkdtempSync(join(tmpdir(), portal ? 'ism-portal-e2e-' : 'ism-reminder-e2e-'));
const artifacts = join(root, 'output/playwright', `${scenario}-e2e-${Date.now()}`);
mkdirSync(artifacts, { recursive: true });
const children = [];
let interrupted = false;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
function start(name, command, args, env = {}) {
  const log = createWriteStream(join(artifacts, `${name}.log`));
  const child = spawn(command, args, { cwd: root, env: { ...process.env, ...env }, detached: true, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.pipe(log, { end: false }); child.stderr.pipe(log, { end: false });
  const entry = { name, child, done: false, code: null, error: null };
  entry.closed = new Promise(resolve => {
    child.on('error', error => { entry.error = error; });
    child.on('close', code => { entry.done = true; entry.code = code; log.end(); resolve(); });
  });
  children.push(entry);
  return entry;
}
function signal(entry, signal) {
  // Every child has a newly-created process group; never signal another checkout's services.
  if (!entry.done && entry.child.pid) try { process.kill(-entry.child.pid, signal); } catch (error) { if (error.code !== 'ESRCH') throw error; }
}
function ensureRunning(entry) {
  if (interrupted) throw new Error('Interrupted');
  if (entry.done) throw new Error(`${entry.name} exited early (${entry.error?.message ?? entry.code}); see ${artifacts}`);
}
async function until(predicate, entry, timeout) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    ensureRunning(entry);
    if (await predicate()) return;
    await sleep(250);
  }
  throw new Error(`${entry.name} readiness timed out; see ${artifacts}`);
}
async function waitExit(entry, timeout) {
  const deadline = Date.now() + timeout;
  while (!entry.done && Date.now() < deadline) {
    if (interrupted) throw new Error('Interrupted');
    await sleep(250);
  }
  if (!entry.done || entry.code !== 0) throw new Error(`${entry.name} failed (${entry.error?.message ?? entry.code ?? 'timeout'}); see ${artifacts}`);
}
async function unusedPort() {
  const server = createServer();
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  const port = server.address().port;
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  return port;
}
process.on('SIGINT', () => { interrupted = true; });
process.on('SIGTERM', () => { interrupted = true; });
try {
  console.log(`[artifacts] ${artifacts}`);
  const backend = start('backend', join(root, 'mvnw'), ['--batch-mode', '--no-transfer-progress', '-q', '-pl', 'backend/ism-bootstrap', '-am',
    '-Pintegration-test', `-Dit.test=${fixture}`, '-Dism.browser.recovery=true', `-Dism.browser.portal-management=${management}`, `-Dism.browser.portal-exit=${exit}`,
    `-Dism.browser.portal-password=${password}`, `-Dism.browser.portal-recovery=${recovery}`, `-Dism.browser.portal-changes=${changes}`, `-Dism.browser.portal-certificates=${certificates}`, `-Dism.browser.portal-submissions=${submissions}`, `-Dism.browser.fixture-dir=${control}`, '-Dfailsafe.failIfNoSpecifiedTests=false', 'verify']);
  await until(() => existsSync(join(control, 'ready')), backend, 360_000);
  const target = readFileSync(join(control, 'ready'), 'utf8').trim();
  if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(target)) throw new Error('Invalid isolated backend URL');
  console.log('[ready] isolated backend');
  const port = await unusedPort();
  const frontendUrl = `http://127.0.0.1:${port}`;
  const frontend = start('frontend', 'corepack', ['pnpm', '--filter', portal ? '@ism/portal' : '@ism/console', 'dev', '--host', '127.0.0.1', '--port', String(port), '--strictPort'],
    portal ? { ISM_PORTAL_API_TARGET: target } : { ISM_CONSOLE_API_TARGET: target });
  await until(async () => { try { return (await fetch(frontendUrl, { signal: AbortSignal.timeout(1000) })).ok; } catch { return false; } }, frontend, 60_000);
  ensureRunning(backend);
  let adminUrl = '';
  if(management){
    adminUrl = `http://127.0.0.1:${await unusedPort()}`;
    const admin = start('admin-frontend', 'corepack', ['pnpm','--filter','@ism/admin','dev','--host','127.0.0.1','--port',new URL(adminUrl).port,'--strictPort'], { ISM_DEV_API_TARGET: target });
    await until(async () => {try{return (await fetch(adminUrl,{signal:AbortSignal.timeout(1000)})).ok;}catch{return false;}},admin,60_000);
  }
  console.log(`[run] real browser scenario: ${scenario}`);
  const browser = portal
    ? start('browser', process.execPath, ['scripts/verify-portal-browser.mjs', control, frontendUrl, adminUrl, submissions ? 'submissions' : certificates ? 'certificates' : changes ? 'changes' : recovery ? 'recovery' : password ? 'password' : exit ? 'exit' : 'management'])
    : start('browser', 'bash', ['scripts/verify-reminder-browser.sh', control, frontendUrl]);
  await waitExit(browser, 180_000);
  await waitExit(backend, 60_000);
  const report = join(root, `backend/ism-bootstrap/target/failsafe-reports/io.github.turbopro.ism.bootstrap.${fixture}.txt`);
  const summary = readFileSync(report, 'utf8');
  if (!/Tests run: 1, Failures: 0, Errors: 0, Skipped: 0/.test(summary)) throw new Error('Fixture report is not a clean pass');
  copyFileSync(report, join(artifacts, 'database-assertions.txt'));
  console.log('[pass] browser assertions and final isolated database assertions passed');
} catch (error) {
  console.error(`[fail] ${error.message}`);
  process.exitCode = 1;
} finally {
  writeFileSync(join(control, 'abort'), 'stop');
  // Stop browser/frontend immediately; let the fixture observe abort and close Testcontainers first.
  for (const entry of children.filter(e => e.name !== 'backend')) signal(entry, 'SIGTERM');
  const backend = children.find(e => e.name === 'backend');
  if (backend && !backend.done) await Promise.race([backend.closed, sleep(30_000)]);
  for (const entry of children) signal(entry, 'SIGTERM');
  await Promise.race([Promise.all(children.map(e => e.closed)), sleep(10_000)]);
  for (const entry of children) signal(entry, 'SIGKILL');
  await Promise.all(children.map(e => e.closed));
  // Only this mkdtemp-owned directory is removed; logs and screenshots remain in ignored output/.
  rmSync(control, { recursive: true, force: true });
}
