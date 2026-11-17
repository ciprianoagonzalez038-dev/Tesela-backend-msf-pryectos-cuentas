package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmarReseteoClaveRequest(
        @NotBlank(message = "El token es obligatorio") String token,
        @NotBlank(message = "La clave es obligatoria")
        @Size(min = 8, message = "La clave debe tener al menos 8 caracteres") String nuevaClave) {
}
