package com.springboot.cloud.cagr.xrello.cuentas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas de arquitectura automatizadas (ver XRELLO_CONTRATO.md §18, "Identificador protegido vs
 * identificador mutado" y la regla "ningún controller accede a `repository` directamente") —
 * ambos bugs reales que motivaron estas reglas se encontraron en ms-xrello-tableros, pero el
 * riesgo estructural es el mismo aquí: estos tests existen para que NINGÚN controller nuevo
 * reintroduzca ese patrón sin que algo falle de inmediato.
 */
class ArquitecturaTest {

    private static final String PAQUETE_BASE = "com.springboot.cloud.cagr.xrello.cuentas";

    /**
     * El {@code sub} del Authorization Server es el id numerico del usuario en ms-usuario: parsea
     * sin error como Long y coincidiria con el id de OTRA persona en {@code cta_usuario}. La unica
     * defensa que no depende de la disciplina de cada desarrollador es que NINGUNA clase fuera de
     * {@code security.identidad} pueda leer claims de un {@code Jwt}: asi no existe ningun camino
     * por el que un claim llegue a convertirse en id local.
     */
    @Test
    @DisplayName("Solo security.identidad lee claims de un Jwt (ningun claim se convierte en id local)")
    void soloIdentidadLeeClaimsDeUnJwt() {
        reglaClaims().check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(PAQUETE_BASE));
    }

    @Test
    @DisplayName("La regla de claims detecta de verdad una violacion (no pasa en vacio)")
    void laReglaDeClaimsDetectaUnaViolacion() {
        JavaClasses conViolacion = new ClassFileImporter().importClasses(ViolacionDeEjemplo.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reglaClaims().check(conViolacion))
                .isInstanceOf(AssertionError.class);
    }

    private static ArchRule reglaClaims() {
        return noClasses()
                .that().resideOutsideOfPackage(PAQUETE_BASE + ".security.identidad..")
                .should().callMethodWhere(
                        com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                        com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner(
                                                com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo(
                                                        org.springframework.security.oauth2.jwt.Jwt.class)))
                                .and(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                        com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching(
                                                "getSubject|getClaim.*|getClaims"))))
                .because("un claim del token nunca debe poder convertirse en id local; la identidad se "
                        + "resuelve solo en security.identidad (subject_externo)");
    }

    /** Clase fuera de {@code security.identidad} que lee un claim: debe hacer FALLAR la regla. */
    @SuppressWarnings("unused")
    static class ViolacionDeEjemplo {
        Long idLocalDesdeElToken(org.springframework.security.oauth2.jwt.Jwt jwt) {
            return Long.valueOf(jwt.getSubject());
        }
    }

    @Test
    @DisplayName("Ningun controller (web/web.admin) accede a `repository` directamente")
    void controllersNoAccedenARepository() {
        ArchRule regla = noClasses()
                .that().resideInAPackage(PAQUETE_BASE + ".web..")
                .should().dependOnClassesThat().resideInAPackage(PAQUETE_BASE + ".repository..")
                .because("toda carga de recursos debe pasar por un servicio que verifique membresía "
                        + "(ver XRELLO_CONTRATO.md §18) — un controller con acceso directo a "
                        + "`repository` puede saltarse esa verificación sin que nada lo impida");

        regla.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(PAQUETE_BASE));
    }

    /**
     * Regla LITERAL, sin excepciones: en este servicio, TODO {@code @PathVariable} cuyo nombre
     * empieza por {@code codigo} pertenece a un endpoint protegido por
     * {@code @accesoProyecto.esMiembro/esOwner/tieneRol} — nunca hay un caso legítimo de
     * verificación solo-en-servicio para un identificador "codigo*" en este proyecto (a
     * diferencia de ms-xrello-tableros, ver su {@code ArquitecturaTest} para el contraste y por
     * qué allá la regla necesita una lista de excepciones explícita). Confirmado por auditoría
     * manual (ver XRELLO_CONTRATO.md §18) antes de escribir este test: hoy pasa sin ninguna
     * excepción — si alguna vez deja de pasar, es porque se agregó un endpoint nuevo sin
     * {@code @PreAuthorize}, exactamente el descuido que este test existe para atrapar.
     */
    @Test
    @DisplayName("Todo endpoint con un @PathVariable 'codigo*' lleva @PreAuthorize")
    void endpointsConPathVariableCodigoLlevanPreAuthorize() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> infractores = new ArrayList<>();
        for (var candidato : scanner.findCandidateComponents(PAQUETE_BASE)) {
            Class<?> controlador = Class.forName(candidato.getBeanClassName());
            for (Method metodo : controlador.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(metodo, RequestMapping.class)) {
                    continue;
                }
                if (tieneCodigoPathVariable(metodo) && !AnnotatedElementUtils.hasAnnotation(metodo, PreAuthorize.class)) {
                    infractores.add(controlador.getSimpleName() + "#" + metodo.getName());
                }
            }
        }

        assertThat(infractores)
                .as("Métodos con un @PathVariable 'codigo*' sin @PreAuthorize — ver XRELLO_CONTRATO.md §18/§19")
                .isEmpty();
    }

    private boolean tieneCodigoPathVariable(Method metodo) {
        for (Parameter parametro : metodo.getParameters()) {
            PathVariable anotacion = parametro.getAnnotation(PathVariable.class);
            if (anotacion == null) {
                continue;
            }
            String nombre = !anotacion.value().isBlank() ? anotacion.value()
                    : !anotacion.name().isBlank() ? anotacion.name()
                    : parametro.getName();
            if (nombre.startsWith("codigo")) {
                return true;
            }
        }
        return false;
    }
}
