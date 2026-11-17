package com.springboot.cloud.cagr.xrello.cuentas.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.security.UsuarioActual;
import com.springboot.cloud.cagr.xrello.cuentas.usuario.OnboardingService;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.OnboardingRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.OnboardingResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Recorrido de primera vez del usuario autenticado. Opera siempre sobre «mi» cuenta: no recibe ids ni códigos. */
@RestController
@RequestMapping("/api/v1/cuentas/usuarios/me/onboarding")
@Tag(name = "Onboarding", description = "Última versión del recorrido de Tesela que el usuario vio u omitió.")
public class UsuarioOnboardingController {

    private final OnboardingService onboardingService;
    private final UsuarioActual usuarioActual;

    public UsuarioOnboardingController(OnboardingService onboardingService, UsuarioActual usuarioActual) {
        this.onboardingService = onboardingService;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping
    @Operation(summary = "Versión del recorrido que el usuario ya vio (null = nunca) — sin identificador en la petición, no requiere @PreAuthorize")
    public ResponseEntity<ApiResponse<OnboardingResponse>> obtener() {
        return ResponseEntity.ok(ApiResponse.success(new OnboardingResponse(onboardingService.versionVista(usuarioActual.getId()))));
    }

    @PutMapping
    @Operation(summary = "Marca como vista (u omitida) una versión del recorrido; nunca baja la ya registrada")
    public ResponseEntity<ApiResponse<OnboardingResponse>> marcar(@Valid @RequestBody OnboardingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(new OnboardingResponse(
                onboardingService.marcarVista(usuarioActual.getId(), request.version()))));
    }
}
