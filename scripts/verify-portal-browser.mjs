import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
const [control, url, adminUrl, scenario] = process.argv.slice(2);
if(scenario && !['management','exit','password','recovery','changes'].includes(scenario))throw new Error('Unknown portal browser scenario');
if (adminUrl && !/^http:\/\/127\.0\.0\.1:\d+$/.test(adminUrl)) throw new Error('Loopback admin URL required');
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
  if (result.error || result.status !== 0 || /### Error/.test(result.stdout ?? '')) {
    const detail=(result.stdout?.split('### Error')[1]?.split('### ')[0] || result.error?.message || 'nonzero exit')
      .replaceAll(token,'[redacted]').replaceAll('Employee#Pass123','[redacted]').replaceAll('Portal#Pass123','[redacted]').replaceAll('Changed#Pass456','[redacted]').replaceAll('Recovered#Pass456','[redacted]').replace(/[A-Za-z0-9_-]{43}/g,'[redacted]').slice(0,1500);
    throw new Error(`Browser command ${args[0]} failed; ${detail}`);
  }
}
try {
  cli(['open', url, '--browser', 'chrome']);
  cli(['snapshot']);
  cli(['run-code', '--filename', script], true);
  if(scenario==='password')cli(['run-code','--filename','scripts/browser/portal-real-password.js'],true);
  if(adminUrl){
    const managementScript = join(control,'portal-management-flow.js');
    writeFileSync(managementScript,readFileSync(scenario==='changes'?'scripts/browser/portal-real-changes.js':scenario==='recovery'?'scripts/browser/portal-real-recovery.js':scenario==='exit'?'scripts/browser/portal-real-exit.js':'scripts/browser/portal-real-management.js','utf8').replaceAll('__ADMIN_URL__',adminUrl),{mode:0o600});
    cli(['run-code','--filename',managementScript],true);
  }
  writeFileSync(join(control, 'finish'), 'done');
  console.log('Real portal browser assertions passed; waiting for database assertions.');
} catch (error) { writeFileSync(join(control, 'abort'), 'stop'); process.exitCode = 1; console.error(error.message); }
finally { try { cli(['close'], true); } catch {} }
