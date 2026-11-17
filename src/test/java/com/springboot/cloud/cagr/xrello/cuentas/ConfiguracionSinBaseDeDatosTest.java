package com.springboot.cloud.cagr.xrello.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.springboot.cloud.cagr.xrello.cuentas.config.ApiProperties;
import com.springboot.cloud.cagr.xrello.cuentas.config.OpenApiConfig;
import com.springboot.cloud.cagr.xrello.cuentas.security.JwtDecoderConfig;
import com.springboot.cloud.cagr.xrello.cuentas.security.JwtProperties;

import io.swagger.v3.oas.models.OpenAPI;

/**
 * Verifica SOLO lo que genuinamente no necesita persistencia: que {@link JwtDecoderConfig} arma
 * un {@link JwtDecoder} válido y que las {@code @ConfigurationProperties}
 * ({@link ApiProperties}/{@link JwtProperties}) se ligan correctamente — ver el mismo patrón en
 * {@code ms-xrello-tableros/ConfiguracionSinBaseDeDatosTest} (XRELLO_CONTRATO.md §18).
 *
 * <p>{@code classes = {...}} (en vez de {@code classes = MsXrelloCuentasApplication.class}): ninguna
 * de estas cuatro clases es {@code @SpringBootApplication}, así que
 * {@code @EnableAutoConfiguration} nunca se activa — sin JPA en el contexto, ningún repositorio se
 * intenta crear, y no hace falta ningún {@code @MockitoBean}.</p>
 */
@SpringBootTest(
        classes = {ApiProperties.class, OpenApiConfig.class, JwtProperties.class, JwtDecoderConfig.class},
        properties = {
                "app.security.jwt.secret=solo-para-este-test-de-configuracion-nunca-real-32b",
                // La red SecretosSinResolverListener exige que ninguna propiedad secreta referencie una variable sin
                // definir, aunque este test no use la BD ni ms-usuario: se le da un valor inocuo a cada una.
                "spring.datasource.username=sin-bd", "spring.datasource.password=sin-bd",
                "app.directorio.usuario.internal-token=sin-ms-usuario-en-este-test-32-caracteres",
                "app.mensajeria-client.api-key=sin-mensajeria-en-este-test",
                "app.api.title=Xrello Cuentas (test)",
                "app.api.description=Test",
                "app.api.version=1.0.0-test",
                "app.api.contact.name=Dev",
                "app.api.contact.email=dev@test.com",
                "app.api.server.url=http://localhost:8007",
                "app.api.server.description=Test"
        }
)
@EnableConfigurationProperties({ApiProperties.class, JwtProperties.class})
@DisplayName("Configuración sin base de datos: JwtDecoderConfig y @ConfigurationProperties")
class ConfiguracionSinBaseDeDatosTest {

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private ApiProperties apiProperties;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private OpenAPI openAPI;

    @Test
    @DisplayName("JwtDecoderConfig construye un JwtDecoder valido a partir del secreto configurado")
    void jwtDecoderSeConstruyeCorrectamente() {
        assertThat(jwtDecoder).isNotNull();
    }

    @Test
    @DisplayName("ApiProperties liga sus valores desde application.properties/properties del test")
    void apiPropertiesSeLigaCorrectamente() {
        assertThat(apiProperties.getTitle()).isEqualTo("Xrello Cuentas (test)");
        assertThat(apiProperties.getServer().getUrl()).isEqualTo("http://localhost:8007");
        assertThat(apiProperties.getContact().getEmail()).isEqualTo("dev@test.com");
    }

    @Test
    @DisplayName("JwtProperties liga el secreto configurado")
    void jwtPropertiesSeLigaCorrectamente() {
        assertThat(jwtProperties.getSecret()).isEqualTo("solo-para-este-test-de-configuracion-nunca-real-32b");
    }

    @Test
    @DisplayName("OpenApiConfig produce un bean OpenAPI usando los valores de ApiProperties")
    void openApiConfigProduceElBeanOpenAPI() {
        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Xrello Cuentas (test)");
    }
}
