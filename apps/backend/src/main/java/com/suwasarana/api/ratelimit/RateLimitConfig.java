package com.suwasarana.api.ratelimit;

import com.suwasarana.api.config.RateLimitingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wiring of the abuse-protection layer.
 *
 * <p>{@link RateLimitingFilter} is a Spring bean, which Spring Boot would also register with the servlet
 * container. {@code SecurityConfig} already adds it to the security filter chain, so the container registration is
 * switched off here: the filter then runs exactly once per request, inside the security chain.
 */
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitService limits;

    public RateLimitConfig(RateLimitService limits) {
        this.limits = limits;
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(RateLimitingFilter filter) {
        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserRateLimitInterceptor(limits)).addPathPatterns("/api/**");
    }
}
