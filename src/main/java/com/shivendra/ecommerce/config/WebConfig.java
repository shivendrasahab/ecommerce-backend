package com.shivendra.ecommerce.config;

import com.shivendra.ecommerce.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the token-auth interceptor on the endpoints that act on behalf of a user.
 * Catalogue browsing (/products, /categories, /search) stays public.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/cart/**", "/orders/**", "/payments/**", "/auth/logout", "/auth/me")
                // gateways call the webhook server-to-server without our bearer token
                .excludePathPatterns("/payments/webhook");
    }
}
