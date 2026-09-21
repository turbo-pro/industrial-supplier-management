import { describe, expect, it, vi } from 'vitest';
import { createIsmClient } from './index';

describe('generated ISM client', () => {
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
