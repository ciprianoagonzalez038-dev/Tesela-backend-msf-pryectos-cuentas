package com.springboot.cloud.cagr.xrello.cuentas;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de cuentas, usuarios y membresias de Xrello.
 *
 * <p>Todo el dominio vive bajo el paquete base
 * {@code com.springboot.cloud.cagr.xrello.cuentas} — sin paquetes hermanos, por eso
 * NO hacen falta {@code @ComponentScan}/{@code @EntityScan}/{@code @EnableJpaRepositories}
 * manuales (ver XRELLO_CONTRATO.md §4): el component-scan por defecto de
 * {@code @SpringBootApplication} ya alcanza todo.</p>
 *
 * <p>El bloque {@code static} fija la JVM en UTC antes de que Spring levante el
 * contexto (JPA, Flyway, etc.), en cualquier camino de arranque (nativo,
 * {@code @SpringBootTest}, Docker) sin depender de flags {@code -Duser.timezone}
 * que haya que replicar en cada mecanismo de arranque.</p>
 */
@SpringBootApplication
public class MsXrelloCuentasApplication {

	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(MsXrelloCuentasApplication.class, args);
	}

}
