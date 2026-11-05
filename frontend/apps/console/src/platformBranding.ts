import type { components } from '@ism/api-client';
export type Branding = components['schemas']['PlatformBranding'];
export const defaultBranding: Branding = {systemName:'工业供应商管理系统 Console',logoUrl:'',faviconUrl:'',footerText:''};
export function safeImage(value:string){
 if(!value)return '';
 if(/[\\\u0000-\u0020\u007f]/.test(value))return '';
 if(value.startsWith('/')&&!value.startsWith('//'))return value;
 try{const u=new URL(value);return ['http:','https:'].includes(u.protocol)&&!u.username&&!u.password?value:'';}catch{return '';}
}
export function isBranding(value:unknown):value is Branding {const b=value as Branding;return !!b&&typeof b.systemName==='string'&&!!b.systemName.trim()&&b.systemName.length<=100&&typeof b.logoUrl==='string'&&b.logoUrl.length<=2000&&typeof b.faviconUrl==='string'&&b.faviconUrl.length<=2000&&typeof b.footerText==='string'&&b.footerText.length<=300;}
