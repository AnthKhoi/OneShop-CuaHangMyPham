package com.oneshop.config;

import org.sitemesh.builder.SiteMeshFilterBuilder;
import org.sitemesh.config.ConfigurableSiteMeshFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SiteMeshFilterConfig {

    @Bean
    public FilterRegistrationBean<ConfigurableSiteMeshFilter> siteMeshFilter() {
        FilterRegistrationBean<ConfigurableSiteMeshFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new ConfigurableSiteMeshFilter() {
            @Override
            protected void applyCustomConfiguration(SiteMeshFilterBuilder builder) {
                // Clear default /WEB-INF/decorators/ prefix so Spring MVC mappings resolve directly
                builder.setDecoratorPrefix("");

                // Admin decorator
                builder.addDecoratorPath("/admin*", "/decorators/admin-layout")
                       .addDecoratorPath("/admin/**", "/decorators/admin-layout")
                       
                       // Storefront decorator
                       .addDecoratorPath("/*", "/decorators/store-layout")
                       .addDecoratorPath("/products/**", "/decorators/store-layout")
                       .addDecoratorPath("/categories/**", "/decorators/store-layout")
                       .addDecoratorPath("/brands/**", "/decorators/store-layout")
                       .addDecoratorPath("/search/**", "/decorators/store-layout")
                       .addDecoratorPath("/cart/**", "/decorators/store-layout")
                       .addDecoratorPath("/checkout/**", "/decorators/store-layout")
                       .addDecoratorPath("/account/**", "/decorators/store-layout")
                       .addDecoratorPath("/orders/**", "/decorators/store-layout")
                       .addDecoratorPath("/wishlist", "/decorators/store-layout")
                       .addDecoratorPath("/wishlist/**", "/decorators/store-layout")
                       
                       // Excluded paths
                       .addExcludedPath("/shipper")
                       .addExcludedPath("/shipper/**")
                       .addExcludedPath("/admin/pos/receipt/**")
                       .addExcludedPath("/decorators/**")
                       .addExcludedPath("/auth/**")
                       .addExcludedPath("/api/**")
                       .addExcludedPath("/css/**")
                       .addExcludedPath("/js/**")
                       .addExcludedPath("/images/**")
                       .addExcludedPath("/error")
                       .addExcludedPath("/error/**")
                       .addExcludedPath("/favicon.ico");
            }
        });
        filterRegistrationBean.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST);
        filterRegistrationBean.addUrlPatterns("/*");
        filterRegistrationBean.setOrder(1);
        return filterRegistrationBean;
    }
}
