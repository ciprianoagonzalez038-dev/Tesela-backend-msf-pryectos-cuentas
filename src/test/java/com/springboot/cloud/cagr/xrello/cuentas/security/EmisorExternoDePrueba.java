package com.springboot.cloud.cagr.xrello.cuentas.security;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Map;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;

/**
 * Authorization Server de mentira para los tests: sirve un JWKS REAL por HTTP (el decoder externo lo
 * descarga como lo haria con ms3-oauth, sin discovery) y firma tokens RS256. Ejercita el cableado
 * de produccion completo: resolver de emisores -> decoder por jwk-set-uri -> resolutor de identidad.
 */
public final class EmisorExternoDePrueba {

    public static final String ISSUER = "http://ms3-oauth.test";
    /** Audiencia de ESTE servicio: los tokens de prueba la llevan salvo que el test fije la suya (clave "aud"). */
    public static final String AUDIENCIA_PROPIA = "ms-xrello-cuentas";

    private static final RSAKey CLAVE;
    private static final HttpServer SERVIDOR;
    private static final NimbusJwtEncoder ENCODER;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair par = generator.generateKeyPair();
            CLAVE = new RSAKey.Builder((RSAPublicKey) par.getPublic())
                    .privateKey((RSAPrivateKey) par.getPrivate()).keyID("test-key").build();
            ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(CLAVE)));

            SERVIDOR = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] jwks = new JWKSet(CLAVE.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            SERVIDOR.createContext("/oauth2/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                exchange.getResponseBody().write(jwks);
                exchange.close();
            });
            SERVIDOR.start();
        } catch (IOException | java.security.GeneralSecurityException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private EmisorExternoDePrueba() {
    }

    public static String jwkSetUri() {
        return "http://localhost:" + SERVIDOR.getAddress().getPort() + "/oauth2/jwks";
    }

    /** Token RS256 del emisor externo con los claims dados ({@code sub} incluido). */
    public static String token(String sub, Map<String, Object> claims) {
        return token(ISSUER, sub, claims);
    }

    public static String token(String issuer, String sub, Map<String, Object> claims) {
        return emitir(issuer, sub, claims, true);
    }

    /** Token sin {@code aud}: para probar que un token sin destinatario se rechaza. */
    public static String tokenSinAudiencia(String sub, Map<String, Object> claims) {
        return emitir(ISSUER, sub, claims, false);
    }

    private static String emitir(String issuer, String sub, Map<String, Object> claims, boolean audienciaPropia) {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(issuer).subject(sub).issuedAt(ahora).expiresAt(ahora.plusSeconds(300));
        if (audienciaPropia && !claims.containsKey("aud")) {
            builder.audience(java.util.List.of(AUDIENCIA_PROPIA));
        }
        claims.forEach(builder::claim);
        return ENCODER.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-key").build(), builder.build())).getTokenValue();
    }
}
