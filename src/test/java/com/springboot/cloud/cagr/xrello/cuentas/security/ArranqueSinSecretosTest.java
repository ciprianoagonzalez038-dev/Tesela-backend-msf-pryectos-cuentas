package com.springboot.cloud.cagr.xrello.cuentas.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * CAMINO DE FALLO de los secretos: arranca el contexto REAL de propiedades (los {@code application*.properties}
 * de main, con el perfil que se usa de verdad) SIN la variable de entorno y comprueba que el arranque ABORTA
 * con un mensaje que nombra la variable. El bug que motiva esto ({@code "${VAR}"} enlazado como literal, el
 * servicio arrancando con un secreto publico) sobrevivio porque toda verificacion previa tenia las variables
 * definidas. Una proteccion que nunca se ha visto fallar no sirve.
 *
 * <p>El entorno de cada arranque NO incluye las variables del sistema ni las propiedades de la JVM: asi el
 * resultado no depende de que el desarrollador tenga (o no) las variables en su shell. Los valores "definidos"
 * se pasan como propiedades por defecto de la aplicacion.</p>
 */
@DisplayName("Arranque SIN las variables de entorno de los secretos (camino de fallo)")
class ArranqueSinSecretosTest {

    @Configuration
    @EnableConfigurationProperties
    @Import({JwtProperties.class})
    static class Minima {
    }

    /** Valores validos para todas las variables; cada test quita la suya. */
    private static Map<String, String> definidas() {
        Map<String, String> v = new HashMap<>();
        v.put("XRELLO_JWT_SECRET", "valor-de-prueba-de-al-menos-32-caracteres-xyz-0123456789");
        v.put("DB_PASSWORD", "x");
        v.put("DB_USERNAME", "u");
        v.put("USUARIO_INTERNAL_TOKEN", "valor-de-prueba-de-al-menos-32-caracteres-abc-9876543210");
        v.put("MENSAJERIA_API_KEY", "tsl_0a1b2c3d_valor-de-prueba-de-al-menos-32-caracteres-123456");
        return v;
    }

    private static Throwable arrancar(String perfil, Map<String, String> variables) {
        StandardEnvironment sinSistema = new StandardEnvironment() {
            @Override
            protected void customizePropertySources(MutablePropertySources propertySources) {
                // sin variables de entorno ni propiedades de la JVM
            }
        };
        SpringApplicationBuilder b = new SpringApplicationBuilder(Minima.class)
                .web(WebApplicationType.NONE).environment(sinSistema).properties(new HashMap<String, Object>(variables));
        if (perfil != null) {
            b.profiles(perfil);
        }
        try (var ctx = b.run()) {
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private static String mensajes(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append(" | ");
        }
        return sb.toString();
    }

    @Test
    @DisplayName("control: con TODAS las variables definidas el contexto arranca (el test de fallo no es trivial)")
    void controlConTodasDefinidas() {
        assertThat(arrancar("local", definidas())).isNull();
        assertThat(arrancar(null, definidas())).isNull();
    }

    @Test
    @DisplayName("sin XRELLO_JWT_SECRET (perfil local, el arranque NATIVO de los procesos 8007/8008) el servicio NO arranca")
    void sinSecretoJwtEnLocal() {
        Map<String, String> v = definidas();
        v.remove("XRELLO_JWT_SECRET");

        Throwable fallo = arrancar("local", v);

        assertThat(fallo).as("el arranque debe ABORTAR").isNotNull();
        assertThat(mensajes(fallo)).contains("XRELLO_JWT_SECRET");
    }

    @Test
    @DisplayName("sin XRELLO_JWT_SECRET y sin perfil el servicio NO arranca")
    void sinSecretoJwtSinPerfil() {
        Map<String, String> v = definidas();
        v.remove("XRELLO_JWT_SECRET");

        Throwable fallo = arrancar(null, v);

        assertThat(fallo).as("el arranque debe ABORTAR").isNotNull();
        assertThat(mensajes(fallo)).contains("XRELLO_JWT_SECRET");
    }

    @Test
    @DisplayName("sin XRELLO_JWT_SECRET (perfil docker) el servicio NO arranca")
    void sinSecretoJwtEnDocker() {
        Map<String, String> v = definidas();
        v.remove("XRELLO_JWT_SECRET");

        Throwable fallo = arrancar("docker", v);

        assertThat(fallo).as("el arranque debe ABORTAR").isNotNull();
        assertThat(mensajes(fallo)).contains("XRELLO_JWT_SECRET");
    }

    @Test
    @DisplayName("sin USUARIO_INTERNAL_TOKEN (D28/Tesela, perfil local) el servicio NO arranca")
    void sinTokenInternoDeUsuarioEnLocal() {
        Map<String, String> v = definidas();
        v.remove("USUARIO_INTERNAL_TOKEN");

        Throwable fallo = arrancar("local", v);

        assertThat(fallo).as("el arranque debe ABORTAR").isNotNull();
        assertThat(mensajes(fallo)).contains("USUARIO_INTERNAL_TOKEN");
    }

    @Test
    @DisplayName("sin MENSAJERIA_API_KEY (API key del cliente Proyectos en ms-mensajeria, perfil local) el servicio NO arranca")
    void sinApiKeyDeMensajeriaEnLocal() {
        Map<String, String> v = definidas();
        v.remove("MENSAJERIA_API_KEY");

        Throwable fallo = arrancar("local", v);

        assertThat(fallo).as("el arranque debe ABORTAR").isNotNull();
        assertThat(mensajes(fallo)).contains("MENSAJERIA_API_KEY");
    }

    @Test
    @DisplayName("sin MENSAJERIA_API_KEY (perfil docker) el servicio NO arranca")
    void sinApiKeyDeMensajeriaEnDocker() {
        Map<String, String> v = definidas();
        v.remove("MENSAJERIA_API_KEY");

        Throwable fallo = arrancar("docker", v);

        assertThat(fallo).as("el arranque debe ABORTAR").isNotNull();
        assertThat(mensajes(fallo)).contains("MENSAJERIA_API_KEY");
    }

    @Test
    @DisplayName("sin USUARIO_INTERNAL_TOKEN (D28/Tesela, perfil docker) el servicio NO arranca")
    void sinTokenInternoDeUsuarioEnDocker() {
        Map<String, String> v = definidas();
        v.remove("USUARIO_INTERNAL_TOKEN");

        Throwable fallo = arrancar("docker", v);

        assertThat(fallo).as("el arranque debe ABORTAR").isNotNull();
        assertThat(mensajes(fallo)).contains("USUARIO_INTERNAL_TOKEN");
    }

    @Test
    @DisplayName("sin DB_PASSWORD (perfil docker, sin default) el servicio NO arranca, no se conecta con la clave literal '${DB_PASSWORD}'")
    void sinClaveDeBaseDeDatosEnDocker() {
        Map<String, String> v = definidas();
        v.remove("DB_PASSWORD");

        Throwable fallo = arrancar("docker", v);

        assertThat(fallo).as("el arranque debe ABORTAR").isNotNull();
        assertThat(mensajes(fallo)).contains("DB_PASSWORD");
    }

    @Test
    @DisplayName("el valor que estuvo versionado como default se rechaza aunque mida mas de 32 bytes")
    void secretoPublicoAntiguo() {
        Map<String, String> v = definidas();
        v.put("XRELLO_JWT_SECRET", "xrello-jwt-secreto-de-desarrollo-local-2026-no-usar-en-produccion");

        Throwable fallo = arrancar("local", v);

        assertThat(fallo).isNotNull();
        assertThat(mensajes(fallo)).contains("versionado");
    }

}
