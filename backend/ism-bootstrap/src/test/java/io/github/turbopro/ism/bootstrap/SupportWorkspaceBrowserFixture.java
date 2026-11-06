package io.github.turbopro.ism.bootstrap;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "ism.support.sessions.enabled=true")
class SupportWorkspaceBrowserFixture extends TenantSupportBrowserFixture {
    @Override boolean withSession() { return true; }
    @Override boolean withWorkspace() { return true; }
}
