package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AceptarInvitacionRequest(@NotBlank(message = "El token es obligatorio") String token) {
}
