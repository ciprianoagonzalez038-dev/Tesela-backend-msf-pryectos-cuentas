package com.springboot.cloud.cagr.xrello.cuentas.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.IdentidadAuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resource server JWT (HS256, ver {@link JwtConfig}) — API stateless, sin
 * sesion de servidor ni CSRF (no hay formularios ni cookies de sesion; el
 * cliente manda el access token como {@code Authorization: Bearer <jwt>} en
 * cada peticion).
 *
 * <p>{@code @EnableMethodSecurity}: habilita {@code @PreAuthorize} — usado por
 * cada endpoint que recibe un identificador de proyecto, evaluando
 * {@code @accesoProyecto.esMiembro(#codigo)}/{@code .esOwner(#codigo)}/
 * {@code .tieneRol(#codigo, ...)} (ver {@link AccesoProyecto}). Sin
 * excepciones — ver XRELLO_CONTRATO.md.</p>
 *
 * <p>La autenticacion la resuelve {@code security.identidad.EmisoresConfig}: dos emisores (local HS256
 * y ms3-oauth RS256) y, en ambos, el usuario LOCAL y sus roles de SISTEMA se leen de la BD
 * ({@code security.identidad.RolesSistema}), NO del claim {@code roles}.</p>
 *
 * <p>Sin bean {@code PasswordEncoder}: desde D28/Tesela (ver XRELLO_CONTRATO.md §14.0) ni el login
 * ni el alta comparan/escriben {@code password_hash} localmente — se delega en ms-usuario via
 * {@code security.identidad.UsuarioDirectorioClient}.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManagerResolver<HttpServletRequest> emisoresResolver,
            ObjectMapper objectMapper)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/v1/publico/**",
                        "/actuator/health",
                        "/actuator/info",
                        "/api-docs/**",
                        "/api-docs",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                    .authenticationManagerResolver(emisoresResolver)
                    .authenticationEntryPoint(new IdentidadAuthenticationEntryPoint(objectMapper)));

        return http.build();
    }
}
