package com.springboot.cloud.cagr.xrello.cuentas.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;
import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoConRolView;
import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoService;
import com.springboot.cloud.cagr.xrello.cuentas.security.UsuarioActual;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearProyectoRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.ProyectoResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.ProyectoResumenResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.TransferirPropiedadRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Proyectos del usuario autenticado. Recibe el {@code codigo} UUID del proyecto, nunca el id
 * numerico interno (ver XRELLO_CONTRATO.md §5) — {@code @PreAuthorize} en TODO endpoint que
 * reciba uno, sin excepciones (ver {@code security.AccesoProyecto}).
 */
@RestController
@RequestMapping("/api/v1/cuentas/proyectos")
@Tag(name = "Proyectos", description = "Proyectos/tableros Kanban del usuario autenticado.")
public class ProyectoController {

    private final ProyectoService proyectoService;
    private final UsuarioActual usuarioActual;

    public ProyectoController(ProyectoService proyectoService, UsuarioActual usuarioActual) {
        this.proyectoService = proyectoService;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping
    @Operation(summary = "Home: proyectos del usuario autenticado, con su rol en cada uno — "
            + "sin identificador de proyecto en la peticion, no requiere @PreAuthorize")
    public ResponseEntity<ApiResponse<List<ProyectoResumenResponse>>> home() {
        List<ProyectoConRolView> vistas = proyectoService.listarProyectosDeUsuario(usuarioActual.getId());
        return ResponseEntity.ok(ApiResponse.success(vistas.stream().map(this::aResumenResponse).toList()));
    }

    @PostMapping
    @Operation(summary = "Crea un proyecto nuevo; el usuario autenticado queda como owner — "
            + "no hay proyecto existente que verificar, no requiere @PreAuthorize")
    public ResponseEntity<ApiResponse<ProyectoResponse>> crear(@Valid @RequestBody CrearProyectoRequest request) {
        Proyecto proyecto = proyectoService.crearProyecto(
                usuarioActual.getId(), request.nombre(), request.descripcion(), request.prefijo(), request.plantillaCodigo());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(aProyectoResponse(proyecto)));
    }

    @GetMapping("/{codigo}")
    @PreAuthorize("@accesoProyecto.esMiembro(#codigo)")
    @Operation(summary = "Detalle de un proyecto — solo miembros")
    public ResponseEntity<ApiResponse<ProyectoResponse>> obtener(@PathVariable @P("codigo") String codigo) {
        return ResponseEntity.ok(ApiResponse.success(aProyectoResponse(proyectoService.obtenerPorCodigo(codigo))));
    }

    @PatchMapping("/{codigo}/archivar")
    @PreAuthorize("@accesoProyecto.esOwner(#codigo)")
    @Operation(summary = "Archiva el proyecto — solo el owner; deja de consumir cupo de inmediato")
    public ResponseEntity<ApiResponse<Void>> archivar(@PathVariable @P("codigo") String codigo) {
        proyectoService.archivarProyectoPorCodigo(codigo);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{codigo}/transferir-propiedad")
    @PreAuthorize("@accesoProyecto.esOwner(#codigo)")
    @Operation(summary = "Transfiere la propiedad a otro miembro (identificado por email) — solo "
            + "el owner actual; exige que el destinatario ya sea miembro y tenga cupo disponible")
    public ResponseEntity<ApiResponse<Void>> transferirPropiedad(
            @PathVariable @P("codigo") String codigo, @Valid @RequestBody TransferirPropiedadRequest request) {
        proyectoService.transferirPropiedadPorCodigo(codigo, request.emailNuevoOwner());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private ProyectoResponse aProyectoResponse(Proyecto proyecto) {
        return new ProyectoResponse(
                proyecto.getCodigo(), proyecto.getNombre(), proyecto.getDescripcion(),
                proyecto.getPrefijo(), proyecto.getEstado(), proyecto.getMiembrosActuales());
    }

    private ProyectoResumenResponse aResumenResponse(ProyectoConRolView vista) {
        Proyecto proyecto = vista.proyecto();
        return new ProyectoResumenResponse(proyecto.getCodigo(), proyecto.getNombre(), vista.rolCodigo(), vista.esOwner());
    }
}
