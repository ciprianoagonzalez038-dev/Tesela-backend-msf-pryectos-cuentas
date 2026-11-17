package com.springboot.cloud.cagr.xrello.cuentas.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Red de seguridad GENERICA contra el literal {@code "${VAR}"}: Spring Boot NO falla cuando enlaza un
 * {@code ${VAR}} sin resolver a un {@code @ConfigurationProperties} (o a {@code spring.datasource.*}); deja el
 * texto literal {@code "${VAR}"} como valor, es decir, un secreto PUBLICO y predecible, y el servicio arranca
 * sin avisar. Quitar el valor por defecto NO basta: hay que validar.
 *
 * <p>Antes de refrescar el contexto (cuando ya estan todas las fuentes, incluidas las de los tests) revisa las
 * propiedades de nombre sensible ({@code password}, {@code secret}, {@code token}, {@code private-key},
 * {@code credential}, {@code spring.datasource.username}) y, si alguna referencia una variable sin definir,
 * aborta el arranque nombrando la propiedad y la VARIABLE (nunca valores). Usa
 * {@code Environment.getProperty}, que SI es estricto con los placeholders (el mismo motivo por el que
 * {@code @Value} falla): se evalua el valor EFECTIVO, asi que una propiedad sobreescrita por otra fuente no da
 * falsos positivos. Las fuentes del sistema (variables de entorno, propiedades de la JVM) no se recorren.</p>
 *
 * <p>Es una red generica, no sustituye la validacion SEMANTICA de cada secreto (longitud minima, valores
 * antiguos ya publicos): esa vive en la clase de propiedades dueña del secreto. Registrada en
 * {@code META-INF/spring.factories}. Copia identica en ms3-oauth, ms-usuario, ms-xrello-cuentas y
 * ms-xrello-tableros: candidata a vivir una sola vez en ms-commons / la libreria de plataforma.</p>
 */
public class SecretosSinResolverListener implements ApplicationListener<ApplicationPreparedEvent> {

    private static final Pattern SENSIBLE = Pattern.compile(
            "(?i)(password|passwd|secret|token|private-?key|api-?key|credential|spring\\.datasource\\.username)");
    private static final Pattern VARIABLE = Pattern.compile("placeholder '([^']+)'");

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        revisar(event.getApplicationContext().getEnvironment());
    }

    static void revisar(ConfigurableEnvironment env) {
        Set<String> claves = new TreeSet<>();
        for (PropertySource<?> fuente : env.getPropertySources()) {
            if (fuente instanceof EnumerablePropertySource<?> enumerable && !delSistema(fuente)) {
                for (String nombre : enumerable.getPropertyNames()) {
                    if (SENSIBLE.matcher(nombre).find()) {
                        claves.add(nombre);
                    }
                }
            }
        }
        List<String> problemas = new ArrayList<>();
        for (String clave : claves) {
            try {
                env.getProperty(clave);
            } catch (IllegalArgumentException ex) {
                Matcher m = VARIABLE.matcher(String.valueOf(ex.getMessage()));
                problemas.add(clave + " -> variable de entorno sin definir: " + (m.find() ? m.group(1) : "?"));
            }
        }
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Propiedades secretas que referencian variables SIN DEFINIR (Spring Boot "
                    + "las enlazaria como el texto literal '${VAR}', un secreto publico): " + problemas
                    + ". Define las variables (copia .env.example a .env) antes de arrancar.");
        }
    }

    private static boolean delSistema(PropertySource<?> fuente) {
        return StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME.equals(fuente.getName())
                || StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME.equals(fuente.getName());
    }
}
