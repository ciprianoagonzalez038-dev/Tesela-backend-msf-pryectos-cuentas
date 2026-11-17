package com.springboot.cloud.cagr.xrello.cuentas.infrastructure.parametrizacion;

import com.springboot.cloud.cagr.commons.parametrizacion.ParametrizacionCompletoPort;
import com.springboot.cloud.cagr.commons.parametrizacion.ParametrizacionRemoteClient;
import com.springboot.cloud.cagr.commons.parametrizacion.ParametrizacionRemotoException;
import com.springboot.cloud.cagr.commons.parametrizacion.dto.ParametroRemotoDto;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adapter de infraestructura de {@link ParametrizacionCompletoPort} — delega el transporte al
 * {@link ParametrizacionRemoteClient} que {@code ms-commons} ya auto-registra
 * ({@code ParametrizacionRemoteClientAutoConfiguration}). Mirror exacto de
 * {@code infrastructure.parametrizacion.ParametrizacionCompletoLookupAdapter} de ms-usuario/
 * ms-onboarding — sin este adapter no existe ningún bean de tipo {@code ParametrizacionCompletoPort}
 * y el arranque falla con {@code NoSuchBeanDefinitionException} (ms-commons ya lo documenta como
 * el paso que más se omite; cayó exactamente en esto al escribir {@code InvitacionService}).
 *
 * <p>Delegación 1:1, sin traducir el fallo: {@link ParametrizacionRemotoException} se deja
 * propagar tal cual — es {@code InvitacionService} quien decide qué significa un fallo de
 * ms-parametrizacion al crear una invitación (no romper la creación, solo no mandar el correo).</p>
 */
@Component
public class ParametrizacionCompletoLookupAdapter implements ParametrizacionCompletoPort {

    private final ParametrizacionRemoteClient parametrizacionRemoteClient;

    public ParametrizacionCompletoLookupAdapter(ParametrizacionRemoteClient parametrizacionRemoteClient) {
        this.parametrizacionRemoteClient = parametrizacionRemoteClient;
    }

    @Override
    public Map<String, ParametroRemotoDto> cargarConfiguracionCompleta() {
        return parametrizacionRemoteClient.cargarConfiguracionCompleta();
    }
}
