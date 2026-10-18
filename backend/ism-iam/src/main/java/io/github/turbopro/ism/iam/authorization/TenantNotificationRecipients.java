package io.github.turbopro.ism.iam.authorization;

import java.util.Set;

/** Public IAM contract; callers must establish the target tenant context. */
public interface TenantNotificationRecipients {
    Set<Long> activeAdministrators(long tenantId);
}
