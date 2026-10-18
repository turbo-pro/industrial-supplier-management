package io.github.turbopro.ism.platform.packageplan;

import java.time.LocalDateTime;

public final class SubscriptionReminderModels {
    private SubscriptionReminderModels() {}
    public record Candidate(long tenantId,long subscriptionId,String stage,String versionName,LocalDateTime expiresAt) {}
    public record Reminder(String stage,String status,int attemptCount,int recipientCount,
                           LocalDateTime attemptedAt,LocalDateTime sentAt,String errorCode) {}
    public record Dispatch(String status,String stage,int recipientCount,String errorCode) {}
}
