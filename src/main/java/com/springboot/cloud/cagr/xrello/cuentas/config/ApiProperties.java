package com.springboot.cloud.cagr.xrello.cuentas.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propiedades personalizadas de la API cargadas desde {@code application.properties}
 * bajo el prefijo {@code app.api}.
 *
 * <p>Al estar anotadas con {@link ConfigurationProperties}, Spring Boot genera
 * automáticamente los metadatos de configuración ({@code spring-configuration-metadata.json})
 * durante la compilación, lo que habilita validación y autocompletado en el IDE.</p>
 *
 * <p>Para sobreescribir valores por entorno, basta con añadir un perfil:
 * {@code application-dev.properties}, {@code application-qa.properties},
 * {@code application-prod.properties}.</p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.api")
public class ApiProperties {

    /** Título de la API mostrado en Swagger UI. */
    private String title;

    /** Descripción extendida de la API. */
    private String description;

    /** Versión de la API (ej. {@code 1.0.0}). */
    private String version;

    /** Datos de contacto del equipo responsable. */
    private Contact contact = new Contact();

    /** Servidor activo en el entorno actual. */
    private Server server = new Server();

    // ── Clases internas ──────────────────────────────────────────────────────

    @Getter
    @Setter
    public static class Contact {

        /** Nombre del equipo o persona de contacto. */
        private String name;

        /** Correo electrónico de contacto. */
        private String email;
    }

    @Getter
    @Setter
    public static class Server {

        /** URL base del servidor (ej. {@code https://api.empresa.com}). */
        private String url;

        /** Descripción del entorno (ej. {@code Producción (PROD)}). */
        private String description;
    }
}
