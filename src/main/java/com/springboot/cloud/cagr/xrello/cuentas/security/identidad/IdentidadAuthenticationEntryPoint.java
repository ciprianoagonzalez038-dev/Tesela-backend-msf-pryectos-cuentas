package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.cloud.cagr.commons.dto.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responde las fallas de autenticacion del resource server: las de {@link IdentidadRechazadaException}
 * con su codigo propio (403) y el sobre {@code ApiResponse}; todas las demas (token ausente,
 * invalido, vencido, emisor desconocido) con el 401 estandar de Bearer ({@code WWW-Authenticate}).
 */
public class IdentidadAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper objectMapper;

    public IdentidadAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException, jakarta.servlet.ServletException {
        if (ex instanceof IdentidadRechazadaException rechazo) {
            response.setStatus(rechazo.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(rechazo.getStatus(), rechazo.getMessage()));
            return;
        }
        bearer.commence(request, response, ex);
        // Token con audiencia ajena o ausente: ademas del WWW-Authenticate estandar, el cuerpo dice POR QUE
        // (el resto de tokens invalidos sigue sin cuerpo, como antes).
        String mensaje = ex.getMessage();
        int i = mensaje == null ? -1 : mensaje.indexOf(AudienceValidator.PREFIJO_MENSAJE);
        if (i >= 0) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(),
                    ApiResponse.error(org.springframework.http.HttpStatus.UNAUTHORIZED, mensaje.substring(i)));
        }
    }
}
