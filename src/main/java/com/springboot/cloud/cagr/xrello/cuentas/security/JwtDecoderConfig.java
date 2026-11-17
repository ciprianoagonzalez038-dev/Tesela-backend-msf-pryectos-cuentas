package com.springboot.cloud.cagr.xrello.cuentas.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Decoder del emisor LOCAL (HS256, TRANSITORIO). Convive con el del Authorization Server (ms3-oauth,
 * RS256), que se arma en {@code security.identidad.EmisoresConfig}; este es el decoder cuyo emisor
 * ({@code iss}) es {@link TokenService#EMISOR} y por eso ahora valida tambien ese claim.
 *
 * <p><b>Migracion — corregida:</b> la recomendacion anterior de reemplazar este bean por
 * {@code JwtDecoders.fromIssuerLocation(issuerUri)} era incorrecta: esa llamada consulta el
 * discovery del emisor al ARRANCAR y el servicio no levantaria con el Authorization Server caido.
 * El decoder externo se configura con las dos propiedades estandar de Spring Boot juntas,
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} e {@code ...issuer-uri}: el issuer
 * se valida localmente y el JWKS se descarga de forma perezosa. Al apagar el emisor local se
 * elimina este bean y {@code TokenService}; ver XRELLO_CONTRATO.md §14.</p>
 */
@Configuration
public class JwtDecoderConfig {

    private final JwtProperties jwtProperties;

    public JwtDecoderConfig(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        byte[] bytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        SecretKeySpec claveSecreta = new SecretKeySpec(bytes, "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(claveSecreta)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenService.EMISOR));
        return decoder;
    }
}
