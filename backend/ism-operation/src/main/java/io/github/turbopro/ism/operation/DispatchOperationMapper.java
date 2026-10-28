package io.github.turbopro.ism.operation;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface DispatchOperationMapper {
    @Select("""
        SELECT id,event_id,tenant_id,event_type,event_version,aggregate_type,aggregate_id,payload,status,
          retry_count,lease_owner,lease_until,trace_id
        FROM sys_outbox_event
        WHERE ((status='PENDING' AND next_retry_at<=#{now})
          OR (status='PROCESSING' AND lease_until<=#{now}))
        ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
        """)
    OperationModels.OutboxEvent lockNextOutbox(LocalDateTime now);

    @Select("SELECT id,event_id,tenant_id,event_type,event_version,aggregate_type,aggregate_id,payload,status,retry_count,lease_owner,lease_until,trace_id FROM sys_outbox_event WHERE id=#{id}")
    OperationModels.OutboxEvent claimedOutbox(long id);

    @Update("""
        UPDATE sys_outbox_event SET status='PROCESSING',lease_owner=#{owner},lease_until=#{leaseUntil}
        WHERE id=#{id} AND ((status='PENDING' AND next_retry_at<=#{now})
          OR (status='PROCESSING' AND lease_until<=#{now}))
        """)
    int claimOutbox(long id, String owner, LocalDateTime now, LocalDateTime leaseUntil);

    @Update("""
        UPDATE sys_outbox_event SET status='PUBLISHED',published_at=#{now},lease_owner=NULL,lease_until=NULL
        WHERE id=#{id} AND status='PROCESSING' AND lease_owner=#{owner} AND lease_until>#{now}
        """)
    int publishOutbox(long id, String owner, LocalDateTime now);

    @Update("""
        UPDATE sys_outbox_event SET status=#{status},retry_count=retry_count+1,next_retry_at=#{nextRetryAt},
          last_error=#{error},lease_owner=NULL,lease_until=NULL
        WHERE id=#{id} AND status='PROCESSING' AND lease_owner=#{owner} AND lease_until>#{now} AND retry_count=#{expectedRetries}
        """)
    int failOutbox(long id, String owner, String status, LocalDateTime nextRetryAt, String error, LocalDateTime now, int expectedRetries);

    @Insert("INSERT INTO sys_event_consumption(id,event_id,consumer_code,consumed_at) VALUES(#{id},#{eventId},#{consumer},#{now})")
    int insertConsumption(long id, String eventId, String consumer, LocalDateTime now);

    @Select("""
        SELECT id,tenant_id,task_no,task_type,requester_id,request_payload,status,progress,
          lease_owner,lease_until,retry_count,version,current_stage,cancel_requested,result_file_id,
          error_code,error_message,created_at,started_at,finished_at
        FROM ops_async_task
        WHERE status='QUEUED' OR (status='RUNNING' AND lease_until<=#{now})
        ORDER BY priority DESC,created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
        """)
    OperationModels.AsyncTask lockNextTask(LocalDateTime now);

    @Select("""
        SELECT id,tenant_id,task_no,task_type,requester_id,request_payload,status,progress,
          lease_owner,lease_until,retry_count,version,current_stage,cancel_requested,result_file_id,
          error_code,error_message,created_at,started_at,finished_at
        FROM ops_async_task
        WHERE task_type=#{taskType} AND (status='QUEUED' OR (status='RUNNING' AND lease_until<=#{now}))
        ORDER BY priority DESC,created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
        """)
    OperationModels.AsyncTask lockNextTaskOfType(String taskType,LocalDateTime now);

    @Select("SELECT id,tenant_id,task_no,task_type,requester_id,request_payload,status,progress,lease_owner,lease_until,retry_count,version,current_stage,cancel_requested,result_file_id,error_code,error_message,created_at,started_at,finished_at FROM ops_async_task WHERE id=#{id}")
    OperationModels.AsyncTask claimedTask(long id);

    @Update("""
        UPDATE ops_async_task SET status='RUNNING',lease_owner=#{owner},lease_until=#{leaseUntil},
          started_at=COALESCE(started_at,#{now}),version=version+1
        WHERE id=#{id} AND (status='QUEUED' OR (status='RUNNING' AND lease_until<=#{now}))
        """)
    int claimTask(long id, String owner, LocalDateTime now, LocalDateTime leaseUntil);

    @Update("""
        UPDATE ops_async_task SET lease_until=#{leaseUntil},version=version+1
        WHERE id=#{id} AND status='RUNNING' AND lease_owner=#{owner} AND lease_until>#{now} AND cancel_requested=0
        """)
    int renewTask(long id, String owner, LocalDateTime now, LocalDateTime leaseUntil);

    @Update("""
        UPDATE ops_async_task SET status='SUCCEEDED',progress=100,finished_at=#{now},
          lease_owner=NULL,lease_until=NULL,version=version+1
        WHERE id=#{id} AND status='RUNNING' AND lease_owner=#{owner} AND lease_until>#{now} AND cancel_requested=0
        """)
    int completeTask(long id, String owner, LocalDateTime now);

    @Update("UPDATE ops_async_task SET progress=#{progress},current_stage=#{stage},version=version+1 WHERE id=#{id} AND status='RUNNING' AND lease_owner=#{owner} AND cancel_requested=0 AND lease_until>#{now}")
    int updateTaskProgress(long id,String owner,int progress,String stage,LocalDateTime now);

    @Update("UPDATE ops_async_task SET status='FAILED',result_file_id=NULL,error_code=#{code},error_message=#{message},finished_at=#{now},lease_owner=NULL,lease_until=NULL,version=version+1 WHERE id=#{id} AND status='RUNNING' AND lease_owner=#{owner} AND lease_until>#{now} AND cancel_requested=0")
    int failTask(long id,String owner,String code,String message,LocalDateTime now);

    @Update("UPDATE ops_async_task SET result_file_id=#{fileId} WHERE id=#{id} AND status='RUNNING' AND lease_owner=#{owner} AND lease_until>#{now} AND cancel_requested=0")
    int attachTaskResult(long id,String owner,long fileId,LocalDateTime now);
    @Update("""
        UPDATE ops_async_task SET status='CANCELLED',finished_at=#{now},current_stage='已取消',
          result_file_id=NULL,lease_owner=NULL,lease_until=NULL,version=version+1
        WHERE id=#{id} AND status='RUNNING' AND lease_owner=#{owner}
          AND lease_until>#{now} AND cancel_requested=1
        """)
    int settleTaskCancellation(long id,String owner,LocalDateTime now);
}
