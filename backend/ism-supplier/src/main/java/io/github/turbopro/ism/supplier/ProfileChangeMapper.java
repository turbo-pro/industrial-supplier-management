package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ProfileChangeMapper extends TenantScopedMapper {
    String FIELDS="id,supplier_id,request_key,base_version,before_json,after_json,reason,status,created_by,created_at,reviewed_at,review_comment,version";
    @Select("SELECT "+FIELDS+" FROM sup_profile_change WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND request_key=#{key} FOR UPDATE")
    ProfileChangeModels.Row byKey(long tenantId,long supplierId,String key);
    @Select("SELECT "+FIELDS+" FROM sup_profile_change WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id} FOR UPDATE")
    ProfileChangeModels.Row lock(long tenantId,long supplierId,long id);
    @Select("SELECT COUNT(*) FROM sup_profile_change WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}")
    long count(long tenantId,long supplierId);
    @Select("SELECT "+FIELDS+" FROM sup_profile_change WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<ProfileChangeModels.Row> list(long tenantId,long supplierId,int offset,int size);
    @Insert("INSERT INTO sup_profile_change(id,tenant_id,supplier_id,request_key,base_version,before_json,after_json,reason,created_by,created_at) VALUES(#{id},#{tenantId},#{supplierId},#{key},#{baseVersion},#{before},#{after},#{reason},#{actorId},UTC_TIMESTAMP(3))")
    int insert(long id,long tenantId,long supplierId,String key,int baseVersion,String before,String after,String reason,long actorId);
    @Update("UPDATE sup_profile_change SET status=#{status},reviewed_by=#{actorId},reviewed_at=UTC_TIMESTAMP(3),review_comment=#{comment},version=version+1 WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND id=#{id} AND status='SUBMITTED' AND version=#{version}")
    int finish(long tenantId,long supplierId,long id,String status,long actorId,String comment,int version);
    @Update("UPDATE sup_supplier SET short_name=NULLIF(#{fields.shortName},''),industry=NULLIF(#{fields.industry},''),country_code=#{fields.countryCode},province=NULLIF(#{fields.province},''),city=NULLIF(#{fields.city},''),address=NULLIF(#{fields.address},''),website=NULLIF(#{fields.website},''),updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{supplierId} AND deleted=0 AND status IN ('DRAFT','ACTIVE') AND version=#{baseVersion}")
    int apply(long tenantId,long supplierId,long actorId,int baseVersion,ProfileChangeModels.Fields fields);
}
