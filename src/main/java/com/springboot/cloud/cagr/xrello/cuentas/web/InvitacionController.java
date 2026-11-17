package com.springboot.cloud.cagr.xrello.cuentas.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Invitacion;
import com.springboot.cloud.cagr.xrello.cuentas.invitacion.InvitacionCreada;
import com.springboot.cloud.cagr.xrello.cuentas.invitacion.InvitacionService;
import com.springboot.cloud.cagr.xrello.cuentas.security.UsuarioActual;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearInvitacionRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.InvitacionCreadaResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.InvitacionResumenResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Invitaciones de un proyecto — solo OWNER/ADMIN (crear, listar, revocar). {@code codigo} (path,
 * del proyecto contenedor) participa en TODO {@code @PreAuthorize} de esta clase.
 * {@code codigoInvitacion} en {@code DELETE} es el {@code codigo} UUID de la invitacion (tiene
 * uno propio, igual que el proyecto) — nunca su id numerico.
 */
@RestController
@RequestMapping("/api/v1/cuentas/proyectos/{codigo}/invitaciones")
@Tag(name = "Invitaciones de proyecto", description = "Crear, listar y revocar invitaciones — solo OWNER/ADMIN.")
public class InvitacionController {

    private final InvitacionService invitacionService;
    private final UsuarioActual usuarioActual;

    public InvitacionController(InvitacionService invitacionService, UsuarioActual usuarioActual) {
        this.invitacionService = invitacionService;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping
    @PreAuthorize("@accesoProyecto.esOwner(#codigo) or @accesoProyecto.tieneRol(#codigo, 'ADMIN')")
    @Operation(summary = "Lista las invitaciones del proyecto — solo OWNER/ADMIN; nunca expone el token")
    public ResponseEntity<ApiResponse<List<InvitacionResumenResponse>>> listar(@PathVariable @P("codigo") String codigo) {
        List<Invitacion> invitaciones = invitacionService.listarInvitaciones(codigo);
        return ResponseEntity.ok(ApiResponse.success(invitaciones.stream().map(this::aResumenResponse).toList()));
    }

    @PostMapping
    @PreAuthorize("@accesoProyecto.esOwner(#codigo) or @accesoProyecto.tieneRol(#codigo, 'ADMIN')")
    @Operation(summary = "Crea una invitacion — solo OWNER/ADMIN; el token en claro aparece "
            + "SOLO en esta respuesta, nunca mas")
    public ResponseEntity<ApiResponse<InvitacionCreadaResponse>> crear(
            @PathVariable @P("codigo") String codigo, @Valid @RequestBody CrearInvitacionRequest request) {
        InvitacionCreada creada = invitacionService.crearInvitacion(
                codigo, request.tipo(), request.emailDestino(), request.rolCodigo(),
                request.diasVigencia(), request.usosMaximos(), usuarioActual.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(aCreadaResponse(creada)));
    }

    @DeleteMapping("/{codigoInvitacion}")
    @PreAuthorize("@accesoProyecto.esOwner(#codigo) or @accesoProyecto.tieneRol(#codigo, 'ADMIN')")
    @Operation(summary = "Revoca una invitacion — solo OWNER/ADMIN")
    public ResponseEntity<Void> revocar(
            @PathVariable @P("codigo") String codigo, @PathVariable String codigoInvitacion) {
        invitacionService.revocarInvitacion(codigo, codigoInvitacion);
        return ResponseEntity.noContent().build();
    }

    private InvitacionResumenResponse aResumenResponse(Invitacion invitacion) {
        return new InvitacionResumenResponse(
                invitacion.getCodigo(), invitacion.getTipo(), invitacion.getEmailDestino(),
                invitacion.getRolAsignado().getCodigo(), invitacion.getEstado(),
                invitacion.getUsosMaximos(), invitacion.getUsosActuales(), invitacion.getFechaExpiracion());
    }

    private InvitacionCreadaResponse aCreadaResponse(InvitacionCreada creada) {
        Invitacion invitacion = creada.invitacion();
        return new InvitacionCreadaResponse(
                invitacion.getCodigo(), creada.tokenEnClaro(), invitacion.getTipo(),
                invitacion.getRolAsignado().getCodigo(), invitacion.getFechaExpiracion());
    }
}
