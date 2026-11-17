package com.springboot.cloud.cagr.xrello.cuentas.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;

/**
 * Propiedades del JWT bajo {@code app.security.jwt}. {@code secret} viene de
 * {@code ${XRELLO_JWT_SECRET}} — sin default en el perfil {@code docker} (ver
 * {@code application-docker.properties}); {@code application-local.properties}
 * trae un default de desarrollo documentado.
 *
 * <p><b>Longitud minima de la clave — falla el arranque, no en silencio.</b> HS256 exige una
 * clave de al menos 256 bits (32 bytes); una clave mas corta debilita la firma sin que nada lo
 * avise en tiempo de ejecucion normal. {@link #validar()} corre en {@code @PostConstruct}: si
 * la clave es corta, lanza {@link IllegalStateException} ANTES de que el contexto de Spring
 * termine de arrancar. Centralizado aqui (y no en {@link JwtConfig}) porque TANTO
 * {@link JwtConfig} (emite, {@code JwtEncoder}) COMO {@link JwtDecoderConfig} (valida,
 * {@code JwtDecoder}) construyen su propia {@code SecretKeySpec} a partir de la MISMA clave —
 * duplicar la validacion en ambas clases, ademas de repetirla, dependeria del orden en que
 * Spring cree sus beans (no garantizado) para decidir cual de las dos falla primero.</p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    private static final int LONGITUD_MINIMA_BYTES = 32;

    /** Valor que estuvo versionado como default (publico en el historial): se rechaza aunque mida 32+ bytes. */
    private static final String SECRETO_PUBLICO_ANTIGUO =
            "xrello-jwt-secreto-de-desarrollo-local-2026-no-usar-en-produccion";

    private String secret;
    private long accessTokenMinutes = 30;
    private long refreshTokenDays = 60;

    @PostConstruct
    void validar() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "XRELLO_JWT_SECRET no esta configurado (app.security.jwt.secret)");
        }
        if (secret.contains("${")) {
            // Spring Boot NO falla al enlazar un ${VAR} sin resolver: deja el texto literal como valor.
            throw new IllegalStateException("XRELLO_JWT_SECRET llego como el texto literal '${...}': la variable de "
                    + "entorno NO esta definida. Copia .env.example a .env y define un valor propio.");
        }
        if (SECRETO_PUBLICO_ANTIGUO.equals(secret)) {
            throw new IllegalStateException("XRELLO_JWT_SECRET es el valor que estuvo versionado como default (publico). "
                    + "Genera uno propio.");
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < LONGITUD_MINIMA_BYTES) {
            throw new IllegalStateException(
                    "XRELLO_JWT_SECRET debe tener al menos " + LONGITUD_MINIMA_BYTES
                            + " bytes para HS256 (tiene " + bytes + ")");
        }
    }
}
