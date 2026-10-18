package io.github.turbopro.ism.iam.authorization;

import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class DatabaseTenantNotificationRecipients implements TenantNotificationRecipients {
    private final TenantNotificationRecipientMapper mapper;
    public DatabaseTenantNotificationRecipients(TenantNotificationRecipientMapper mapper) { this.mapper=mapper; }
    @Override public Set<Long> activeAdministrators(long tenantId) { return mapper.administrators(tenantId); }
}
