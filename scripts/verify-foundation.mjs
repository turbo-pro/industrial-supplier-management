#!/usr/bin/env node
import {spawnSync} from 'node:child_process';
import {readFileSync,statSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('..',import.meta.url));
const args=process.argv.slice(2);if(args.length>1||args.some(a=>a!=='--check-reports'))throw Error('Usage: node scripts/verify-foundation.mjs [--check-reports]');
const checkOnly=args.includes('--check-reports');const started=Date.now();
if(!checkOnly){
 const result=spawnSync(join(root,'mvnw'),['--batch-mode','--no-transfer-progress','-f',join(root,'pom.xml'),'-pl','backend/ism-bootstrap','-am','-Pintegration-test','-Dit.test=B0InfrastructureIT,SharedDatabaseAuthenticationIT','-DskipTests=false','-DskipITs=false','-Dfailsafe.failIfNoSpecifiedTests=false','verify'],{cwd:root,stdio:'inherit'});
 if(result.error)throw result.error;if(result.status!==0)process.exit(result.status??1);
}
for(const [name,expected] of [['B0InfrastructureIT',24],['SharedDatabaseAuthenticationIT',1]]){
 const path=join(root,'backend/ism-bootstrap/target/failsafe-reports',`TEST-io.github.turbopro.ism.bootstrap.${name}.xml`);
 if(!checkOnly&&statSync(path).mtimeMs<started-2000)throw Error(`Stale report: ${name}`);
 const header=readFileSync(path,'utf8').match(/<testsuite\b[^>]*>/)?.[0];if(!header)throw Error(`Missing suite header: ${name}`);
 const counts=Object.fromEntries([...header.matchAll(/\b(tests|failures|errors|skipped)="(\d+)"/g)].map(m=>[m[1],Number(m[2])]));
 if(counts.tests!==expected||counts.failures!==0||counts.errors!==0||counts.skipped!==0)throw Error(`Foundation gate rejected ${name}: ${JSON.stringify(counts)}`);
 console.log(`[pass] ${name}: ${expected} tests, no failures/errors/skips`);
}
console.log(checkOnly?'Checked existing reports only; no tests were executed by this invocation.':'Foundation regression gate passed on newly generated reports.');
