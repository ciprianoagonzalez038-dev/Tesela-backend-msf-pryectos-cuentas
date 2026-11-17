package com.springboot.cloud.cagr.xrello.cuentas.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.invitacion.InvitacionPreviewView;
import com.springboot.cloud.cagr.xrello.cuentas.invitacion.InvitacionService;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.InvitacionPreviewResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Consulta PUBLICA de una invitacion — {@code permitAll()}, sin autenticar. Sin
 * {@code @PreAuthorize}: no recibe un identificador de proyecto (el token, no un codigo, es la
 * unica credencial de acceso a esta ruta).
 */
@RestController
@RequestMapping("/api/v1/publico/invitaciones")
@Tag(name = "Invitaciones (publico)", description = "Consulta previa de una invitacion, antes de registrarse.")
public class InvitacionPublicoController {

    private final InvitacionService invitacionService;

    public InvitacionPublicoController(InvitacionService invitacionService) {
        this.invitacionService = invitacionService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Datos de la invitacion para la pantalla previa al registro (410 si esta "
            + "expirada, revocada o agotada) — nunca expone ids internos")
    public ResponseEntity<ApiResponse<InvitacionPreviewResponse>> consultar(@PathVariable String token) {
        InvitacionPreviewView vista = invitacionService.consultarInvitacion(token);
        return ResponseEntity.ok(ApiResponse.success(
                new InvitacionPreviewResponse(vista.nombreProyecto(), vista.nombreOwner(), vista.rolAsignadoNombre())));
    }
}
