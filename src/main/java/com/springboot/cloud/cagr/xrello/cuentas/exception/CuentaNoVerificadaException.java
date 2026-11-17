package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * Login contra una cuenta que ya existe en ms-usuario pero todavia no verifico su correo (403,
 * {@code AutenticacionServiceImpl#autenticar}) -- traducida desde ese 403 en
 * {@code UsuarioDirectorioClient#leer} (BUG-E2E-001: antes caia en el {@code IllegalStateException}
 * generico de ese metodo, envuelto como {@link DirectorioNoDisponibleException} -- un 503 "servicio
 * no disponible" en vez de avisar que falta verificar el correo). Mapeada a 403 por
 * {@link CuentasExceptionHandler}, con el mensaje que ya manda ms-usuario tal cual.
 */
public class CuentaNoVerificadaException extends CuentasException {

    public CuentaNoVerificadaException(String message) {
        super(message);
    }
}
