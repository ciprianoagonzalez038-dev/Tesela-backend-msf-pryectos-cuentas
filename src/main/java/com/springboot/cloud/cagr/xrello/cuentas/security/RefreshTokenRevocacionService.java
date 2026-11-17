package com.springboot.cloud.cagr.xrello.cuentas.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.cloud.cagr.xrello.cuentas.repository.RefreshTokenRepository;

// TRANSITORIO: migra al Authorization Server — junto con el resto del paquete de refresh
// tokens; ver XRELLO_CONTRATO.md, seccion "MIGRACION A OAUTH2".
/**
 * Colaborador SEPARADO de {@link RefreshTokenService} solo para poder poner
 * {@code @Transactional(REQUIRES_NEW)} en un metodo que de verdad pasa por el proxy de Spring.
 *
 * <p><b>Por que no vive directamente en {@code RefreshTokenService.rotar()}</b>: Spring AOP
 * implementa {@code @Transactional} con un proxy alrededor del BEAN — una llamada
 * {@code this.metodo()} desde dentro de la misma clase NUNCA pasa por ese proxy, sale
 * directamente como una llamada Java normal. Si {@code revocarTodasLasSesiones} fuera un metodo
 * mas de {@code RefreshTokenService} y {@code rotar()} lo llamara como {@code this.revocar...()}
 * (autoinvocacion), su {@code @Transactional(REQUIRES_NEW)} seria completamente IGNORADO — el
 * UPDATE terminaria viviendo en la misma transaccion que {@code rotar()}, exactamente el bug que
 * esto existe para evitar (ver mas abajo). Inyectando este servicio COMO OTRO BEAN, la llamada
 * {@code revocacionService.revocarTodasLasSesiones(...)} SI atraviesa el proxy de
 * {@code RefreshTokenRevocacionService} y su propia transaccion independiente aplica de
 * verdad.</p>
 *
 * <p><b>Por que necesita su propia transaccion en primer lugar</b> (razon original, antes movida
 * a nivel de repositorio — ver {@code RefreshTokenRepository#revocarTodosDelUsuario}):
 * {@link RefreshTokenService#rotar} llama este metodo e INMEDIATAMENTE lanza
 * {@code ReusoRefreshTokenException} para rechazar la peticion — con la propagacion default
 * (REQUIRED), el UPDATE de revocacion viviria en la MISMA transaccion que esa excepcion esta a
 * punto de hacer fallar, y el rollback automatico (RuntimeException sin capturar saliendo de un
 * metodo {@code @Transactional}) DESHARIA tambien la revocacion masiva — el segundo refresh
 * token (el que la deteccion de reuso deberia haber invalidado) seguiria funcionando. Verificado
 * en caliente: sin {@code REQUIRES_NEW}, reusar un refresh token dejaba el SIGUIENTE token
 * emitido intacto y utilizable (200 OK en vez de 401).</p>
 */
@Service
public class RefreshTokenRevocacionService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevocacionService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revocarTodasLasSesiones(Long usuarioId) {
        refreshTokenRepository.revocarTodosDelUsuario(usuarioId);
    }
}
