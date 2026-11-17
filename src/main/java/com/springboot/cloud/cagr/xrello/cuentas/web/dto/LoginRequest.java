package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El email es obligatorio") String email,
        @NotBlank(message = "El password es obligatorio") String password) {
}
