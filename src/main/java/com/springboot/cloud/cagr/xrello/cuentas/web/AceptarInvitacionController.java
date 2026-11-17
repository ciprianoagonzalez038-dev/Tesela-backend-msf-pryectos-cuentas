package com.springboot.cloud.cagr.xrello.cuentas.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.domain.MiembroProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.invitacion.InvitacionService;
import com.springboot.cloud.cagr.xrello.cuentas.security.UsuarioActual;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.AceptarInvitacionRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.AceptarInvitacionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Acepta una invitacion pendiente — requiere estar autenticado, pero NO lleva
 * {@code @PreAuthorize}: no recibe un identificador de proyecto (el proyecto se resuelve
 * adentro del servicio, a partir del token), asi que no aplica la regla de
 * XRELLO_CONTRATO.md sobre endpoints con identificador de proyecto.
 */
@RestController
@RequestMapping("/api/v1/cuentas/invitaciones")
@Tag(name = "Invitaciones", description = "Aceptar una invitacion pendiente.")
public class AceptarInvitacionController {

    private final InvitacionService invitacionService;
    private final UsuarioActual usuarioActual;

    public AceptarInvitacionController(InvitacionService invitacionService, UsuarioActual usuarioActual) {
        this.invitacionService = invitacionService;
        this.usuarioActual = usuarioActual;
    }

    @PostMapping("/aceptar")
    @Operation(summary = "Acepta una invitacion y une al usuario autenticado al proyecto")
    public ResponseEntity<ApiResponse<AceptarInvitacionResponse>> aceptar(@Valid @RequestBody AceptarInvitacionRequest request) {
        MiembroProyecto miembro = invitacionService.aceptarInvitacion(
                request.token(), usuarioActual.getId(), usuarioActual.isAprovisionadoEnEstaPeticion());
        return ResponseEntity.ok(ApiResponse.success(new AceptarInvitacionResponse(
                miembro.getProyecto().getCodigo(),
                miembro.getProyecto().getNombre(),
                miembro.getRolProyecto().getCodigo())));
    }
}
