package com.springboot.cloud.cagr.xrello.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoService;
import com.springboot.cloud.cagr.xrello.cuentas.security.RefreshTokenRevocacionService;

/**
 * Arranque REAL del contexto completo contra MySQL vía Testcontainers — SIN ningún
 * {@code @MockitoBean} (ver XRELLO_CONTRATO.md §18 y la tarea que reemplazó al viejo
 * {@code MsXrelloCuentasApplicationTests}, que acumulaba un {@code @MockitoBean} por cada
 * repositorio nuevo que cualquier {@code @Service} declarara — 10 y subiendo).
 *
 * <p>Reemplaza esa cobertura por dos pruebas complementarias, ninguna con mocks:</p>
 * <ul>
 *   <li>ESTA clase: el contexto de la aplicación completa (JPA real, Flyway real, seguridad real,
 *       todos los {@code @Service}/repositorios reales) arranca sin excepción.</li>
 *   <li>{@link ConfiguracionSinBaseDeDatosTest}: una porción MINÚSCULA del contexto (solo las
 *       clases de configuración que genuinamente no necesitan base de datos).</li>
 * </ul>
 *
 * <p>Único cambio autorizado en ms-xrello-cuentas para esta tarea — ver XRELLO_CONTRATO.md §18.</p>
 */
@DisplayName("Arranque completo del contexto (Testcontainers MySQL, sin mocks)")
class ArranqueCompletoTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProyectoService proyectoService;

    @Autowired
    private RefreshTokenRevocacionService refreshTokenRevocacionService;

    @Test
    @DisplayName("El contexto completo arranca con todos los beans reales (JPA, Flyway, seguridad, servicios) sin ningun mock")
    void elContextoArrancaCompletoSinMocks() {
        assertThat(applicationContext).isNotNull();
        assertThat(proyectoService).isNotNull();
        assertThat(refreshTokenRevocacionService).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isGreaterThan(0);
    }
}
