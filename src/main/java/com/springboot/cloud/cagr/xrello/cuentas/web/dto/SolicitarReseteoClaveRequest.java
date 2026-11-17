package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** El endpoint SIEMPRE responde 200 (ver {@code AuthController#solicitarReseteoClave}). */
public record SolicitarReseteoClaveRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Escribe un correo valido") String correo) {
}
