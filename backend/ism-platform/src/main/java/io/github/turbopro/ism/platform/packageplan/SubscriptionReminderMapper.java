package io.github.turbopro.ism.platform.packageplan;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SubscriptionReminderMapper {
    // Same candidate ordering as EffectiveSubscriptionSql; expiry never revives an older subscription.
    String CANDIDATE="""
        SELECT candidate.id FROM plt_subscription candidate WHERE candidate.tenant_id=t.id
          AND candidate.status='ACTIVE' AND candidate.effective_from <= UTC_TIMESTAMP(3)
        ORDER BY candidate.effective_from DESC,candidate.assignment_sequence DESC,candidate.created_at DESC,candidate.id DESC LIMIT 1
        """;
    String STAGE="CASE WHEN s.effective_to <= UTC_TIMESTAMP(3) THEN 'EXPIRED' WHEN s.effective_to <= UTC_TIMESTAMP(3)+INTERVAL 1 DAY THEN 'WITHIN_1_DAY' ELSE 'WITHIN_7_DAYS' END";
    String FROM=" FROM plt_tenant t JOIN plt_subscription s ON s.id=("+CANDIDATE+") JOIN plt_package_version v ON v.id=s.package_version_id AND v.status='PUBLISHED' ";
    String ELIGIBLE=" t.status='ACTIVE' AND t.initialization_status='READY' AND s.effective_to IS NOT NULL AND s.effective_to <= UTC_TIMESTAMP(3)+INTERVAL 7 DAY AND s.effective_to >= UTC_TIMESTAMP(3)-INTERVAL 30 DAY ";
    @Select("SELECT t.id AS tenant_id,s.id AS subscription_id,"+STAGE+" AS stage,v.version_name,s.effective_to AS expires_at"+FROM+" WHERE "+ELIGIBLE+" AND t.id=#{tenantId}")
    SubscriptionReminderModels.Candidate due(long tenantId);
    @Select("SELECT t.id AS tenant_id,s.id AS subscription_id,"+STAGE+" AS stage,v.version_name,s.effective_to AS expires_at"+FROM+
            " LEFT JOIN plt_subscription_reminder r ON r.subscription_id=s.id AND r.stage=("+STAGE+") WHERE "+ELIGIBLE+
            " AND s.id>#{cursor} AND (r.status IS NULL OR (r.status='FAILED' AND r.attempted_at <= UTC_TIMESTAMP(3)-INTERVAL 30 MINUTE)) ORDER BY s.id LIMIT #{size}")
    List<SubscriptionReminderModels.Candidate> candidates(long cursor,int size);
    @Select("SELECT stage,status,attempt_count,recipient_count,attempted_at,sent_at,error_code FROM plt_subscription_reminder WHERE tenant_id=#{tenantId} AND subscription_id=#{subscriptionId} ORDER BY attempted_at,stage")
    List<SubscriptionReminderModels.Reminder> history(long tenantId,long subscriptionId);
    @Select("SELECT stage,status,attempt_count,recipient_count,attempted_at,sent_at,error_code FROM plt_subscription_reminder WHERE tenant_id=#{tenantId} AND subscription_id=#{subscriptionId} AND stage=#{stage}")
    SubscriptionReminderModels.Reminder reminder(long tenantId,long subscriptionId,String stage);
    @Insert("INSERT INTO plt_subscription_reminder(tenant_id,subscription_id,stage,status,attempted_at) VALUES(#{tenantId},#{subscriptionId},#{stage},'PENDING',#{now}) ON DUPLICATE KEY UPDATE subscription_id=subscription_id")
    int ensure(long tenantId,long subscriptionId,String stage,LocalDateTime now);
    @Update("UPDATE plt_subscription_reminder SET status='SENT',attempt_count=attempt_count+1,recipient_count=#{recipients},attempted_at=#{now},sent_at=#{now},error_code=NULL WHERE tenant_id=#{tenantId} AND subscription_id=#{subscriptionId} AND stage=#{stage}")
    int sent(long tenantId,long subscriptionId,String stage,int recipients,LocalDateTime now);
    @Insert("INSERT INTO plt_subscription_reminder(tenant_id,subscription_id,stage,status,attempt_count,attempted_at,error_code) VALUES(#{tenantId},#{subscriptionId},#{stage},'FAILED',1,#{now},#{error}) ON DUPLICATE KEY UPDATE attempt_count=IF(status='SENT',attempt_count,attempt_count+1),attempted_at=IF(status='SENT',attempted_at,#{now}),error_code=IF(status='SENT',error_code,#{error}),status=IF(status='SENT',status,'FAILED')")
    int failed(long tenantId,long subscriptionId,String stage,String error,LocalDateTime now);
}
