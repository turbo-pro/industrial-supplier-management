package io.github.turbopro.ism.iam.organization;
import org.apache.ibatis.annotations.*;
/** IAM tenant root has no tenant_id column. Caller derives this ID from the server context. */
@Mapper
interface OrganizationStructureRootMapper {
    @Select("SELECT status FROM iam_tenant WHERE id=#{tenantId} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    String lock(long tenantId);
}
