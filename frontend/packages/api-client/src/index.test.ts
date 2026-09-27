import { describe, expect, it, vi } from 'vitest';
import { createIsmClient } from './index';

describe('generated ISM client', () => {
  it('sets or clears an exit deadline and reminds with both versions, not completion',async()=>{
    const fetchMock=vi.fn<typeof fetch>().mockImplementation(async()=>new Response(JSON.stringify({data:{id:'90',version:3}}),{status:200,headers:{'Content-Type':'application/json'}}));
    const client=createIsmClient({baseUrl:'http://localhost:8080/api',fetch:fetchMock});
    const params={path:{supplierId:'30',id:'90',entityId:'100'}};
    await client.POST('/suppliers/{supplierId}/exit-applications/{id}/entities/{entityId}/deadline',{params,body:{dueDate:null,reason:'取消期限',version:0,applicationVersion:2}});
    expect(await (fetchMock.mock.calls[0]![0] as Request).json()).toEqual({dueDate:null,reason:'取消期限',version:0,applicationVersion:2});
    await client.POST('/suppliers/{supplierId}/exit-applications/{id}/entities/{entityId}/remind',{params,body:{version:1,applicationVersion:3}});
    const request=fetchMock.mock.calls[1]![0] as Request;expect(request.url).toContain('/entities/100/remind');expect(await request.json()).toEqual({version:1,applicationVersion:3});
  });
  it('loads personal exit tasks with bounded query filters and no recipient override',async()=>{
    const fetchMock=vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({data:{total:21,page:1,size:20,items:[]}}),{status:200,headers:{'Content-Type':'application/json'}}));
    const client=createIsmClient({baseUrl:'http://localhost:8080/api',fetch:fetchMock});
    const result=await client.GET('/supplier-exit-tasks/mine',{params:{query:{keyword:'测试',code:'OPEN_CONTRACT',page:1,size:20}}});
    expect(result.data?.data.total).toBe(21);
    const url=new URL((fetchMock.mock.calls[0]![0] as Request).url);
    expect(url.pathname).toBe('/api/supplier-exit-tasks/mine');expect(url.searchParams.get('page')).toBe('1');expect(url.searchParams.get('keyword')).toBe('测试');expect(url.searchParams.has('assigneeId')).toBe(false);
  });
  it('decodes unread counts and permission errors for the personal inbox',async()=>{
    const fetchMock=vi.fn<typeof fetch>().mockResolvedValueOnce(new Response(JSON.stringify({data:{count:3}}),{status:200,headers:{'Content-Type':'application/json'}})).mockResolvedValueOnce(new Response(JSON.stringify({error:{code:'FORBIDDEN',message:'无权限'}}),{status:403,headers:{'Content-Type':'application/json'}}));
    const client=createIsmClient({baseUrl:'http://localhost:8080/api',fetch:fetchMock});
    expect((await client.GET('/messages/inbox/unread-count')).data?.data.count).toBe(3);
    expect((await client.GET('/messages/inbox')).error?.error.message).toBe('无权限');
  });
  it('marks only the specified message read through the PUT contract',async()=>{
    const fetchMock=vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({data:null}),{status:200,headers:{'Content-Type':'application/json'}}));
    const client=createIsmClient({baseUrl:'http://localhost:8080/api',fetch:fetchMock});
    await client.PUT('/messages/inbox/{id}/read',{params:{path:{id:'100'}}});
    const request=fetchMock.mock.calls[0]![0] as Request;
    expect(request.method).toBe('PUT');expect(request.url).toBe('http://localhost:8080/api/messages/inbox/100/read');
  });
  it('loads a bounded exit entity page with its application version',async()=>{
    const fetchMock=vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({success:true,data:{total:21,page:1,size:20,applicationVersion:2,items:[]},traceId:'exit-page',timestamp:'2026-09-27T10:00:00Z'}),{status:200,headers:{'Content-Type':'application/json'}}));
    const client=createIsmClient({baseUrl:'http://localhost:8080/api',fetch:fetchMock});
    const response=await client.GET('/suppliers/{supplierId}/exit-applications/{id}/entities',{params:{path:{supplierId:'30',id:'90'},query:{page:1,size:20}}});
    expect(response.data?.data?.applicationVersion).toBe(2);expect(response.data?.data?.total).toBe(21);
    const url=new URL((fetchMock.mock.calls[0]![0] as Request).url);
    expect(url.pathname).toBe('/api/suppliers/30/exit-applications/90/entities');expect(url.searchParams.get('page')).toBe('1');expect(url.searchParams.get('size')).toBe('20');
  });
  it('assigns exit entities with both optimistic versions and never sends a completion flag',async()=>{
    const fetchMock=vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({success:true,data:{id:'90',version:2,entities:[]},traceId:'exit-assign',timestamp:'2026-09-27T10:00:00Z'}),{status:200,headers:{'Content-Type':'application/json'}}));
    const client=createIsmClient({baseUrl:'http://localhost:8080/api',fetch:fetchMock});
    const body={assigneeId:'7',note:'跟进合同处置',version:0,applicationVersion:1};
    const response=await client.POST('/suppliers/{supplierId}/exit-applications/{id}/entities/{entityId}/assign',{params:{path:{supplierId:'30',id:'90',entityId:'100'}},body});
    expect(response.data?.data?.version).toBe(2);
    const request=fetchMock.mock.calls[0]![0] as Request;
    expect(request.url).toBe('http://localhost:8080/api/suppliers/30/exit-applications/90/entities/100/assign');
    expect(await request.json()).toEqual(body);
  });
  it.each(['contract.ledger','project.ledger'] as const)('keeps %s view saves on the registered table path',async(tableKey)=>{
    const columns=(tableKey==='contract.ledger'?['contractNo','name','amount','period','status']:['projectCode','name','contractNo','period','status']).map(key=>({key,visible:true,width:160}));
    const fetchMock=vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({success:true,data:{id:'901',name:'常用',columns,defaultView:true,version:0,updatedAt:'2026-09-27T05:20:00Z'},traceId:'column-save',timestamp:'2026-09-27T05:20:00Z'}),{status:200,headers:{'Content-Type':'application/json'}}));
    const client=createIsmClient({baseUrl:'http://localhost:8080/api',fetch:fetchMock});
    const body={name:'常用',columns,defaultView:true,version:0};
    const response=await client.POST('/table-views/{tableKey}',{params:{path:{tableKey}},body});
    expect(response.data?.data?.id).toBe('901');
    const request=fetchMock.mock.calls[0]![0] as Request;
    expect(request.url).toBe(`http://localhost:8080/api/table-views/${tableKey}`);
    expect(await request.json()).toEqual(body);
  });
  it('decodes ledger permission failures instead of treating them as empty pages',async()=>{
    const fetchMock=vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({success:false,error:{code:'IAM_FORBIDDEN',message:'无权执行此操作',retryable:false},traceId:'ledger-denied',timestamp:'2026-09-27T05:20:00Z'}),{status:403,headers:{'Content-Type':'application/json'}}));
    const client=createIsmClient({baseUrl:'http://localhost:8080/api',fetch:fetchMock});
    const response=await client.GET('/contracts',{params:{query:{page:1,size:20}}});
    expect(response.data).toBeUndefined();expect(response.error?.error.code).toBe('IAM_FORBIDDEN');
  });
  it('sends a typed login request and decodes the standard envelope', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      success: true,
      data: {
        accessToken: 'access-token', refreshToken: 'refresh-token', expiresIn: 900,
        user: { id: '1', tenantId: '10', username: 'admin', displayName: '管理员', passwordChangeRequired: false },
      },
      traceId: 'trace-login', timestamp: '2026-09-21T00:00:00Z',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    const client = createIsmClient({ baseUrl: 'http://localhost:8080/api', fetch: fetchMock });

    const { data, error } = await client.POST('/auth/login', {
      body: { tenantCode: 'demo', username: 'admin', password: 'Demo-password-1', deviceId: 'e2e' },
    });

    expect(error).toBeUndefined();
    expect(data?.data?.user.username).toBe('admin');
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});
