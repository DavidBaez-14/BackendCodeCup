# Despliegue en Render

Guía para quien despliega BackendCodeCup en Render con Docker. Explica qué se configura, por qué, cómo se verifica y qué hacer cuando algo falla.

**Para qué existe el servicio:** que el equipo que solo trabaja en la interfaz tenga un backend estable al cual apuntar, sin acceso a este repo ni a los secretos.

**Qué no cambia:** en local se sigue corriendo desde el Spring Boot Dashboard de VS Code, como dice el [README](README.md). Docker solo se usa para la nube.

**Archivos que intervienen:**

| Archivo | Para qué |
|---|---|
| `Dockerfile` | Compila con Maven y arranca el jar con Java 21, sin root, escuchando en el `PORT` que da Render. |
| `.dockerignore` | Deja fuera `.env`, `target/` y `.git`: ningún secreto entra a la imagen. |
| `src/main/resources/application.properties` | Lee toda la configuración de variables de entorno. No hay secretos en el código. |

---

## 1. Quién tiene qué

David es el dueño del repo y de Supabase y te pasa lo que necesites. **El equipo de la interfaz no tiene acceso a este repo ni a ningún secreto.** Si te piden algo, revisa esta tabla antes de compartirlo.

| Recurso | David | Quien despliega | Equipo de la interfaz |
|---|---|---|---|
| Repo `BackendCodeCup` | Dueño | Colaborador con escritura | No |
| Panel de Render (variables y logs) | Si lo pide | Sí, es el dueño del servicio | **No.** Las variables tienen secretos y los logs, motivos de bloqueos y sanciones. |
| Panel de Supabase | Dueño | No hace falta | No |
| `DB_PASSWORD`, `SUPABASE_SECRET_KEY` | Sí | Sí | **Nunca** |
| `SEED_PASSWORD` | Sí | Sí | Solo si el servicio apunta al proyecto de desarrollo (sección 2) |
| URL del backend y Swagger | Sí | Sí | Sí. Son públicos. |
| URL de Supabase y publishable key | Sí | Sí | Sí. Son públicas: ya van dentro del JavaScript de la web. |

**Los secretos viajan por un canal privado:** en persona o con un gestor de contraseñas (por ejemplo, Bitwarden Send). Nunca por el grupo, en un commit ni en una captura del panel de Render. Cuando los pegues en Render, borra el mensaje donde te llegaron.

---

## 2. A qué Supabase apunta el servicio (lo decide David)

**Supabase #1 es producción:** tiene personas reales con documento, correo y su historial. Como el equipo de la interfaz no es de confianza total, lo recomendado es que este servicio apunte a un **proyecto aparte, "CodeCup dev"**, con datos de prueba.

| | Proyecto "CodeCup dev" (recomendado) | Supabase #1 (producción) |
|---|---|---|
| Qué ve el equipo de la interfaz | Datos ficticios del seed | Datos reales |
| Cuentas para probar | Las del seed, si David comparte `SEED_PASSWORD` | Cada uno registra la suya y David le da solo los roles que necesita. **Nunca ADMINISTRADOR.** |
| Si alguien rompe algo | Se vuelve a correr el seed | Hay que restaurar un respaldo |

**Si se usa #1, `SEED_PASSWORD` no se comparte jamás.** Es la misma contraseña para los 30 usuarios del seed, así que la de `jugador01` también abre `admin01`.

### Preparar "CodeCup dev"

Se hace una sola vez. Flyway y el seed hacen casi todo.

**Lo hace David en Supabase:**
1. **Crear el proyecto** en la región **East US (North Virginia)**, la misma de #1 y de Render. El plan gratis permite dos proyectos activos.
2. **Correo sin confirmación.** En Authentication → Sign In / Providers → Email, apagar **Confirm email**. En desarrollo no hace falta mandar correos.
3. **URLs permitidas.** En Authentication → URL Configuration, poner `http://localhost:5173` como Site URL y agregar `http://localhost:5173/**` a Redirect URLs.
4. **Bucket de comprobantes.** En Storage → New bucket, crear `comprobantes-inscripcion` como **privado**. El backend no lo crea solo.
5. **Clave secreta para Render.** En Project Settings → API Keys → Secret keys, crear una nueva llamada `render`. Así se puede revocar sin tocar nada más.
6. **Pasar los datos.** Te pasa la contraseña de la base, el host del Session pooler (Connect → Session pooler), la publishable key y la clave `render`.

**Lo haces tú:**
1. **Crear el servicio en Render** con esos valores (secciones 4 y 5). En el primer arranque, Flyway crea los schemas `identidad`, `competicion`, `finanzas` y `analytics`, y aplica todas las migraciones.
2. **Correr el seed una vez desde tu máquina**, no desde Render. Usa un `.env` que apunte a "CodeCup dev" y que incluya `SUPABASE_SECRET_KEY` y `SEED_PASSWORD`:
   ```bash
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=seed"
   ```
   Crea 30 usuarios confirmados (`admin01`, `arbitro01`, `delegado01`, `jugador01`…) y dos torneos de prueba. Es idempotente y termina solo. El detalle está en el README.

**Si más adelante hace falta el backend de producción**, es **otro servicio** de Render con las variables de #1. Nunca el mismo servicio cambiando de base.

---

## 3. Conectar Render con GitHub (lo hace David)

Render lee el repo con su GitHub App. Como el repo es de la cuenta personal de David, **la instalación la aprueba él**: un colaborador no puede.

- Al instalarla, elegir **Only select repositories → `BackendCodeCup`**.
- Si al crear el servicio Render no te muestra el repo, falta este paso.

---

## 4. Crear el servicio

En Render: **New → Web Service** y eliges `BackendCodeCup`.

| Campo | Valor | Por qué |
|---|---|---|
| Branch | `main` | Render despliega lo que entra a `main`. |
| Language / Runtime | **Docker** | Render no tiene runtime nativo de Java. |
| Region | **Virginia (US East)** | Supabase está en `us-east-1`. Otra región suma latencia a cada consulta. |
| Instance Type | Free para empezar | Ver la sección 9. |
| Dockerfile Path | `./Dockerfile` | |
| Docker Build Context Directory | `.` | |
| Health Check Path | `/actuator/health` | Responde 200 solo si la app arrancó **y** llega a la base. Si falla, Render no pasa el tráfico al despliegue nuevo. |
| Auto-Deploy | On Commit | Cada merge a `main` se despliega solo. |

**La URL queda `https://<nombre>.onrender.com`.** Es la que usa la web.

---

## 5. Variables de entorno

En Environment del servicio. Los valores salen del proyecto de Supabase que decidió David (sección 2).

| Variable | Valor | ¿Secreta? | Notas |
|---|---|---|---|
| `DB_URL` | `jdbc:postgresql://<host-del-pooler>:5432/postgres` | No | **Session pooler, puerto 5432.** No uses la conexión directa (`db.<ref>.supabase.co`, solo IPv6) ni el transaction pooler (6543), que no sirve para Hibernate ni Flyway. |
| `DB_USERNAME` | `postgres.<ref-del-proyecto>` | No | |
| `DB_PASSWORD` | La que te pasa David | **Sí** | |
| `SUPABASE_URL` | `https://<ref-del-proyecto>.supabase.co` | No | **Debe ser el mismo proyecto que usa la web** (`VITE_SUPABASE_URL`). Si no, todo responde 401. |
| `SUPABASE_PUBLISHABLE_KEY` | `sb_publishable_…` | No | Prellena el "Token de prueba" de Swagger. |
| `SUPABASE_SECRET_KEY` | La clave `render` | **Sí** | Sin ella, subir y ver comprobantes falla. |
| `FRONTEND_URLS` | `https://<web>,http://localhost:5173` | No | Ver abajo. |
| `BOOTSTRAP_ADMIN_EMAIL` | El correo de David | No | Si no hay ningún admin, esa persona recibe ADMINISTRADOR al registrarse. |
| `ANALYTICS_WEBHOOK_SECRET` | Lo indica David | **Sí** | Vacío lo deshabilita. |
| `JAVA_OPTS` | Opcional | No | Reemplaza los ajustes de memoria del Dockerfile (sección 9). |

**`FRONTEND_URLS` son los orígenes que acepta CORS, separados por coma:**
- Se comparan **exactos**: con `https://`, sin `/` al final y sin rutas.
- **El primero arma los enlaces de invitación.** Va primero la web real; `localhost` nunca va primero.
- `http://localhost:5173` deja que el equipo de la interfaz trabaje en local contra este backend.
- Las vistas previas de Vercel cambian de URL en cada rama, así que no pasan CORS. Se agregan a mano o se cambia el código a `allowedOriginPatterns`, con su spec.

**No se definen en Render:**
- `PORT` y `SERVER_PORT`: Render pone `PORT` y el Dockerfile lo usa.
- `SEED_PASSWORD`: el seed nunca corre en la nube.

**Cada vez que cambias una variable, Render redespliega.**

---

## 6. Primer despliegue y verificación

1. **En los logs del despliegue busca:**
   - Flyway: `Successfully applied N migrations` en una base nueva, o `Schema ... is up to date` si ya estaba.
   - `Started CodeCupApplication in … seconds`.
   - Con la instancia gratis, el arranque puede tardar uno o dos minutos.
2. **Salud:**
   ```bash
   curl https://<nombre>.onrender.com/actuator/health
   # {"status":"UP"}
   ```
3. **Swagger:** abre `https://<nombre>.onrender.com/swagger-ui/index.html`. En el selector de servidores la URL debe empezar por **`https://`**. Si sale `http://`, se perdió `SERVER_FORWARD_HEADERS_STRATEGY` del Dockerfile.
4. **Token y sesión:** sigue "Probar en Swagger sin la web" del README con un usuario de prueba. `GET /api/auth/me` debe responder 200 o `404 CUENTA_NO_REGISTRADA`, nunca 401.
5. **CORS:**
   ```bash
   curl -i -X OPTIONS https://<nombre>.onrender.com/api/auth/me \
     -H "Origin: http://localhost:5173" \
     -H "Access-Control-Request-Method: GET"
   # Debe traer: Access-Control-Allow-Origin: http://localhost:5173
   ```

---

## 7. Qué se le entrega al equipo de la interfaz

Su `.env.local` de la web:

```bash
VITE_SUPABASE_URL=https://<ref-del-proyecto>.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=sb_publishable_…
VITE_GATEWAY_URL=https://<nombre>.onrender.com
```

- **El contrato de la API es Swagger:** `https://<nombre>.onrender.com/swagger-ui/index.html`. El JSON está en `/v3/api-docs`.
- **La web tiene que correr en `http://localhost:5173` exacto.** Si Vite salta a 5174 porque el puerto está ocupado, CORS falla.
- **La primera petición después de un rato puede tardar un minuto** porque la instancia gratis se duerme. Abrir `/actuator/health` la despierta.
- **Cuentas para probar:** según la sección 2.

---

## 8. Cómo llegan los cambios

**`main` es lo que corre en Render.** Todo entra por PR y lo revisa la otra persona del backend.

**Antes de aprobar un PR:**
- `./mvnw test` en verde. Hoy no hay CI, así que se corre a mano.
- Si cambia la forma de un endpoint (campos, rutas, códigos de error), se avisa al equipo de la interfaz. Agregar campos no rompe nada; renombrar o quitar sí.

### Migraciones: la regla que más importa

La app corre Flyway al arrancar y Hibernate valida el esquema (`ddl-auto=validate`). **Si algo no cuadra, el backend de Render no arranca y el equipo de la interfaz se queda sin backend.**

1. **Nunca edites una migración que ya llegó a `main`.** Ya se aplicó en la base de Render. Si cambia su checksum, Flyway se niega a arrancar.
2. **Reserva el siguiente número `V<n>` en la spec o el PR** antes de escribirla. Si los dos crean un `V7` distinto, chocan.
3. **Una rama con una migración nueva no se corre contra la base de Render.** Se prueba con `./mvnw test`: `FlywayBaselineTest` aplica todas las migraciones sobre un Postgres embebido. Si hace falta, también contra un Postgres local.
4. **Las migraciones agregan; no borran ni renombran de golpe.** Para quitar una columna, primero un despliegue deja de usarla y en uno posterior la migración la borra.
5. **Un cambio de entidad sin su migración** rompe el arranque con `Schema-validation: missing column`.

### Volver atrás

En Render: Events → un despliegue anterior → **Rollback**.

**El rollback revierte el código, no la base.** Flyway ignora migraciones más nuevas que el código, y si la migración solo agregó cosas, la versión anterior sigue funcionando. Por eso la regla 4.

---

## 9. Plan gratis: lo que hay que saber

- **Se duerme tras 15 minutos sin tráfico.** El siguiente arranque de Spring Boot con poca CPU tarda alrededor de un minuto.
- **750 horas gratis al mes por workspace.** Las horas dormido no cuentan.
- **Disco efímero:** se borra en cada despliegue o reinicio. No afecta: los comprobantes van a Supabase Storage.
- **Memoria:** la instancia gratis es pequeña (512 MB al escribir esto; revisa render.com/pricing). El Dockerfile limita el heap al 60 % para dejar espacio al resto de la JVM.
  - Si Render mata el proceso (`Exited with status 137` u `OutOfMemoryError`), primero prueba con `JAVA_OPTS` y `-XX:MaxRAMPercentage=50`. Si sigue, sube a una instancia paga.
- **Supabase gratis pausa el proyecto tras una semana sin uso.** El health check empieza a fallar. David lo reactiva desde el panel.
- **Conexiones:** cada backend que corre contra la misma base, incluido uno local, abre hasta 5. El pooler del plan gratis tiene un tope bajo, así que conviene no pasar de dos o tres backends a la vez.

---

## 10. Problemas comunes

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| El despliegue falla por "no open ports" o timeout | La app se cayó al arrancar o se cambió el `CMD` del Dockerfile | Busca la primera excepción en los logs. El `CMD` debe usar `-Dserver.port=${PORT:-8082}`. |
| `Migration checksum mismatch for migration version N` | Alguien editó una migración ya aplicada | Revierte ese archivo en `main` a como estaba. **No corras `flyway repair` sin hablar con David.** |
| `Schema-validation: missing column/table` | El código pide algo que ninguna migración creó | Falta la migración en el PR. |
| `password authentication failed` o timeout a la base | `DB_URL`, `DB_USERNAME` o `DB_PASSWORD` | Session pooler, puerto 5432, usuario `postgres.<ref>`. |
| `Max client connections reached` / `EMAXCONNSESSION` | Demasiados backends contra la misma base | Apaga los backends locales que apunten a ella. |
| Todo responde 401 | El token es de otro proyecto de Supabase | `SUPABASE_URL` del backend = `VITE_SUPABASE_URL` de la web. |
| `404 CUENTA_NO_REGISTRADA` | Normal: el usuario aún no se registró en CodeCup | `POST /api/auth/registro`. |
| Error de CORS en el navegador | El origen no está en `FRONTEND_URLS` | Agrega el origen exacto, sin `/` final. Render redespliega solo. |
| Swagger: "Failed to fetch" con URL `http://` | Faltan los encabezados reenviados | Revisa `ENV SERVER_FORWARD_HEADERS_STRATEGY` en el Dockerfile. |
| Falla subir o ver comprobantes | `SUPABASE_SECRET_KEY` vacía o no existe el bucket | Revisa la variable y el bucket `comprobantes-inscripcion` en Storage. |
| El health check responde 503 | La app no llega a la base | Supabase pausado o credenciales. Revisa los logs. |
| `Exited with status 137` | Sin memoria | Sección 9. |

---

## 11. Seguridad y cierre

- **Activa la verificación en dos pasos** en GitHub y en Render.
- **No compartas capturas de los logs:** las notificaciones escriben en ellos IDs de personas y cuentas junto con motivos de bloqueo y sanciones.
- **Si se filtra un secreto:**
  1. David revoca la clave `render` en Supabase (es inmediato) y crea otra.
  2. Si se filtró la contraseña de la base, David la cambia.
  3. Actualizas las variables en Render.
- **Al terminar el proyecto o si alguien sale del equipo:** se revoca la clave `render`, se borra o transfiere el servicio de Render y David cambia la contraseña de la base (y actualiza su `.env`).

---

## Opcional: probar la imagen en tu máquina

Antes de tocar Render puedes comprobar que la imagen arranca:

```bash
docker build -t backendcodecup .
docker run --rm -p 8082:8082 --env-file .env backendcodecup
# Luego: http://localhost:8082/actuator/health
```

- **Se conecta a la base que diga tu `.env`.**
- `--env-file` no quita comillas: los valores del `.env` van sin ellas.
