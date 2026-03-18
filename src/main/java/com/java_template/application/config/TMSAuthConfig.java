package com.java_template.application.config;

import com.java_template.application.auth.AuthorizationFilter;
import com.java_template.application.auth.JwtTokenProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TMS API Authorization configuration
 */
@Configuration
public class TMSAuthConfig {

    @Bean
    @ConditionalOnProperty(name = "app.auth.filter.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AuthorizationFilter> authorizationFilter(
            JwtTokenProvider tokenProvider) {
        FilterRegistrationBean<AuthorizationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new AuthorizationFilter(tokenProvider));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }
}

