package com.springboot.cloud.cagr.xrello.cuentas.web.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;
import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoService;
import com.springboot.cloud.cagr.xrello.cuentas.web.admin.dto.ReconciliacionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * <p><b>RUTA ADMINISTRATIVA.</b> Recibe el {@code codigo} UUID del proyecto (nunca el id
 * numerico interno, ver XRELLO_CONTRATO.md §5 — el {@code {id}} de la ruta descrita en el
 * prompt es notacion generica, no un pedido literal de exponer el id numerico: la regla
 * "nunca el id numerico" aplica tambien aqui). Protegida por el rol de SISTEMA
 * {@code ROLE_SOPORTE} (ver {@code domain.RolSistema}, {@code security.SecurityConfig}) — NO
 * por {@code esOwner}: cualquier {@code /api/v1/admin/**} debe exigir un rol de sistema, nunca
 * solo la relacion del llamador con el proyecto puntual (un OWNER cualquiera no deberia poder
 * recalcular contadores de SU PROPIO proyecto a discrecion).</p>
 */
@RestController
@RequestMapping("/api/v1/admin/proyectos")
@Tag(name = "Administracion de proyectos", description = "Recalculo de contadores (rutas administrativas).")
public class AdminProyectoController {

    private final ProyectoService proyectoService;

    public AdminProyectoController(ProyectoService proyectoService) {
        this.proyectoService = proyectoService;
    }

    @PostMapping("/{codigo}/reconciliar-contador")
    @PreAuthorize("hasAuthority('ROLE_SOPORTE')")
    @Operation(summary = "Recalcula miembros_actuales contra el conteo real de cta_miembro_proyecto")
    public ResponseEntity<ApiResponse<ReconciliacionResponse>> reconciliar(@PathVariable @P("codigo") String codigo) {
        Proyecto proyecto = proyectoService.obtenerPorCodigo(codigo);
        proyectoService.reconciliarContadorMiembros(proyecto.getId());
        Proyecto actualizado = proyectoService.obtenerPorCodigo(codigo);
        return ResponseEntity.ok(ApiResponse.success(new ReconciliacionResponse(actualizado.getMiembrosActuales())));
    }
}
