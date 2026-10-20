package io.github.turbopro.ism.iam.portal;
import org.apache.ibatis.annotations.*;

/** IAM tenant root is global; only the authenticated/derived context ID is passed by the provisioner. */
@Mapper
public interface PortalTenantIdentityMapper {
    @Select("SELECT status FROM iam_tenant WHERE id=#{id} FOR SHARE")
    String status(long id);
}
