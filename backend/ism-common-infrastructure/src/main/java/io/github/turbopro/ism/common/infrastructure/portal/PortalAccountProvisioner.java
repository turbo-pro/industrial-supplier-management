package io.github.turbopro.ism.common.infrastructure.portal;

/** IAM owns portal credentials; callers must already hold the invitation's supplier lock. */
public interface PortalAccountProvisioner {
    void create(long accountId,long supplierId,String username,String displayName,String invitationEmail,String password);
}
