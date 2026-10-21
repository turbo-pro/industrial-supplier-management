import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
const [control, url] = process.argv.slice(2);
if (!control || !/^http:\/\/127\.0\.0\.1:\d+$/.test(url ?? '') || !existsSync(join(control, 'ready'))) throw new Error('Active loopback fixture required');
const token = readFileSync(join(control, 'invitation'), 'utf8').trim();
if (!/^[A-Za-z0-9_-]{43}$/.test(token)) throw new Error('Invalid fixture invitation');
const script = join(control, 'portal-flow.js');
writeFileSync(script, readFileSync('scripts/browser/portal-real-auth.js', 'utf8').replace('__INVITATION__', token), { mode: 0o600 });
const session = `ism-portal-real-${process.pid}`;
function cli(args, quiet = false) {
  // run-code prints source including the test invitation; do not persist that output.
  const result = spawnSync('npx', ['--yes', '--package', '@playwright/cli@0.1.22', 'playwright-cli', `-s=${session}`, ...args], { encoding: 'utf8', timeout: 120_000 });
  if (!quiet) process.stdout.write(result.stdout ?? '');
  if (result.error || result.status !== 0 || /### Error/.test(result.stdout ?? '')) throw new Error(`Browser command ${args[0]} failed; credentials omitted`);
}
try {
  cli(['open', url, '--browser', 'chrome']);
  cli(['snapshot']);
  cli(['run-code', '--filename', script], true);
  writeFileSync(join(control, 'finish'), 'done');
  console.log('Real portal browser assertions passed; waiting for database assertions.');
} catch (error) { writeFileSync(join(control, 'abort'), 'stop'); process.exitCode = 1; console.error(error.message); }
finally { try { cli(['close'], true); } catch {} }
