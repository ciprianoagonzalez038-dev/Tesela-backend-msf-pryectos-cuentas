package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

/** Nunca expone {@code id} ni {@code passwordHash}. */
public record UsuarioResponse(String email, String nombre) {
}
