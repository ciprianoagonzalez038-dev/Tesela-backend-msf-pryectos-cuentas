# =============================================================================
# ms-xrello-cuentas — imagen Docker (Java 17 / Spring Boot 3.5)
#
# IMPORTANTE: el contexto de build debe ser el directorio padre MS/, no
# ms-xrello-cuentas/, porque ms-xrello-cuentas depende de ms-commons como
# artefacto Maven local (no existe un pom.xml agregador en el workspace).
# Construir:
#
#   cd MS/
#   docker build -f ms-xrello-cuentas/Dockerfile -t ms-xrello-cuentas:latest .
#
# docker-compose.yml (en esta misma carpeta) ya resuelve esto con
# "context: .." — basta con `docker compose up --build` desde ms-xrello-cuentas/.
# =============================================================================

# ── Etapa 1: build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# ms-commons se instala primero en el repo local de Maven del contenedor,
# igual que en el flujo de desarrollo local (mvn install -DskipTests).
COPY ms-commons/pom.xml ms-commons/pom.xml
RUN mvn -q -f ms-commons/pom.xml -DskipTests dependency:go-offline || true
COPY ms-commons/src ms-commons/src
RUN mvn -q -f ms-commons/pom.xml install -DskipTests

# ms-xrello-cuentas — ya puede resolver ms-commons desde el repo local recien instalado.
COPY ms-xrello-cuentas/pom.xml ms-xrello-cuentas/pom.xml
RUN mvn -q -f ms-xrello-cuentas/pom.xml -DskipTests dependency:go-offline || true
COPY ms-xrello-cuentas/src ms-xrello-cuentas/src
RUN mvn -q -f ms-xrello-cuentas/pom.xml package -DskipTests

# ── Etapa 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /workspace/ms-xrello-cuentas/target/ms-xrello-cuentas-0.0.1-SNAPSHOT.jar app.jar

# Perfil "docker" (ver application-docker.properties): Eureka HA de 3 peers y
# datasource apuntando a host.docker.internal por defecto.
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8007

ENTRYPOINT ["java", "-jar", "app.jar"]
