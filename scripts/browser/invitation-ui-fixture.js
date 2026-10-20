async page => {
  let generation = 0, items = [], canManage = true, failRead = false;
  const supplier = { id: '900', organizationId: '100', code: 'SUP-001', name: '门户邀请界面测试供应商', type: 'MANUFACTURER', status: 'ACTIVE', riskLevel: 'LOW', countryCode: 'CN', source: 'MANUAL', version: 0, contacts: [] };
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api', '');
    const method = route.request().method();
    let data = {};
    if (path === '/auth/login') data = { accessToken: 'ui-only', refreshToken: 'r', expiresIn: 900, user: { id: '1', tenantId: '10', username: 'admin', displayName: '测试管理员', passwordChangeRequired: false, passwordChangeRecommended: false } };
    else if (path === '/organizations/tree') data = [{ id: '100', code: 'HQ', name: '总部', type: 'HEADQUARTERS', status: 'ACTIVE', children: [] }];
    else if (path === '/organizations/current') data = { id: '100', code: 'HQ', name: '总部', type: 'HEADQUARTERS' };
    else if (path === '/navigation/menus') data = [];
    else if (path.includes('branding')) data = { systemName: '工业供应商管理系统', logoUrl: '', faviconUrl: '', footerText: '' };
    else if (path.endsWith('/portal-invitations/ui-mode')) { const body = route.request().postDataJSON(); canManage = body.canManage ?? canManage; failRead = body.failRead ?? failRead; if (body.accepted && items[0]) items[0] = { ...items[0], status: 'ACCEPTED' }; }
    else if (path.endsWith('/portal-invitations') && method === 'GET') {
      if (failRead) return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, error: { message: '模拟邀请读取失败' } }) });
      data = { latestGeneration: generation, canManage, items };
    } else if (path.endsWith('/portal-invitations') && method === 'POST') {
      const command = route.request().postDataJSON();
      if (command.expectedGeneration !== generation) throw new Error('Incorrect generation');
      items = items.map(item => ({ ...item, status: item.status === 'PENDING' ? 'REPLACED' : item.status }));
      const invitation = { id: String(++generation), generation, recipientName: command.recipientName, recipientEmail: command.recipientEmail, status: 'PENDING', expiresAt: '2026-12-01T00:00:00', createdAt: '2026-11-30T00:00:00', version: 0 };
      items.unshift(invitation); data = { invitation, token: 'mock-only-token-not-valid-for-registration-001' };
    } else if (path.endsWith('/revoke')) {
      const command = route.request().postDataJSON();
      if (!command.reason?.trim()) throw new Error('Missing reason');
      items[0] = { ...items[0], status: 'REVOKED', revokeReason: command.reason, version: 1 }; data = items[0];
    } else if (path === '/suppliers/900') data = supplier;
    else if (path === '/suppliers') data = { total: 1, page: 0, size: 20, items: [supplier] };
    else if (path.endsWith('/purchase-categories')) data = { version: 0, categoryIds: [] };
    else if (path === '/supplier-purchase-categories') data = [];
    else if (path === '/qualifications') data = { total: 0, page: 0, size: 5, items: [] };
    else if (path.startsWith('/table-views/')) data = { views: [], sharedViews: [], canPublish: false, catalog: [
      { key: 'code', label: '编码', width: 160, required: true }, { key: 'name', label: '名称', width: 260, required: true },
      { key: 'type', label: '类型', width: 110 }, { key: 'riskLevel', label: '风险', width: 90 }, { key: 'status', label: '状态', width: 90 },
    ] };
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data }) });
  });
  await page.goto('http://127.0.0.1:4176/suppliers/master?supplierId=900');
  return { mocked: true, scenario: 'invitation management only' };
}
