package com.springboot.cloud.cagr.xrello.cuentas.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JwtProperties — validacion del secreto al arrancar")
class JwtPropertiesSecretoTest {

    private static JwtProperties con(String secret) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret);
        return p;
    }

    @Test
    @DisplayName("el literal ${XRELLO_JWT_SECRET} (variable sin definir enlazada como texto) falla nombrando la variable")
    void literal() {
        assertThatThrownBy(() -> con("${XRELLO_JWT_SECRET}").validar()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("XRELLO_JWT_SECRET").hasMessageContaining("literal");
    }

    @Test
    @DisplayName("ausente, en blanco o corto: falla")
    void ausenteOCorto() {
        assertThatThrownBy(() -> con(null).validar()).hasMessageContaining("no esta configurado");
        assertThatThrownBy(() -> con("  ").validar()).hasMessageContaining("no esta configurado");
        assertThatThrownBy(() -> con("corto").validar()).hasMessageContaining("32");
    }

    @Test
    @DisplayName("el valor que estuvo versionado como default se rechaza aunque mida mas de 32 bytes")
    void publicoAntiguo() {
        assertThatThrownBy(() -> con("xrello-jwt-secreto-de-desarrollo-local-2026-no-usar-en-produccion").validar())
                .hasMessageContaining("versionado");
    }

    @Test
    @DisplayName("un secreto propio de 32+ bytes pasa")
    void valido() {
        assertThatCode(() -> con("x".repeat(40)).validar()).doesNotThrowAnyException();
    }
}
