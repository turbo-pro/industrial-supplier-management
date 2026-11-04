package io.github.turbopro.ism.common.api.support;
import java.util.List;
import java.util.Set;
/** Internal bounded role metadata, never user impersonation or a grant.
 * Caller must hold its target tenant lock and transaction while resolving delegation. */
public interface SupportRoleAuthority {
 record Role(String id,String name,int version,boolean tenantAll,Set<String> organizations){}
 List<Role> options(long tenantId);
 Role resolve(long tenantId,long roleId);
}
