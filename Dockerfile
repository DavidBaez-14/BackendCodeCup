# Imagen para desplegar BackendCodeCup en Render (ver DESPLIEGUE.md).
# En local se sigue corriendo desde el Spring Boot Dashboard de VS Code; Docker solo se usa para la nube.

# ── 1. Compilar ──────────────────────────────────────────────────────────────
# Usa el mvn de la imagen y no ./mvnw: en Windows el checkout puede dejar mvnw con CRLF y romperlo en Linux.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Primero solo el pom: si no cambian las dependencias, Docker reutiliza esta capa y el build es más rápido.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Los tests no corren aquí: el Postgres embebido de FlywayBaselineTest no arranca como root dentro del build.
# Se corren antes del merge con ./mvnw test.
RUN mvn -B -q package -DskipTests

# ── 2. Ejecutar ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

# Sin root dentro del contenedor.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /build/target/codecup-*.jar /app/app.jar
USER app

# Render termina el HTTPS y reenvía la petición con X-Forwarded-*. Sin esto, Swagger arma URLs http://
# y "Try it out" falla por contenido mixto.
ENV SERVER_FORWARD_HEADERS_STRATEGY=framework

# Memoria pensada para la instancia gratis de Render. Se puede reemplazar con la variable JAVA_OPTS en Render.
# - MaxRAMPercentage=60: deja espacio para metaspace e hilos y evita que Render mate el proceso por memoria.
# - SerialGC: el recolector más liviano para una CPU fraccionada.
# - TieredStopAtLevel=1: arranque más rápido con poca CPU, a cambio de algo de rendimiento máximo.
# - ExitOnOutOfMemoryError: si se queda sin memoria, termina y Render lo reinicia en vez de quedar colgado.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

# Render inyecta PORT (10000 por defecto). En local, sin PORT, queda en 8082 como siempre.
EXPOSE 8082
CMD ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8082} -jar /app/app.jar"]
