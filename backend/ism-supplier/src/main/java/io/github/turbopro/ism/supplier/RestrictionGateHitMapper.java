package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RestrictionGateHitMapper extends TenantScopedMapper {
    record Row(long id,long supplierId,long actorId,String actionCode,String decision,String hitCode,String sourceId,LocalDate businessDate,LocalDateTime createdAt){}
    @Insert("INSERT INTO sup_restriction_gate_hit(id,tenant_id,supplier_id,actor_id,action_code,decision,hit_code,source_id,business_date) VALUES(#{id},#{tenantId},#{supplierId},#{actorId},#{actionCode},#{decision},#{hitCode},#{sourceId},#{businessDate})")
    int insert(long id,long tenantId,long supplierId,long actorId,String actionCode,String decision,String hitCode,String sourceId,LocalDate businessDate);
    @Select("SELECT COUNT(*) FROM sup_restriction_gate_hit WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}")
    long count(long tenantId,long supplierId);
    @Select("SELECT id,supplier_id,actor_id,action_code,decision,hit_code,source_id,business_date,created_at FROM sup_restriction_gate_hit WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<Row> list(long tenantId,long supplierId,int offset,int size);
}
