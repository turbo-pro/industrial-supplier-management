import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
const [control,url] = process.argv.slice(2);
const session = `ism-site-preference-${process.pid}`;
function cli(args) {
  const r=spawnSync('npx',['--yes','--package','@playwright/cli@0.1.22','playwright-cli',`-s=${session}`,...args],{encoding:'utf8',timeout:150_000});
  if(r.error||r.status!==0||/### Error/.test(r.stdout??'')) throw new Error((r.stdout?.split('### Error')[1]?.split('### ')[0]||r.error?.message||'Browser command failed').replaceAll('Console#Pass123','[redacted]').replaceAll('Site#Initial123','[redacted]').replace(/Bearer\s+[A-Za-z0-9._~-]+/g,'Bearer [redacted]').replace(/[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/g,'[redacted-token]').slice(0,1600));
  return r.stdout;
}
try {
  mkdirSync('output/playwright/site-preference',{recursive:true});
  const fixture=JSON.parse(readFileSync(join(control,'fixture.json'),'utf8'));
  const script=join(control,'site-preference.js');
  writeFileSync(script,readFileSync('scripts/browser/site-preference.js','utf8').replace('__FIXTURE__',JSON.stringify(fixture)),{mode:0o600});
  cli(['open',url,'--browser','chrome']); cli(['run-code','--filename',script]);
  writeFileSync(join(control,'finish'),'done');console.log('Site preference browser workflow passed.');
} catch(error){try{writeFileSync('output/playwright/site-preference/failure-snapshot.txt',cli(['snapshot']));}catch{}writeFileSync(join(control,'abort'),'stop');process.exitCode=1;console.error(error.message);}
finally{try{cli(['close']);}catch{}}
