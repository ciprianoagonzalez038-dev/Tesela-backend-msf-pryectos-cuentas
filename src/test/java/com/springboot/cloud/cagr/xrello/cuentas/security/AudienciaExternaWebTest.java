package com.springboot.cloud.cagr.xrello.cuentas.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.springboot.cloud.cagr.xrello.cuentas.AbstractIntegrationTest;

/** {@code aud} del emisor ms3-oauth (cadena de seguridad real); el emisor local NO lo valida. */
@DisplayName("Audiencia (aud) del emisor externo — HTTP real")
class AudienciaExternaWebTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void emisorExterno(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", EmisorExternoDePrueba::jwkSetUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> EmisorExternoDePrueba.ISSUER);
    }

    private ResponseEntity<String> get(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return restTemplate.exchange(url("/api/v1/cuentas/proyectos"), HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    @DisplayName("aud de otro servicio: 401 con mensaje claro (audiencia recibida y aceptada) y header WWW-Authenticate")
    void audAjeno() {
        ResponseEntity<String> r = get(EmisorExternoDePrueba.token("sub-aud-ajeno",
                Map.of("email", "aud-ajeno@test.com", "aud", List.of("ms-parametrizacion"))));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).contains("El token no es para este servicio").contains("ms-parametrizacion")
                .contains("ms-xrello-cuentas");
        assertThat(r.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
    }

    @Test
    @DisplayName("un token de OTRO cliente de Xrello (aud=ms-xrello-tableros solo) tampoco vale en cuentas")
    void audDeOtroServicioDeXrello() {
        ResponseEntity<String> r = get(EmisorExternoDePrueba.token("sub-aud-tableros",
                Map.of("email", "aud-tableros@test.com", "aud", "ms-xrello-tableros")));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("sin aud: 401")
    void sinAud() {
        ResponseEntity<String> r = get(EmisorExternoDePrueba.tokenSinAudiencia("sub-sin-aud",
                Map.of("email", "sin-aud@test.com")));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).contains("audiencia ausente");
    }

    @Test
    @DisplayName("aud propio entre varios, o como cadena: pasa la validacion y entra")
    void audPropio() {
        assertThat(get(EmisorExternoDePrueba.token("sub-aud-multi", Map.of("email", "aud-multi@test.com",
                "aud", List.of("ms-xrello-cuentas", "ms-xrello-tableros")))).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(EmisorExternoDePrueba.token("sub-aud-cadena", Map.of("email", "aud-cadena@test.com",
                "aud", "ms-xrello-cuentas"))).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("el emisor LOCAL (HS256) no valida aud: un token propio sin aud sigue entrando")
    void emisorLocalNoValidaAud() throws Exception {
        String email = "local-sin-aud@test.com";
        ResponseEntity<String> registro = restTemplate.postForEntity(url("/api/v1/publico/auth/registro"),
                new com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroRequest(email, "Local", null),
                String.class);
        assertThat(registro.getStatusCode().is2xxSuccessful()).as(registro.getBody()).isTrue();

        // Sin clave en el registro (ver RegistroRequest) — la PRIMERA clave real se fija al
        // verificar el correo; por convencion del doble de prueba
        // (StaticTestUsuarioDirectorioPort), el "token" de verificacion es el propio correo.
        ResponseEntity<String> verificacion = restTemplate.postForEntity(url("/api/v1/publico/auth/verificar-correo"),
                new com.springboot.cloud.cagr.xrello.cuentas.web.dto.VerificarCorreoRequest(email, "PasswordSeguro123!"),
                String.class);
        assertThat(verificacion.getStatusCode().is2xxSuccessful()).as(verificacion.getBody()).isTrue();
        String token = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(verificacion.getBody()).path("data").path("accessToken").asText();
        assertThat(get(token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
