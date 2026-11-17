package com.springboot.cloud.cagr.xrello.cuentas.invitacion;

/**
 * Vista publica de una invitacion, para la pantalla previa al registro
 * ({@code InvitacionService#consultarInvitacion}). NUNCA lleva ids ni datos
 * internos — solo lo que un invitado sin cuenta todavia puede ver.
 */
public record InvitacionPreviewView(String nombreProyecto, String nombreOwner, String rolAsignadoNombre) {
}
