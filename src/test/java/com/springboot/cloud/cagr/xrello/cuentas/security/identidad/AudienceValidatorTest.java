package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

@DisplayName("AudienceValidator")
class AudienceValidatorTest {

    private final AudienceValidator validador = new AudienceValidator(List.of("ms-xrello-cuentas"));

    private static Jwt jwt(Object aud) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject("7")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (aud != null) {
            b.claim("aud", aud);
        }
        return b.build();
    }

    @Test
    @DisplayName("aud propio como arreglo o como cadena: valido")
    void audPropio() {
        assertThat(validador.validate(jwt(List.of("ms-xrello-cuentas"))).hasErrors()).isFalse();
        assertThat(validador.validate(jwt("ms-xrello-cuentas")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("aud con varios servicios entre los que esta el propio: valido")
    void audMultiple() {
        assertThat(validador.validate(jwt(List.of("ms-lista", "ms-xrello-cuentas"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("aud ajeno: invalido, con mensaje que dice la audiencia recibida y la aceptada")
    void audAjeno() {
        var r = validador.validate(jwt(List.of("ms-parametrizacion")));

        assertThat(r.hasErrors()).isTrue();
        String msg = r.getErrors().iterator().next().getDescription();
        assertThat(msg).startsWith(AudienceValidator.PREFIJO_MENSAJE).contains("ms-parametrizacion").contains("ms-xrello-cuentas");
        assertThat(r.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
    }

    @Test
    @DisplayName("sin aud o aud vacio: invalido (un token sin destinatario no vale en ninguna parte)")
    void sinAud() {
        assertThat(validador.validate(jwt(null)).hasErrors()).isTrue();
        assertThat(validador.validate(jwt(List.of())).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("exige al menos una audiencia aceptada configurada (no se puede aceptar todo por omision)")
    void sinConfiguracion() {
        assertThatThrownBy(() -> new AudienceValidator(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
