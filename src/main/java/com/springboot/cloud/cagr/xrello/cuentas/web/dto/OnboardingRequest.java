package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OnboardingRequest(
        @NotNull(message = "La versión es obligatoria") @Min(value = 0, message = "La versión no puede ser negativa") Integer version) {
}
