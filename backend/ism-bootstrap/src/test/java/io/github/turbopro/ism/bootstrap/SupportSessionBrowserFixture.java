package io.github.turbopro.ism.bootstrap;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "ism.support.sessions.enabled=true")
class SupportSessionBrowserFixture extends TenantSupportBrowserFixture {
    @Override boolean withSession() { return true; }
}
