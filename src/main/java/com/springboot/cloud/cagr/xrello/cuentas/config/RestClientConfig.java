package com.springboot.cloud.cagr.xrello.cuentas.config;

import java.time.Duration;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia otros microservicios, resuelto por nombre lógico vía Eureka
 * ({@code http://ms-usuario}). Timeouts explícitos: sin ellos una respuesta lenta quedaría colgada
 * y el circuit breaker nunca la vería como fallo. Mismo patrón que ms3-oauth/RestClientConfig.
 *
 * <p>readTimeout en 8s (antes 4s, visto en {@link ResilienciaConfig}): {@code crear}/
 * {@code verificarCorreo} (alta con verificación de correo) pueden tardar más que el login
 * (~0,8s) que dimensionó el valor original — {@code AutenticacionServiceImpl.crear} de ms-usuario
 * manda el correo de verificación de forma síncrona. Confirmado en vivo (2026-11-11): la llamada
 * se cortaba aquí (o en el read-timeout de app-xrello-web hacia este servicio) mientras ms-usuario
 * sí completaba la operación.</p>
 */
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(Duration.ofSeconds(2))
                        .withReadTimeout(Duration.ofSeconds(8)));
        return RestClient.builder().requestFactory(requestFactory);
    }
}
