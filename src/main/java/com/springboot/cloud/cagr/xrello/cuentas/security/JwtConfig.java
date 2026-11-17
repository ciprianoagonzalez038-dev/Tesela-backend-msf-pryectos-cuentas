package com.springboot.cloud.cagr.xrello.cuentas.security;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

// TRANSITORIO: migra al Authorization Server — esta clase entera (emision propia de access
// tokens) desaparece cuando el ecosistema tenga un Authorization Server OAuth2 centralizado; ver
// XRELLO_CONTRATO.md, seccion "MIGRACION A OAUTH2". El validador de {@link JwtDecoder}
// (que SI persiste, solo cambia de implementacion) vive aparte, en {@link JwtDecoderConfig}.
/**
 * Emisor de JWT HS256 con clave simetrica compartida ({@code ${XRELLO_JWT_SECRET}}), sin agregar
 * una libreria JWT de terceros: {@code spring-boot-starter-oauth2-resource-server} ya trae
 * {@code spring-security-oauth2-jose} (Nimbus), que provee {@link JwtEncoder} a partir de una
 * clave simetrica — sin necesidad del proyecto completo "Spring Authorization Server".
 *
 * <p>La validacion de longitud minima de la clave vive en {@link JwtProperties#validar()}, no
 * aqui — la comparte con {@link JwtDecoderConfig}, que construye su propia {@code SecretKeySpec}
 * a partir de la misma {@code secret} para validar (en vez de emitir) tokens.</p>
 */
@Configuration
public class JwtConfig {

    private final JwtProperties jwtProperties;

    public JwtConfig(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Bean
    JwtEncoder jwtEncoder() {
        byte[] bytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        SecretKeySpec claveSecreta = new SecretKeySpec(bytes, "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(claveSecreta)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSource);
    }
}
