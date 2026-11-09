package terminus.co.edu.ufps.codecup.shared.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Una sola cadena para todo el backend. El access token de Supabase se valida con
 * el JWKS público (propiedades spring.security.oauth2.resourceserver.jwt.*).
 * Los roles los pone {@link SupabaseJwtConverter} leyendo la BD.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SupabaseJwtConverter supabaseJwtConverter;
    private final ProblemDetailAuthHandlers authHandlers;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Spec 0029: Explorar y el detalle de un torneo, sin sesión.
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        // Spec 0024: ver una invitación antes de tener cuenta (pantalla N-4).
                        .requestMatchers(HttpMethod.GET, "/api/auth/invitaciones/*").permitAll()
                        // Analytics: consulta pública (tablas, goleadores, premios).
                        .requestMatchers(HttpMethod.GET, "/api/analytics/**").permitAll()
                        // Disparadores de snapshots: se autentican con el shared secret
                        // X-Webhook-Secret dentro del propio controller, no con JWT.
                        .requestMatchers(HttpMethod.POST,
                                "/api/analytics/webhooks/**",
                                "/api/analytics/torneos/*/recompute").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(supabaseJwtConverter))
                        .authenticationEntryPoint(authHandlers)
                        .accessDeniedHandler(authHandlers))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authHandlers)
                        .accessDeniedHandler(authHandlers));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend-urls}") List<String> origins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(origins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
