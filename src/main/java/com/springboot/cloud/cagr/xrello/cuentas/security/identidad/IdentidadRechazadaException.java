package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

/**
 * El token es valido (firma, emisor, vigencia) pero NO se puede mapear a un usuario local
 * utilizable: sin correo, cuenta no activa, conflicto de vinculacion. Es una
 * {@link AuthenticationException} porque ocurre dentro del filtro de resource server; lleva su
 * propio codigo HTTP (403 en vez del 401 generico) y {@link IdentidadAuthenticationEntryPoint} lo
 * responde con el sobre {@code ApiResponse}.
 */
public class IdentidadRechazadaException extends AuthenticationException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    public IdentidadRechazadaException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
