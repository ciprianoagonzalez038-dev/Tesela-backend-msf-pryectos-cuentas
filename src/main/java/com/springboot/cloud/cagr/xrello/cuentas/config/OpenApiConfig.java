package com.springboot.cloud.cagr.xrello.cuentas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración global de la especificación OpenAPI 3.1 (Swagger).
 *
 * <p>Los metadatos se inyectan a través de {@link ApiProperties}, que mapea
 * el prefijo {@code app.api} del {@code application.properties}.
 * Esto permite sobreescribir título, versión, contacto y URL del servidor
 * por perfil de entorno sin tocar el código.</p>
 *
 * <h3>URLs disponibles en tiempo de ejecución</h3>
 * <ul>
 *   <li>Swagger UI   → {@code /swagger-ui.html}</li>
 *   <li>OpenAPI JSON → {@code /api-docs}</li>
 *   <li>OpenAPI YAML → {@code /api-docs.yaml}  ← importar en Redoc / Postman</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    private final ApiProperties apiProperties;

    @Bean
    OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(apiProperties.getTitle())
                        .description(apiProperties.getDescription())
                        .version(apiProperties.getVersion())
                        .contact(new Contact()
                                .name(apiProperties.getContact().getName())
                                .email(apiProperties.getContact().getEmail()))
                        .license(new License()
                                .name("Uso interno — todos los derechos reservados")
                                .url("#")))
                .servers(List.of(
                        new Server()
                                .url(apiProperties.getServer().getUrl())
                                .description(apiProperties.getServer().getDescription())));
    }
}
