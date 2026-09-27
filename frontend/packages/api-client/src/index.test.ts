import { describe, expect, it, vi } from 'vitest';
import { createIsmClient } from './index';

describe('generated ISM client', () => {
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
