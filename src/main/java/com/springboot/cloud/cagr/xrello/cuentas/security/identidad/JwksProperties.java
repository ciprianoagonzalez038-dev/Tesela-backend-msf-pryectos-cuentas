package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Propiedades bajo {@code app.security.jwks}: como se obtiene y se conserva el JWKS del Authorization
 * Server (ver {@link JwksDecoderFactory} y XRELLO_CONTRATO.md §14.3). Los valores por defecto son los
 * recomendados; solo se tocan con una razon.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.jwks")
public class JwksProperties {

    /** Cada cuanto se refrescan las llaves con el emisor arriba. */
    private Duration cacheTtl = Duration.ofMinutes(5);

    /** Tiempo maximo que una peticion espera si otra ya esta refrescando la cache. */
    private Duration refreshTimeout = Duration.ofSeconds(15);

    /**
     * Cuanto tiempo, desde la ultima descarga exitosa, se sigue confiando en la copia guardada si el
     * emisor no responde. Es el LIMITE de la desconfianza: pasado este plazo se rechaza todo. Subirlo
     * da mas disponibilidad ante una caida larga pero deja mas tiempo una llave retirada por compromiso.
     */
    private Duration outageTolerance = Duration.ofHours(1);

    /** Minimo entre dos descargas provocadas por un kid desconocido (cero = sin limite, solo para tests). */
    private Duration minRefetchInterval = Duration.ofSeconds(5);

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration readTimeout = Duration.ofSeconds(2);

    public void validar() {
        if (outageTolerance.compareTo(cacheTtl) < 0) {
            throw new IllegalStateException("app.security.jwks.outage-tolerance (" + outageTolerance
                    + ") no puede ser menor que cache-ttl (" + cacheTtl + ")");
        }
    }
}
