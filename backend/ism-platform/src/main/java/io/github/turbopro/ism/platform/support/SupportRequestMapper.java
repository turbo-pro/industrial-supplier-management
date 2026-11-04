package io.github.turbopro.ism.platform.support;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface SupportRequestMapper {
 String COLUMNS="r.id,r.biz_no,r.platform_operator_id,u.display_name AS operator_name,u.status AS operator_status,r.target_tenant_id,t.tenant_code,t.tenant_name,r.ticket_no,r.reason,r.profile_json,r.requested_expire_at,r.status,r.submitted_at,r.version,r.created_at";
 String FROM=" FROM plt_support_request r JOIN plt_user u ON u.id=r.platform_operator_id JOIN plt_tenant t ON t.id=r.target_tenant_id ";
 @Select("SELECT UTC_TIMESTAMP(3)") LocalDateTime now();
 @Select("SELECT id,status,initialization_status FROM plt_tenant WHERE id=#{id} FOR UPDATE") SupportRequestModels.Tenant lockTenant(long id);
 @Select("SELECT "+COLUMNS+FROM+"WHERE r.id=#{id}") SupportRequestModels.Row get(long id);
 @Select("SELECT "+COLUMNS+FROM+"WHERE r.id=#{id} FOR UPDATE") SupportRequestModels.Row lock(long id);
 @Select("<script>SELECT COUNT(*)"+FROM+"WHERE <choose><when test='review'>r.submitted_at IS NOT NULL</when><otherwise>r.platform_operator_id=#{actor}</otherwise></choose></script>")
 long count(long actor,boolean review);
 @Select("<script>SELECT "+COLUMNS+FROM+"WHERE <choose><when test='review'>r.submitted_at IS NOT NULL</when><otherwise>r.platform_operator_id=#{actor}</otherwise></choose> ORDER BY r.created_at DESC,r.id DESC LIMIT #{size} OFFSET #{offset}</script>")
 List<SupportRequestModels.Row> list(long actor,boolean review,int offset,int size);
 @Insert("INSERT INTO plt_support_request(id,biz_no,platform_operator_id,target_tenant_id,ticket_no,reason,profile_json,requested_expire_at) VALUES(#{id},#{biz},#{actor},#{tenant},#{ticket},#{reason},#{profile},#{expires})")
 int insert(long id,String biz,long actor,long tenant,String ticket,String reason,String profile,LocalDateTime expires);
 @Update("UPDATE plt_support_request SET status=#{status},submitted_at=CASE WHEN #{status}='SUBMITTED' THEN #{now} ELSE submitted_at END,version=version+1 WHERE id=#{id} AND version=#{version}")
 int update(long id,int version,String status,LocalDateTime now);
 @Insert("INSERT INTO plt_support_approval(id,request_id,operator_id,reviewer_id,decision,comment,reviewed_at) VALUES(#{id},#{request},#{operator},#{reviewer},#{decision},#{comment},#{now})")
 int approve(long id,long request,long operator,long reviewer,String decision,String comment,LocalDateTime now);
 @Insert("INSERT INTO plt_support_request_event(id,request_id,actor_id,request_version,action,comment,occurred_at) VALUES(#{id},#{request},#{actor},#{version},#{action},#{comment},#{now})")
 int event(long id,long request,long actor,int version,String action,String comment,LocalDateTime now);
 @Select("SELECT CAST(e.id AS CHAR) AS id,CAST(e.actor_id AS CHAR) AS actor_id,u.display_name AS actor_name,e.request_version,e.action,e.comment,e.occurred_at FROM plt_support_request_event e JOIN plt_user u ON u.id=e.actor_id WHERE e.request_id=#{id} ORDER BY e.request_version")
 List<SupportRequestModels.Event> events(long id);
}
