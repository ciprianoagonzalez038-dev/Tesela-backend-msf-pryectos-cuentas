package com.springboot.cloud.cagr.xrello.cuentas.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import com.springboot.cloud.cagr.xrello.cuentas.domain.MiembroProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.RolProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.exception.RecursoNoEncontradoException;
import com.springboot.cloud.cagr.xrello.cuentas.repository.MiembroProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.ProyectoRepository;

/**
 * Guardia de acceso por proyecto, usable desde {@code @PreAuthorize} (ver
 * XRELLO_CONTRATO.md y {@code security.SecurityConfig}, {@code @EnableMethodSecurity}):
 *
 * <pre>{@code
 * @PreAuthorize("@accesoProyecto.esMiembro(#codigo)")
 * }</pre>
 *
 * <p>Los metodos reciben el {@code codigo} UUID del proyecto (nunca el id
 * numerico interno — los controllers tampoco lo conocen, ver
 * XRELLO_CONTRATO.md §5) y resuelven la membresia del {@link UsuarioActual}
 * contra {@code cta_miembro_proyecto} con una sola consulta por
 * {@code codigo} (join implicito via {@code Proyecto.codigo}).</p>
 *
 * <p><b>{@code @RequestScope}, no singleton</b>: "el resultado se cachea por
 * request, no mas" — un bean {@code @RequestScope} crea una instancia nueva
 * (con su propio mapa de cache, vacio) por cada peticion HTTP y la descarta al
 * terminarla; no hace falta invalidar nada a mano ni arriesgar servir una
 * membresia vieja en la siguiente peticion. Funciona sin registrar ningun
 * {@code RequestContextFilter} propio: el scope de request de Spring MVC ya
 * esta activo durante la evaluacion de {@code @PreAuthorize} (corre dentro
 * del mismo hilo/peticion que {@code DispatcherServlet}).</p>
 */
@Component("accesoProyecto")
@RequestScope
public class AccesoProyecto {

    private final MiembroProyectoRepository miembroProyectoRepository;
    private final ProyectoRepository proyectoRepository;
    private final UsuarioActual usuarioActual;
    private final Map<String, Optional<MiembroProyecto>> cache = new HashMap<>();

    public AccesoProyecto(MiembroProyectoRepository miembroProyectoRepository, ProyectoRepository proyectoRepository,
            UsuarioActual usuarioActual) {
        this.miembroProyectoRepository = miembroProyectoRepository;
        this.proyectoRepository = proyectoRepository;
        this.usuarioActual = usuarioActual;
    }

    public boolean esMiembro(String codigoProyecto) {
        return membresia(codigoProyecto).isPresent();
    }

    public boolean tieneRol(String codigoProyecto, String rolCodigo) {
        return membresia(codigoProyecto)
                .map(m -> m.getRolProyecto().getCodigo().equals(rolCodigo))
                .orElse(false);
    }

    public boolean esOwner(String codigoProyecto) {
        return tieneRol(codigoProyecto, RolProyecto.ROL_OWNER);
    }

    /**
     * 404 antes que 403: si el código no corresponde a NINGÚN proyecto se lanza {@link RecursoNoEncontradoException}
     * (se propaga desde la expresión de {@code @PreAuthorize} y el handler la mapea a 404); un proyecto que existe
     * pero no es accesible sigue dando 403. Mismo criterio que ms-xrello-tableros (auditoría visual 2026-11-08).
     */
    private Optional<MiembroProyecto> membresia(String codigoProyecto) {
        Optional<MiembroProyecto> m = cache.computeIfAbsent(codigoProyecto,
                codigo -> miembroProyectoRepository.findByProyecto_CodigoAndUsuario_Id(codigo, usuarioActual.getId()));
        if (m.isEmpty() && proyectoRepository.findByCodigo(codigoProyecto).isEmpty()) {
            throw new RecursoNoEncontradoException("No existe el proyecto " + codigoProyecto);
        }
        return m;
    }
}
