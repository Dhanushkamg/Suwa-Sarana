package com.suwasarana.api.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers {@link AuthorizeBeforeBindingInterceptor} for every API route. */
@Configuration
public class AuthorizeBeforeBindingConfig implements WebMvcConfigurer {

    private final AuthorizeBeforeBindingInterceptor interceptor;

    public AuthorizeBeforeBindingConfig(AuthorizeBeforeBindingInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
