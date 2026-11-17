package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import java.net.MalformedURLException;
import java.net.URL;

import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/**
 * Decoder del Authorization Server con una fuente de JWKS **tolerante a caidas del emisor**.
 *
 * <p><b>Por que existe — numero medido, no hipotesis:</b> con {@code NimbusJwtDecoder.withJwkSetUri(url).build()}
 * (el decoder por defecto, Spring Security 6.5.10 / Nimbus 9.37.4) la cache del JWKS vive **5 minutos**; si
 * el emisor cae mas que eso, el servicio rechaza **todos** los tokens validos (medido: primera falla a los
 * 300 s). Es el mismo acople de disponibilidad que ya se rechazo al elegir la vista de membresia (§19) y al
 * descartar {@code fromIssuerLocation} (§14.1). Ver XRELLO_CONTRATO.md §14.3.</p>
 *
 * <p><b>Comportamiento:</b> las llaves se refrescan cada {@code cacheTtl} con el emisor arriba; si la descarga
 * falla, se sigue validando con la ultima copia buena durante {@code outageTolerance} contado desde la
 * ultima descarga exitosa; pasado ese limite se vuelve a rechazar todo (la desconfianza esta ACOTADA: una
 * llave retirada por compromiso deja de confiarse, como maximo, cuando vence ese plazo). Una llave nueva
 * (kid desconocido) provoca una descarga, como mucho una cada {@code minRefetchInterval} para que un token con
 * un kid inventado no convierta al emisor en blanco de una avalancha. Los timeouts de descarga son cortos
 * para que un emisor <i>colgado</i> (no solo caido) no deje hilos esperando.</p>
 *
 * <p>Se descarto la cache de Spring sin expiracion: nunca olvida una llave retirada.</p>
 *
 * <p>Limites conocidos: un servicio que arranca en frio con el emisor caido no valida nada hasta su primera
 * descarga (ninguna configuracion lo evita); y el rechazo por JWKS no disponible sale como 401, no 503.</p>
 */
public final class JwksDecoderFactory {

    private static final int LIMITE_TAMANO_BYTES = 51_200;

    private JwksDecoderFactory() {
    }

    public static NimbusJwtDecoder crear(String jwkSetUri, String issuer, JwksProperties props) {
        props.validar();
        URL url;
        try {
            url = new URL(jwkSetUri);
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("jwk-set-uri invalida: " + jwkSetUri, ex);
        }

        DefaultResourceRetriever retriever = new DefaultResourceRetriever(
                (int) props.getConnectTimeout().toMillis(),
                (int) props.getReadTimeout().toMillis(),
                LIMITE_TAMANO_BYTES);

        JWKSourceBuilder<SecurityContext> builder = JWKSourceBuilder.create(url, retriever)
                .cache(props.getCacheTtl().toMillis(), props.getRefreshTimeout().toMillis())
                .outageTolerant(props.getOutageTolerance().toMillis())
                // Sin refresh-ahead (refresco en segundo plano antes de vencer): el refresco es bajo demanda,
                // con menos piezas moviles; con un TTL de minutos no aporta y exigiria TTL > 30 s.
                .refreshAheadCache(false)
                .retrying(false);
        if (props.getMinRefetchInterval().isZero()) {
            builder.rateLimited(false);
        } else {
            builder.rateLimited(props.getMinRefetchInterval().toMillis());
        }
        JWKSource<SecurityContext> fuente = builder.build();

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(
                JOSEObjectType.JWT, new JOSEObjectType("at+jwt"), null));
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, fuente));
        // Igual que el builder de Spring: la validacion de claims (vigencia, emisor) la hace el
        // OAuth2TokenValidator del decoder, no el procesador de Nimbus (evitaria validarla dos veces).
        processor.setJWTClaimsSetVerifier((claims, context) -> { });

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
