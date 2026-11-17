package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Propiedades bajo {@code app.directorio.usuario}: como llegar a ms-usuario para autenticar y dar
 * de alta identidades (ver {@link UsuarioDirectorioClient}). Mismo patron que
 * {@code app.oauth.usuario} de ms3-oauth.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.directorio.usuario")
public class DirectorioUsuarioProperties {

    /** Nombre logico resuelto via Eureka, nunca una URL literal. */
    private String baseUrl = "http://ms-usuario";

    /** Secreto compartido (header X-Internal-Token); mismo valor que app.internal.token de ms-usuario. */
    private String internalToken;
}
