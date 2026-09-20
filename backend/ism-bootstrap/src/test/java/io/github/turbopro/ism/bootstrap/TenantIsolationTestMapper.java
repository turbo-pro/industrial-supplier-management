package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TenantIsolationTestMapper extends TenantScopedMapper {
    @Insert("""
            INSERT INTO iam_user(id,tenant_id,username,display_name,password_hash,status,force_password_change)
            VALUES(#{id},#{tenantId},#{username},#{displayName},#{passwordHash},'ACTIVE',0)
            """)
    int insert(long id, long tenantId, String username, String displayName, String passwordHash);

    @Select("""
            SELECT id,tenant_id,username,display_name
            FROM iam_user WHERE tenant_id=#{tenantId} ORDER BY id
            """)
    List<TenantUserRow> findAll(long tenantId);

    @Select("""
            SELECT id,tenant_id,username,display_name
            FROM iam_user WHERE id=#{id} AND tenant_id=#{tenantId}
            """)
    TenantUserRow findById(long id, long tenantId);

    @Select("""
            SELECT id,tenant_id,username,display_name
            FROM iam_user WHERE tenant_id=#{tenantId} ORDER BY id
            """)
    List<TenantUserRow> exportAll(long tenantId);

    @Update("UPDATE iam_user SET display_name=#{displayName} WHERE id=#{id} AND tenant_id=#{tenantId}")
    int rename(long id, long tenantId, String displayName);

    @Select("SELECT id,tenant_id,username,display_name FROM iam_user WHERE username=#{username}")
    List<TenantUserRow> unsafeFindWithoutTenant(String username);

    record TenantUserRow(long id, long tenantId, String username, String displayName) {
    }
}
