package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

/** Pantalla previa al registro — publico, sin autenticar. Nunca lleva ids internos. */
public record InvitacionPreviewResponse(String nombreProyecto, String nombreOwner, String rolAsignado) {
}
