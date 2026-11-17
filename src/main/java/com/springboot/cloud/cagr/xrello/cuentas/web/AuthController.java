package com.springboot.cloud.cagr.xrello.cuentas.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.security.AuthService;
import com.springboot.cloud.cagr.xrello.cuentas.security.RegistroResultado;
import com.springboot.cloud.cagr.xrello.cuentas.security.RegistroService;
import com.springboot.cloud.cagr.xrello.cuentas.security.TokenPair;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.ConfirmarReseteoClaveRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.LoginRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RefreshRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.SolicitarReseteoClaveRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.TokenResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.UsuarioResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.VerificarCorreoRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// TRANSITORIO: migra al Authorization Server — registro/login/refresh locales desaparecen de
// aqui (pasan a ser flujos del Authorization Server: authorization code, etc.); ver
// XRELLO_CONTRATO.md, seccion "MIGRACION A OAUTH2".
/**
 * Rutas PUBLICAS de autenticacion — {@code permitAll()} en
 * {@code security.SecurityConfig}. Sin {@code @PreAuthorize}: ninguna recibe
 * un identificador de proyecto.
 */
@RestController
@RequestMapping("/api/v1/publico/auth")
@Tag(name = "Autenticacion", description = "Registro, inicio de sesion y renovacion de tokens.")
public class AuthController {

    private final RegistroService registroService;
    private final AuthService authService;

    public AuthController(RegistroService registroService, AuthService authService) {
        this.registroService = registroService;
        this.authService = authService;
    }

    @PostMapping("/registro")
    @Operation(summary = "Crea una cuenta nueva SIN clave (queda inactiva hasta verificar el correo, "
            + "ver /verificar-correo); si se manda un token de invitacion valido, el usuario queda "
            + "dentro del proyecto al terminar — una invitacion invalida NUNCA aborta el registro")
    public ResponseEntity<ApiResponse<RegistroResponse>> registro(@Valid @RequestBody RegistroRequest request) {
        RegistroResultado resultado = registroService.registrar(
                request.email(), request.nombre(), request.tokenInvitacion());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(aRegistroResponse(resultado)));
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica con email y password; devuelve access token (30 min) y refresh token (60 dias)")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenPair tokens = authService.login(request.email(), request.password());
        return ResponseEntity.ok(ApiResponse.success(aTokenResponse(tokens)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rota el refresh token (de un solo uso) y emite un par de tokens nuevo; "
            + "reutilizar un refresh token ya usado revoca todas las sesiones del usuario")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenPair tokens = authService.refrescar(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(aTokenResponse(tokens)));
    }

    @PostMapping("/verificar-correo")
    @Operation(summary = "Fija la PRIMERA clave real de la cuenta y la activa usando el token del "
            + "enlace de verificacion (404 token invalido, 410 vencido o ya usado); si todo sale "
            + "bien, deja la sesion iniciada (access token (30 min) y refresh token (60 dias))")
    public ResponseEntity<ApiResponse<TokenResponse>> verificarCorreo(@Valid @RequestBody VerificarCorreoRequest request) {
        TokenPair tokens = authService.verificarCorreo(request.token(), request.nuevaClave());
        return ResponseEntity.ok(ApiResponse.success(aTokenResponse(tokens)));
    }

    @PostMapping("/solicitar-reseteo-clave")
    @Operation(summary = "Envia el correo de reseteo de clave si existe una cuenta con ese correo; "
            + "SIEMPRE responde 200, exista o no, para no revelar que correos tienen cuenta")
    public ResponseEntity<ApiResponse<Void>> solicitarReseteoClave(@Valid @RequestBody SolicitarReseteoClaveRequest request) {
        authService.solicitarReseteoClave(request.correo());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/confirmar-reseteo-clave")
    @Operation(summary = "Fija la clave nueva de la cuenta usando el token del enlace de reseteo "
            + "(404 token invalido, 410 vencido o ya usado); si todo sale bien, deja la sesion "
            + "iniciada (access token (30 min) y refresh token (60 dias))")
    public ResponseEntity<ApiResponse<TokenResponse>> confirmarReseteoClave(@Valid @RequestBody ConfirmarReseteoClaveRequest request) {
        TokenPair tokens = authService.confirmarReseteoClave(request.token(), request.nuevaClave());
        return ResponseEntity.ok(ApiResponse.success(aTokenResponse(tokens)));
    }

    private RegistroResponse aRegistroResponse(RegistroResultado resultado) {
        Usuario usuario = resultado.usuario();
        return new RegistroResponse(
                new UsuarioResponse(usuario.getEmail(), usuario.getNombre()),
                resultado.invitacionAplicada(),
                resultado.motivoInvitacionNoAplicada());
    }

    private TokenResponse aTokenResponse(TokenPair tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
