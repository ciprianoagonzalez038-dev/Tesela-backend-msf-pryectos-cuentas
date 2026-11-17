package com.springboot.cloud.cagr.xrello.cuentas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base de los tests de integracion contra MySQL real (ver
 * XRELLO_CONTRATO.md §11 — prohibido {@code @SpringBootTest} contra una base
 * de datos real compartida; Testcontainers levanta una instancia MySQL 8.0.45
 * efimera y descartable, nunca db_xrello local).
 *
 * <p>Flyway aplica {@code V1}/{@code V2} contra el contenedor al arrancar el
 * contexto — cada ejecucion de la suite valida de paso, en caliente, que las
 * migraciones y las entidades siguen sin discrepancias
 * ({@code spring.jpa.hibernate.ddl-auto=validate}, heredado de
 * {@code application.properties}).</p>
 *
 * <p><b>Patron "singleton container", a proposito SIN {@code @Testcontainers}
 * ni {@code @Container}:</b> con esas anotaciones, el contenedor estatico se
 * arranca y se DETIENE una vez por cada clase de test que extiende esta base
 * — al correr la suite completa en una sola JVM (varias clases), la primera
 * clase lo apaga en su {@code afterAll} y la segunda falla con
 * "Connection refused" (verificado en caliente). El arranque manual en un
 * bloque {@code static} evita que el ciclo de vida quede atado a una clase en
 * particular: el contenedor se crea UNA sola vez por JVM (se comparte entre
 * {@code LimiteServiceTest}/{@code ProyectoServiceTest}/etc.) y nunca se
 * detiene explicitamente — lo limpia Ryuk (el reaper de Testcontainers) al
 * terminar la JVM.</p>
 *
 * <p>{@code eureka.client.enabled=false} (perfil {@code test}, ver
 * {@code application-test.properties}): no hay Eureka corriendo en el entorno
 * de tests, y no hace falta — nada de lo que se prueba aqui depende de
 * service discovery.</p>
 *
 * <p>{@code webEnvironment = RANDOM_PORT}: los tests de autenticacion/autorizacion (registro,
 * login, refresh, {@code @PreAuthorize}) necesitan la cadena de filtros de Spring Security REAL
 * corriendo sobre HTTP — un {@code @WebMvcTest}/mock no la ejercita. {@link #restTemplate}
 * queda listo para golpear la app real en {@link #port}.</p>
 */
@SpringBootTest(classes = MsXrelloCuentasApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * {@code MsXrelloCuentasApplication} ya fija la JVM en UTC en SU PROPIO bloque {@code static}
     * — pero ESE bloque solo corre cuando Spring carga esa clase, y aquí eso pasa DESPUÉS de que
     * {@code MYSQL.start()} (más abajo) ya hizo la primera conexión JDBC real para sondear el
     * contenedor. Mismo bug encontrado en caliente en {@code ms-xrello-tableros} escribiendo
     * {@code sprint.SprintService#serieBurndown} (ver su {@code AbstractIntegrationTest}): en una
     * máquina cuyo timezone de SISTEMA tiene offset NEGATIVO, esa primera conexión hace que algo en
     * la cadena JDBC cachee el timezone de sistema ANTES de que la aplicación lo corrija — un
     * {@link java.time.LocalDate} escrito vía Hibernate queda en la base UN DÍA ANTES del valor
     * real. Replicado aquí preventivamente aunque HOY ningún campo {@code LocalDate} exista todavía
     * en el dominio de este servicio (verificado: ninguna entidad de {@code cta_*} mapea uno) — la
     * misma ventana de carrera existe estructuralmente en cuanto se agregue el primero, y es más
     * barato cerrarla ahora que redescubrirla.
     */
    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.45")
            .withDatabaseName("db_xrello_test")
            .withUsername("xrello_test")
            .withPassword("xrello_test")
            // Robustez en maquinas lentas / con otros contenedores compitiendo por disco: el datadir
            // en memoria (tmpfs) evita la inicializacion lenta de InnoDB y los timeouts amplios
            // evitan que Testcontainers abandone el arranque a los 120 s por defecto.
            .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw"))
            .withStartupTimeoutSeconds(600)
            .withConnectTimeoutSeconds(600)
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }
}
