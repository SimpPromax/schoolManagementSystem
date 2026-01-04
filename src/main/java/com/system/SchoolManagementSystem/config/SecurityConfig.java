package com.system.SchoolManagementSystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint entryPoint;
    private final JwtRequestFilter jwtRequestFilter;
    private final TenantFilter tenantFilter;

    public SecurityConfig(CustomAuthenticationEntryPoint entryPoint,
                          JwtRequestFilter jwtRequestFilter,
                          TenantFilter tenantFilter) {
        this.entryPoint = entryPoint;
        this.jwtRequestFilter = jwtRequestFilter;
        this.tenantFilter = tenantFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - NO authentication required
                        .requestMatchers(
                                // Authentication endpoints
                                "/api/auth/login",
                                "/api/auth/register",

                                // Tenant registration
                                "/api/tenants/register",

                                // Public demo endpoints
                                "/api/demo/public/**",

                                // Tenant demo endpoints (for testing)
                                "/api/tenant-demo/**",

                                // Error handling
                                "/error",

                                // Actuator endpoints
                                "/actuator/**",

                                // API documentation
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**",

                                // Root and favicon
                                "/",
                                "/favicon.ico"
                        ).permitAll()

                        // Protected endpoints - Authentication required
                        .requestMatchers("/api/auth/register-admin").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/tenant-admin/**").hasRole("TENANT_ADMIN")
                        .requestMatchers("/api/teacher/**").hasRole("TEACHER")
                        .requestMatchers("/api/student/**").hasAnyRole("STUDENT", "USER")

                        // Demo endpoints - Authentication required
                        .requestMatchers("/api/demo/protected/**").authenticated()
                        .requestMatchers("/api/demo/create", "/api/demo/all",
                                "/api/demo/{id}", "/api/demo/update/{id}",
                                "/api/demo/delete/{id}").authenticated()

                        // Tenant management - Admin only
                        .requestMatchers("/api/tenants").hasRole("ADMIN")
                        .requestMatchers("/api/tenants/**").hasRole("ADMIN")

                        // All other API endpoints require authentication
                        .requestMatchers("/api/**").authenticated()

                        // Allow all other requests (static resources, etc.)
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(entryPoint)
                );

        // Add filters with explicit ordering
        http.addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:4200", "http://localhost:8080"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "X-Tenant-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}