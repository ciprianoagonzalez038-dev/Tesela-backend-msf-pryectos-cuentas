package com.springboot.cloud.cagr.xrello.cuentas.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

/**
 * Límite de tiempo del circuit breaker {@code usuarios} (login/alta contra ms-usuario). Sin esto aplica el valor por
 * defecto de Resilience4j (1 s), menor que el timeout de lectura del {@code RestClient} (8 s, ver
 * {@link RestClientConfig}): un login tarda ~0,8 s y con algo de cola superaba 1 s, así que respondía 500 de forma
 * intermitente (log: {@code TimeLimiter 'usuarios' recorded a timeout exception}). La fábrica de Spring Cloud busca
 * la configuración por el nombre del breaker EN EL REGISTRO de time limiters (un {@code Customizer} no la aplicaba:
 * el log seguía diciendo {@code timeoutDuration=PT1S}). Auditoría visual 2026-11-08.
 *
 * <p>Subido de 6s a 10s junto con el read timeout del {@code RestClient} (4s -> 8s, 2026-11-11):
 * {@code crear}/{@code verificarCorreo} (alta con verificación de correo) hacen más trabajo que el login que
 * dimensionó el valor original -- ver el Javadoc de {@link RestClientConfig}.</p>
 */
@Configuration
public class ResilienciaConfig {

    /** Mayor que el read timeout del RestClient (8 s) para que el corte lo decida el cliente HTTP y no este límite. */
    static final Duration TIMEOUT_USUARIOS = Duration.ofSeconds(10);

    @Bean
    TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.of(Map.of(
                "usuarios", TimeLimiterConfig.custom().timeoutDuration(TIMEOUT_USUARIOS).build()));
    }
}
