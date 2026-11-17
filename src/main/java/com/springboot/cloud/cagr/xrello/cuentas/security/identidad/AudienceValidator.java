package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Valida {@code aud} (CONTRATO_PLATAFORMA.md 1.2): el token solo vale si alguna de sus audiencias es
 * un servicio que ESTE servicio acepta. Se aplica UNICAMENTE al emisor ms3-oauth (el emisor local
 * HS256 sigue vivo y no emite {@code aud}: ver {@link EmisoresConfig}).
 *
 * <p>Sin {@code aud}, o con un {@code aud} ajeno, el token es invalido PARA ESTE SERVICIO: 401, no 403
 * (no es un usuario sin permiso, es un token emitido para otro destinatario). Un {@code aud} de un
 * solo elemento puede llegar como cadena o como arreglo; {@link Jwt#getAudience()} normaliza ambos.</p>
 */
public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    /** Inicio del mensaje de rechazo; {@link IdentidadAuthenticationEntryPoint} lo usa para devolverlo en el cuerpo. */
    public static final String PREFIJO_MENSAJE = "El token no es para este servicio";

    private final Set<String> aceptadas;

    public AudienceValidator(Collection<String> aceptadas) {
        if (aceptadas == null || aceptadas.isEmpty()) {
            throw new IllegalArgumentException("Debe haber al menos una audiencia aceptada (app.security.audiences)");
        }
        this.aceptadas = Set.copyOf(aceptadas);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audiencias = jwt.getAudience();
        if (audiencias != null && audiencias.stream().anyMatch(aceptadas::contains)) {
            return OAuth2TokenValidatorResult.success();
        }
        String recibida = audiencias == null || audiencias.isEmpty() ? "ausente" : String.valueOf(audiencias);
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                PREFIJO_MENSAJE + ": audiencia " + recibida + ", este servicio acepta " + aceptadas, null));
    }
}
