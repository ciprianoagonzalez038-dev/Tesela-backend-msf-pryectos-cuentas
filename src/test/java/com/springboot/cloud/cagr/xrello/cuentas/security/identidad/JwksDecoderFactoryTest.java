package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;

/**
 * La fabrica de decoders frente a un emisor que se cae, se cuelga o rota sus llaves. Sin Spring ni Docker:
 * un servidor HTTP minimo hace de emisor. Los plazos son de milisegundos para que corra en segundos; la
 * relacion entre ellos (tolerancia > TTL de cache) es la misma que en produccion.
 */
@DisplayName("JwksDecoderFactory — cache de JWKS tolerante a caidas del emisor")
class JwksDecoderFactoryTest {

    private static final String ISSUER = "http://ms3-oauth.test";

    /** Emisor de mentira: sirve un JWKS que se puede cambiar, apagar o colgar. */
    private static final class Emisor {
        final List<RSAKey> llaves = new ArrayList<>();
        final AtomicInteger descargas = new AtomicInteger();
        final AtomicBoolean colgado = new AtomicBoolean();
        HttpServer servidor;
        int puerto;

        Emisor(RSAKey primera) throws Exception {
            llaves.add(primera);
            arrancar(0);
        }

        void arrancar(int puertoFijo) throws Exception {
            servidor = HttpServer.create(new InetSocketAddress("localhost", puertoFijo), 0);
            puerto = servidor.getAddress().getPort();
            servidor.createContext("/jwks", ex -> {
                descargas.incrementAndGet();
                if (colgado.get()) {
                    try {
                        Thread.sleep(6_000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                List<JWK> publicas = llaves.stream().map(RSAKey::toPublicJWK).map(k -> (JWK) k).toList();
                byte[] cuerpo = new JWKSet(publicas).toString().getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, cuerpo.length);
                ex.getResponseBody().write(cuerpo);
                ex.close();
            });
            servidor.start();
        }

        void apagar() {
            servidor.stop(0);
        }

        String uri() {
            return "http://localhost:" + puerto + "/jwks";
        }
    }

    private Emisor emisor;

    @AfterEach
    void cerrar() {
        if (emisor != null) {
            emisor.apagar();
        }
    }

    private static RSAKey nuevaLlave(String kid) throws Exception {
        return new RSAKeyGenerator(2048).keyID(kid).generate();
    }

    private static String token(RSAKey llave, String issuer) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("7")
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(600)).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(llave)))
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256).keyId(llave.getKeyID()).build(), claims))
                .getTokenValue();
    }

    private static JwksProperties props(long cacheMs, long toleranciaMs, long refetchMs, long readMs) {
        JwksProperties p = new JwksProperties();
        p.setCacheTtl(Duration.ofMillis(cacheMs));
        p.setOutageTolerance(Duration.ofMillis(toleranciaMs));
        p.setMinRefetchInterval(Duration.ofMillis(refetchMs));
        p.setReadTimeout(Duration.ofMillis(readMs));
        p.setConnectTimeout(Duration.ofMillis(500));
        p.setRefreshTimeout(Duration.ofMillis(500));
        return p;
    }

    private JwtDecoder decoder(JwksProperties p) {
        return JwksDecoderFactory.crear(emisor.uri(), ISSUER, p);
    }

    @Test
    @DisplayName("valida un token del emisor correcto")
    void validaToken() throws Exception {
        RSAKey k1 = nuevaLlave("k1");
        emisor = new Emisor(k1);

        assertThat(decoder(props(60_000, 3_600_000, 0, 500)).decode(token(k1, ISSUER)).getSubject()).isEqualTo("7");
    }

    @Test
    @DisplayName("rechaza un emisor (iss) distinto y una firma hecha con otra clave bajo el mismo kid")
    void rechazaEmisorYFirmaAjena() throws Exception {
        RSAKey k1 = nuevaLlave("k1");
        emisor = new Emisor(k1);
        JwtDecoder d = decoder(props(60_000, 3_600_000, 0, 500));

        assertThatThrownBy(() -> d.decode(token(k1, "http://otro-emisor.test"))).isInstanceOf(JwtException.class);
        RSAKey impostora = new RSAKeyGenerator(2048).keyID("k1").generate();
        assertThatThrownBy(() -> d.decode(token(impostora, ISSUER))).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("CAIDO el emisor y vencida la cache, SIGUE validando con la ultima copia buena (lo que el decoder por defecto no hace tras 300 s)")
    void validaConElEmisorCaidoTrasVencerLaCache() throws Exception {
        RSAKey k1 = nuevaLlave("k1");
        emisor = new Emisor(k1);
        JwtDecoder d = decoder(props(500, 3_600_000, 0, 500));
        assertThat(d.decode(token(k1, ISSUER))).isNotNull();

        emisor.apagar();
        Thread.sleep(1_200);   // mas del doble del TTL de cache

        assertThat(d.decode(token(k1, ISSUER)).getSubject()).isEqualTo("7");
    }

    @Test
    @DisplayName("la desconfianza esta ACOTADA: pasada la tolerancia sin descarga exitosa, rechaza todo")
    void rechazaPasadaLaTolerancia() throws Exception {
        RSAKey k1 = nuevaLlave("k1");
        emisor = new Emisor(k1);
        JwtDecoder d = decoder(props(300, 1_500, 0, 500));
        assertThat(d.decode(token(k1, ISSUER))).isNotNull();

        emisor.apagar();
        Thread.sleep(2_800);   // mas que la tolerancia, contada desde la ultima descarga

        assertThatThrownBy(() -> d.decode(token(k1, ISSUER))).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("adopta una llave rotada (kid nuevo) con el emisor arriba, sin reiniciar")
    void adoptaLlaveRotada() throws Exception {
        RSAKey k1 = nuevaLlave("k1");
        emisor = new Emisor(k1);
        JwtDecoder d = decoder(props(60_000, 3_600_000, 200, 500));
        assertThat(d.decode(token(k1, ISSUER))).isNotNull();

        RSAKey k2 = nuevaLlave("k2");
        emisor.llaves.add(k2);          // rotacion: se publica la nueva junto a la anterior
        Thread.sleep(400);              // supera el minimo entre descargas

        assertThat(d.decode(token(k2, ISSUER)).getSubject()).isEqualTo("7");
        assertThat(d.decode(token(k1, ISSUER)).getSubject()).isEqualTo("7");
    }

    @Test
    @DisplayName("con el emisor caido, un kid desconocido se RECHAZA (la tolerancia no inventa llaves)")
    void kidDesconocidoConEmisorCaido() throws Exception {
        RSAKey k1 = nuevaLlave("k1");
        emisor = new Emisor(k1);
        JwtDecoder d = decoder(props(500, 3_600_000, 0, 500));
        assertThat(d.decode(token(k1, ISSUER))).isNotNull();

        emisor.apagar();
        Thread.sleep(700);
        RSAKey desconocida = nuevaLlave("k-desconocida");

        assertThatThrownBy(() -> d.decode(token(desconocida, ISSUER))).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("un emisor COLGADO (no solo caido) no deja a las peticiones esperando: latencia acotada y sigue validando")
    void emisorColgadoLatenciaAcotada() throws Exception {
        RSAKey k1 = nuevaLlave("k1");
        emisor = new Emisor(k1);
        JwtDecoder d = decoder(props(300, 3_600_000, 0, 400));
        assertThat(d.decode(token(k1, ISSUER))).isNotNull();

        emisor.colgado.set(true);   // acepta la conexion pero no responde
        Thread.sleep(500);          // vence la cache: la proxima peticion intenta refrescar

        long inicio = System.nanoTime();
        assertThat(d.decode(token(k1, ISSUER)).getSubject()).isEqualTo("7");
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(ms).as("la peticion no espera los 6 s del emisor colgado: espera a lo sumo el timeout de lectura")
                .isLessThan(3_000);
    }

    @Test
    @DisplayName("la tolerancia no puede ser menor que el TTL de cache (configuracion incoherente falla el arranque)")
    void configuracionIncoherente() throws Exception {
        RSAKey k1 = nuevaLlave("k1");
        emisor = new Emisor(k1);

        assertThatThrownBy(() -> decoder(props(60_000, 1_000, 0, 500)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outage-tolerance");
    }
}
