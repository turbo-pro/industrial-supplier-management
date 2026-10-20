package io.github.turbopro.ism.supplier;
import io.github.turbopro.ism.common.api.error.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PortalRegistrationRateGuard {
    private final PortalRegistrationRateMapper mapper;
    public PortalRegistrationRateGuard(PortalRegistrationRateMapper mapper){this.mapper=mapper;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void check(String transportPeer){
        String hash=PortalRegistrationService.hash(transportPeer);
        mapper.countAttempt(hash);Integer count=mapper.attempts(hash);mapper.cleanup();
        if(count==null || count>20)throw new ApiException(CommonErrorCode.RATE_LIMITED,"注册请求过于频繁，请稍后重试");
    }
}
