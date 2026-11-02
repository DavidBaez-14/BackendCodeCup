# BackendCodeCup

El backend único de CodeCup: un monolito modular en Spring Boot 3.3 con Java 21. Lo consumen la web de torneos de la FIS (`codecup.games`) y, más adelante, la app móvil multideporte.

| Módulo (`terminus.co.edu.ufps.codecup.*`) | Qué hace | Schema en Supabase #1 |
|---|---|---|
| `identidad` | Personas, cuentas, roles e invitaciones | `identidad` |
| `competicion` | Torneos, equipos, partidos, perfiles | `competicion` |
| `finanzas` | Comprobantes y multas | `finanzas` |
| `analytics` | Tablas, goleadores, porteros y premios públicos | `analytics` |
| `notificaciones` | Salida única de notificaciones (por ahora, logs) | — |
| `shared` | Seguridad, errores (ProblemDetail + `code`) y OpenAPI | — |

**Origen del código:**
- `competicion`, `finanzas` y `analytics` vienen de `supercopa`.
- `identidad` reemplaza a `AuthCodeCup` y se reescribió sin Appwrite ni padrón.

El diseño completo está en la documentación del workspace, fuera de este repo: la spec `.context/specs/0017-backendcodecup-supabase-auth.md` y el plan `.context/docs/plan_auth_modos_identidad.md`.

## Autenticación

- **El login lo hace el cliente directamente con Supabase Auth:** correo con confirmación, Google y recuperación de contraseña.
- **El backend solo valida el access token** con el JWKS público de Supabase (ES256). **Nunca llama a la API de Supabase Auth.**
- **Los roles viven en `identidad.cuenta_roles`**, no en el token. Un cambio de rol se nota en la siguiente petición.

**Flujo de un usuario nuevo:**
1. Inicia sesión en Supabase.
2. `GET /api/auth/me` responde `404` con `CUENTA_NO_REGISTRADA`.
3. `POST /api/auth/registro` crea su persona, su cuenta y el rol JUGADOR, y le aplica las invitaciones pendientes de su correo.

**El primer administrador** sale de `BOOTSTRAP_ADMIN_EMAIL`. Al arrancar, si no hay ningún admin, se crea una invitación de ADMINISTRADOR para ese correo. Se aplica cuando esa persona se registra.

## Correr en local

1. Copia `.env.example` como `.env` y completa estos valores desde los paneles. **`.env` nunca se sube.**
   - `DB_PASSWORD`: Supabase #1 → Connect → Session pooler.
   - `SUPABASE_SECRET_KEY`: Project Settings → API Keys → Secret key (`sb_secret_…`). Solo hace falta para los comprobantes.
   - `BOOTSTRAP_ADMIN_EMAIL`: tu correo.
2. Arranca `CodeCupApplication` desde el Spring Boot Dashboard de VS Code (puerto `8082`).
   - **La primera vez, Flyway crea los 4 schemas y aplica `db/migration` en Supabase #1.** Después, Hibernate solo valida (`ddl-auto=validate`).
3. Swagger está en `http://localhost:8082/swagger-ui/index.html`. Pega el access token en "Authorize".

### Obtener un access token sin la web

Usa la **publishable key** (`sb_publishable_…`), que es pública:

```bash
# 1. Crear el usuario. Supabase envía el correo de confirmación por Brevo.
curl -X POST "https://jcuzocrgyomnuagvnwra.supabase.co/auth/v1/signup" \
  -H "apikey: <PUBLISHABLE_KEY>" -H "Content-Type: application/json" \
  -d '{"email":"tu@correo.com","password":"una-contraseña-larga"}'

# 2. Después de confirmar el correo: pedir el token (dura 1 hora).
curl -X POST "https://jcuzocrgyomnuagvnwra.supabase.co/auth/v1/token?grant_type=password" \
  -H "apikey: <PUBLISHABLE_KEY>" -H "Content-Type: application/json" \
  -d '{"email":"tu@correo.com","password":"una-contraseña-larga"}'
```

El campo `access_token` de la respuesta es el que va en `Authorization: Bearer …`.

## Tests

```bash
./mvnw test        # Windows: .\mvnw.cmd test
```

- Corren sobre **H2 en memoria**, sin Docker ni Supabase.
- `FlywayBaselineTest` es la excepción: levanta un PostgreSQL embebido (zonky, sin Docker) para comprobar que las migraciones aplican y que Hibernate valida contra ellas.

## Cambios de esquema

**Nunca a mano.** Cada cambio es una migración nueva `src/main/resources/db/migration/V<n>__<descripcion>.sql`. `FlywayBaselineTest` comprueba que entidades y migraciones coinciden.
