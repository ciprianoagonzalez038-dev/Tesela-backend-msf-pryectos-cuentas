package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code nuevaClave} es la PRIMERA clave real de la cuenta — el registro ya no la pide (ver
 * {@code RegistroRequest}); se fija aquí, en el mismo paso que activa la cuenta.
 */
public record VerificarCorreoRequest(
        @NotBlank(message = "El token es obligatorio") String token,
        @NotBlank(message = "La clave es obligatoria")
        @Size(min = 8, message = "La clave debe tener al menos 8 caracteres") String nuevaClave) {
}
