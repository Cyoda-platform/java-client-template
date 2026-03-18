package com.java_template.application.config;

import com.java_template.application.auth.AuthService;
import com.java_template.application.auth.AuthorizationFilter;
import com.java_template.application.auth.JwtTokenProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Security configuration for TMS API
 */
@Configuration
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "app.auth.filter.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AuthorizationFilter> authorizationFilter(
            JwtTokenProvider tokenProvider,
            AuthService authService) {
        FilterRegistrationBean<AuthorizationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new AuthorizationFilter(tokenProvider, authService));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }
}

