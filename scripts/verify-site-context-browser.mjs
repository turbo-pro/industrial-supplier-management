import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';
const [control,url] = process.argv.slice(2);
const session = `ism-site-context-${process.pid}`;
function cli(args) {
  const r=spawnSync('npx',['--yes','--package','@playwright/cli@0.1.22','playwright-cli',`-s=${session}`,...args],{encoding:'utf8',timeout:150_000});
  if(r.error||r.status!==0||/### Error/.test(r.stdout??'')) throw new Error((r.stdout?.split('### Error')[1]?.split('### ')[0]||r.error?.message||'Browser command failed').replaceAll('Console#Pass123','[redacted]').replaceAll('Site#Initial123','[redacted]').replace(/Bearer\s+[A-Za-z0-9._~-]+/g,'Bearer [redacted]').replace(/[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/g,'[redacted-token]').slice(0,1600));
  return r.stdout;
}
try {
  mkdirSync('output/playwright/site-context',{recursive:true});
  const fixture=JSON.parse(readFileSync(join(control,'fixture.json'),'utf8'));
  const script=join(control,'site-context.js');
  writeFileSync(script,readFileSync('scripts/browser/site-context.js','utf8').replace('__FIXTURE__',JSON.stringify(fixture)),{mode:0o600});
  cli(['open',url,'--browser','chrome']); cli(['run-code','--filename',script]);
  writeFileSync(join(control,'revoke'),'scope withdrawal');
  const deadline=Date.now()+30000;
  while(!existsSync(join(control,'revoked'))&&Date.now()<deadline) await new Promise(resolve=>setTimeout(resolve,100));
  if(!existsSync(join(control,'revoked')))throw new Error('Isolated revocation fixture did not acknowledge');
  cli(['run-code','--filename','scripts/browser/site-context-revoked.js']);
  writeFileSync(join(control,'finish'),'done');console.log('Site context browser workflow passed.');
} catch(error){try{writeFileSync('output/playwright/site-context/failure-snapshot.txt',cli(['snapshot']));}catch{}writeFileSync(join(control,'abort'),'stop');process.exitCode=1;console.error(error.message);}
finally{try{cli(['close']);}catch{}}
