package io.github.turbopro.ism.bootstrap;
import io.github.turbopro.ism.common.api.support.SupportRoleAuthority;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;
class SupportRoleAuthoritySnapshotIT extends SiteProjectSupport {
 @Autowired SupportRoleAuthority authority;@Autowired PlatformTransactionManager transactions;
 @Test void delegationModuleReadCannotReuseOuterIdempotencySnapshot(){var f=fixture();String sql="SELECT pm.enabled FROM plt_package_module pm JOIN plt_tenant t ON t.package_version_id=pm.package_version_id JOIN plt_module m ON m.id=pm.module_id WHERE t.id=? AND m.module_code='SUPPLIER'";
  new TransactionTemplate(transactions).executeWithoutResult(status->{assertThat(jdbc.queryForObject(sql,Integer.class,f.tenant())).isOne();try(var c=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());var s=c.prepareStatement("UPDATE plt_package_module pm JOIN plt_tenant t ON t.package_version_id=pm.package_version_id JOIN plt_module m ON m.id=pm.module_id SET pm.enabled=0 WHERE t.id=? AND m.module_code='SUPPLIER'")){s.setLong(1,f.tenant());assertThat(s.executeUpdate()).isOne();}catch(Exception e){throw new IllegalStateException(e);}assertThat(jdbc.queryForObject(sql,Integer.class,f.tenant())).isOne();try(var context=TenantContext.openSystem(f.tenant())){assertThat(authority.resolve(f.tenant(),f.role())).isNull();}});
 }
}
