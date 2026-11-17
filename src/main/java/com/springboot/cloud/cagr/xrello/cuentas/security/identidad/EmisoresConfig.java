package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.util.StringUtils;

import com.springboot.cloud.cagr.xrello.cuentas.security.TokenService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Convivencia de DOS emisores, distinguidos sin ambiguedad por el claim {@code iss}:
 *
 * <ul>
 *   <li>{@link TokenService#EMISOR} ({@code "ms-xrello-cuentas"}) → decoder HS256 local
 *       (TRANSITORIO).</li>
 *   <li>El emisor configurado en {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
 *       (ms3-oauth) → decoder RS256 por {@code jwk-set-uri}. Solo se habilita si ambas
 *       propiedades estan definidas.</li>
 * </ul>
 *
 * <p>El {@code iss} SIN verificar solo elige que decoder verifica; ese decoder comprueba firma,
 * vigencia Y que el {@code iss} sea el suyo. Un token firmado por un emisor no puede hacerse pasar
 * por el otro: cada uno se verifica con su propia clave. Un {@code iss} desconocido es 401.</p>
 *
 * <p><b>Por que {@code jwk-set-uri} + {@code issuer-uri} juntas y NO
 * {@code JwtDecoders.fromIssuerLocation}:</b> esta ultima consulta el discovery del emisor al
 * ARRANCAR, asi que el servicio no levantaria con el Authorization Server caido. Con las dos
 * propiedades el issuer se valida localmente y el JWKS se descarga de forma perezosa (primer
 * token) y se cachea. Mismo acople de disponibilidad ya rechazado al elegir la vista de membresia
 * sobre la llamada remota.</p>
 */
@Configuration
public class EmisoresConfig {

    @Bean
    AuthenticationManagerResolver<HttpServletRequest> emisoresResolver(
            JwtDecoder jwtDecoderLocal,
            OAuth2ResourceServerProperties propiedades,
            JwksProperties jwksProperties,
            ResolutorIdentidad resolutor,
            @Value("${app.security.audiences:${spring.application.name}}") List<String> audiencias) {
        Map<String, AuthenticationManager> managers = new HashMap<>();
        managers.put(TokenService.EMISOR, manager(jwtDecoderLocal, resolutor));

        OAuth2ResourceServerProperties.Jwt externo = propiedades.getJwt();
        if (StringUtils.hasText(externo.getJwkSetUri())) {
            String issuer = externo.getIssuerUri();
            if (!StringUtils.hasText(issuer)) {
                throw new IllegalStateException("spring.security.oauth2.resourceserver.jwt.issuer-uri es "
                        + "obligatorio cuando se define jwk-set-uri");
            }
            if (TokenService.EMISOR.equals(issuer)) {
                throw new IllegalStateException("El emisor externo no puede coincidir con el emisor local: "
                        + "dejaria de distinguirse que decoder verifica cada token");
            }
            // Decoder con JWKS tolerante a caidas del emisor (ver JwksDecoderFactory / §14.3): el decoder por
            // defecto rechaza TODOS los tokens a los 5 min de caida del emisor (medido).
            NimbusJwtDecoder decoder = JwksDecoderFactory.crear(externo.getJwkSetUri(), issuer, jwksProperties);
            // aud (CONTRATO_PLATAFORMA.md 1.2): SOLO para este emisor. El emisor local HS256 sigue vivo
            // y no emite aud, asi que no se le exige.
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuer), new AudienceValidator(audiencias)));
            managers.put(issuer, manager(decoder, resolutor, true));
        }
        return new JwtIssuerAuthenticationManagerResolver(managers::get);
    }

    private static AuthenticationManager manager(JwtDecoder decoder, ResolutorIdentidad resolutor) {
        return manager(decoder, resolutor, false);
    }

    private static AuthenticationManager manager(JwtDecoder decoder, ResolutorIdentidad resolutor, boolean externo) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(externo ? resolutor::autenticarTokenExterno : resolutor::autenticarTokenLocal);
        return new ProviderManager(provider);
    }
}
