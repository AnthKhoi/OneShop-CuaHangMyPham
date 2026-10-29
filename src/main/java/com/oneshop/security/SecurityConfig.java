package com.oneshop.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/wishlist/toggle"))
                .addFilterBefore(new org.springframework.web.filter.CharacterEncodingFilter("UTF-8", true, true), org.springframework.security.web.session.DisableEncodeUrlFilter.class)
                .authenticationProvider(authenticationProvider())
                // Use IF_REQUIRED or STATELESS. Since we use JWT with cookies and Thymeleaf flash messages,
                // IF_REQUIRED allows flash attributes on redirects while authenticating via JWT.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.FORWARD, jakarta.servlet.DispatcherType.INCLUDE, jakarta.servlet.DispatcherType.ERROR).permitAll()
                        // Admin routes requiring specific roles
                        .requestMatchers("/admin/users/**", "/admin/branches/**").hasAnyRole("ADMIN", "OWNER")
                        .requestMatchers("/admin/promotions/**", "/admin/media/**", "/admin/transfers/**").hasAnyRole("ADMIN", "OWNER", "BRANCH_MANAGER")
                        .requestMatchers("/admin/inventory/adjust").hasAnyRole("ADMIN", "OWNER", "BRANCH_MANAGER")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "OWNER", "BRANCH_MANAGER", "SALES_STAFF")
                        
                        // Shipper delivery portal
                        .requestMatchers("/shipper/**").hasAnyRole("SHIPPER", "ADMIN")
                        
                        // Allow confirmation page (controller enforces ownership or secret token)
                        .requestMatchers("/orders/*/confirmation").permitAll()
                        // Allow wishlist toggle (controller handles 401 JSON for AJAX)
                        .requestMatchers("/wishlist/toggle").permitAll()
                        // Customer private routes
                        .requestMatchers("/account/**", "/orders/**", "/checkout", "/checkout/**", "/wishlist", "/wishlist/**").authenticated()
                        
                        // Public storefront routes and assets
                        .requestMatchers(
                                "/api/**",
                                "/",
                                "/products",
                                "/products/**",
                                "/categories",
                                "/categories/**",
                                "/brands",
                                "/brands/**",
                                "/search",
                                "/search/**",
                                "/cart",
                                "/cart/**",
                                "/policy",
                                "/policy/**",
                                "/auth",
                                "/auth/**",
                                "/decorators/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/error",
                                "/error/**",
                                "/favicon.ico"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout
                        .logoutRequestMatcher(new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/auth/logout"))
                        .logoutSuccessUrl("/auth/login?logout=true")
                        .deleteCookies("ONESHOP_JWT", "JSESSIONID")
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String uri = request.getRequestURI();
                            if (uri.startsWith("/admin") || uri.startsWith("/account") || uri.startsWith("/checkout") || uri.startsWith("/cart")) {
                                response.sendRedirect("/auth/login?redirect=" + uri);
                            } else {
                                response.sendRedirect("/auth/login");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.sendRedirect("/error/403");
                        })
                );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
