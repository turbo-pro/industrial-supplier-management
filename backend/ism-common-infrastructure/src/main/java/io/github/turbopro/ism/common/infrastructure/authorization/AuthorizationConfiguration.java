package io.github.turbopro.ism.common.infrastructure.authorization;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthorizationConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuthorizationGrantLoader.class)
    AuthorizationGrantLoader denyAllAuthorizationGrantLoader() {
        return new DenyAllAuthorizationGrantLoader();
    }
}
