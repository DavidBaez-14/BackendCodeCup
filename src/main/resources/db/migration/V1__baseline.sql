-- ═════════════════════════════════════════════════════════════════════════════
-- V1 · Línea base de BackendCodeCup (spec 0017)
--
-- Un schema por módulo en Supabase #1:
--   identidad    personas, cuentas, roles e invitaciones (reemplaza a MS1)
--   competicion  torneos, equipos, partidos… (ex schema "supercopa" de MS2)
--   finanzas     comprobantes y multas (MS3)
--   analytics    snapshots de tablas, goleadores y porteros (MS4)
--
-- Cómo se armó: DDL generado desde las entidades JPA (Hibernate, dialecto
-- PostgreSQL), más las reglas que la BD de supercopa ya tenía en producción y
-- que las entidades no declaran (sección final). Hibernate corre con
-- ddl-auto=validate: cualquier cambio de esquema va en una migración nueva.
-- ═════════════════════════════════════════════════════════════════════════════


create schema if not exists analytics;

create schema if not exists competicion;

create schema if not exists finanzas;

create schema if not exists identidad;

create table analytics.snapshot_goleador (
    goles integer not null,
    posicion integer,
    actualizado_en timestamp(6) not null,
    id uuid not null default gen_random_uuid(),
    torneo_id uuid not null,
    cedula varchar(20) not null,
    equipo_nombre varchar(150),
    jugador_nombre varchar(150),
    primary key (id),
    unique (torneo_id, cedula)
);

create table analytics.snapshot_portero (
    goles_en_contra integer not null,
    partidos_jugados integer not null,
    posicion integer,
    promedio_gc numeric(5,2),
    actualizado_en timestamp(6) not null,
    equipo_torneo_id uuid not null,
    id uuid not null default gen_random_uuid(),
    torneo_id uuid not null,
    equipo_nombre varchar(150),
    primary key (id),
    unique (torneo_id, equipo_torneo_id)
);

create table analytics.snapshot_posicion (
    descalificado boolean not null,
    dg integer not null,
    gc integer not null,
    gf integer not null,
    pe integer not null,
    pg integer not null,
    pj integer not null,
    posicion integer not null,
    pp integer not null,
    pts integer not null,
    rojas integer not null,
    actualizado_en timestamp(6) not null,
    grupo varchar(10) not null,
    equipo_torneo_id uuid not null,
    id uuid not null default gen_random_uuid(),
    torneo_id uuid not null,
    form varchar(20),
    equipo_nombre varchar(150) not null,
    primary key (id),
    unique (torneo_id, equipo_torneo_id)
);

create table analytics.webhook_log (
    procesado_en timestamp(6),
    recibido_en timestamp(6) not null,
    id uuid not null default gen_random_uuid(),
    partido_id uuid,
    torneo_id uuid,
    tipo varchar(40) not null,
    error TEXT,
    payload_json TEXT,
    primary key (id)
);

create table competicion.equipo_torneo (
    fecha_expulsion timestamp(6),
    fecha_inscripcion timestamp(6) not null,
    equipo_id uuid not null,
    id uuid not null default gen_random_uuid(),
    torneo_id uuid not null,
    delegado_cedula varchar(20) not null,
    estado_inscripcion varchar(20) not null check (estado_inscripcion in ('PENDIENTE_PAGO','APROBADO','RECHAZADO','EXPULSADO')),
    aprobado_por varchar(50),
    expulsado_por varchar(50),
    motivo_expulsion varchar(500),
    motivo_rechazo varchar(500),
    primary key (id),
    unique (torneo_id, equipo_id),
    unique (torneo_id, delegado_cedula)
);

create table competicion.equipos (
    created_at timestamp(6) not null,
    id uuid not null default gen_random_uuid(),
    delegado_cedula varchar(20) not null unique,
    nombre varchar(150) not null,
    primary key (id)
);

create table competicion.eventos_partido (
    orden integer not null,
    created_at timestamp(6) not null,
    equipo_torneo_id uuid not null,
    id uuid not null default gen_random_uuid(),
    partido_id uuid not null,
    cedula varchar(20),
    tipo_evento varchar(20) not null check (tipo_evento in ('GOL','AMARILLA','AZUL','ROJA')),
    primary key (id),
    unique (partido_id, orden)
);

create table competicion.jugador_equipo (
    altura_cm_snapshot integer,
    fecha_fin date,
    fecha_inicio date not null,
    numero_camiseta integer,
    semestre_snapshot integer,
    snapshot_at timestamp(6),
    equipo_torneo_id uuid not null,
    id uuid not null default gen_random_uuid(),
    torneo_id uuid not null,
    cedula varchar(20) not null,
    estado varchar(20) not null check (estado in ('ACTIVO','RETIRADO')),
    pierna_habil_snapshot varchar(20),
    rol_jugador_snapshot varchar(20) check (rol_jugador_snapshot in ('ESTUDIANTE','GRADUADO','PROFESOR','ADMINISTRATIVO')),
    posicion_snapshot varchar(30),
    nombre_snapshot varchar(150),
    primary key (id),
    unique (cedula, torneo_id)
);

create table competicion.jugadores (
    altura_cm integer,
    created_at timestamp(6) not null,
    cedula varchar(20) not null,
    pierna_habil varchar(20),
    posicion varchar(30),
    apodo varchar(60),
    correo varchar(150),
    nombre varchar(150) not null,
    foto_url varchar(500),
    primary key (cedula)
);

create table competicion.multa_config (
    fechas_suspension_roja integer not null,
    monto_amarilla numeric(12,2) not null,
    monto_azul numeric(12,2) not null,
    monto_roja numeric(12,2) not null,
    created_at timestamp(6) not null,
    id uuid not null default gen_random_uuid(),
    torneo_id uuid not null unique,
    primary key (id)
);

create table competicion.partido_jugador (
    goles integer not null,
    jugo boolean not null,
    equipo_torneo_id uuid not null,
    id uuid not null default gen_random_uuid(),
    partido_id uuid not null,
    cedula varchar(20) not null,
    primary key (id),
    unique (partido_id, cedula)
);

create table competicion.partidos (
    grupo varchar(1),
    jornada integer,
    created_at timestamp(6) not null,
    fecha timestamp(6) not null,
    siguiente_slot varchar(10) check (siguiente_slot in ('LOCAL','VISITANTE')),
    equipo_local_torneo_id uuid,
    equipo_visitante_torneo_id uuid,
    id uuid not null default gen_random_uuid(),
    siguiente_partido_id uuid,
    torneo_id uuid not null,
    estado varchar(20) not null check (estado in ('PROGRAMADO','EN_CURSO','FINALIZADO','APLAZADO','WO','DESCANSO')),
    fase varchar(20) check (fase in ('GRUPOS','REPECHAJE','OCTAVOS','CUARTOS','SEMIS','FINAL','TERCER_PUESTO')),
    tipo_cierre varchar(30),
    primary key (id)
);

create table competicion.premio_asignado (
    fecha_asignacion timestamp(6) not null,
    equipo_torneo_id uuid,
    id uuid not null default gen_random_uuid(),
    premio_id uuid not null,
    torneo_id uuid not null,
    cedula varchar(20),
    primary key (id),
    unique (torneo_id, premio_id)
);

create table competicion.premio_catalogo (
    id uuid not null default gen_random_uuid(),
    codigo varchar(50) not null unique,
    nombre varchar(100) not null,
    descripcion varchar(250),
    primary key (id)
);

create table competicion.solicitudes_equipo (
    fecha_resolucion timestamp(6),
    fecha_solicitud timestamp(6) not null,
    equipo_torneo_id uuid not null,
    id uuid not null default gen_random_uuid(),
    torneo_id uuid not null,
    cedula varchar(20) not null,
    estado varchar(20) not null check (estado in ('PENDIENTE','APROBADA','RECHAZADA')),
    correo varchar(150),
    nombre varchar(150) not null,
    motivo_rechazo varchar(500),
    primary key (id)
);

create table competicion.titulos (
    fecha date,
    equipo_torneo_id uuid not null,
    id uuid not null default gen_random_uuid(),
    torneo_id uuid not null,
    puesto varchar(20) not null check (puesto in ('CAMPEON','SUBCAMPEON','TERCERO')),
    primary key (id),
    unique (torneo_id, puesto)
);

create table competicion.torneo_premio (
    monto integer,
    created_at timestamp(6) not null,
    id uuid not null default gen_random_uuid(),
    premio_id uuid not null,
    torneo_id uuid not null,
    titulo varchar(150),
    descripcion varchar(300),
    primary key (id)
);

create table competicion.torneos (
    clasifican_por_grupo integer,
    edicion integer not null,
    fecha_fin date,
    fecha_inicio date,
    monto_inscripcion numeric(12,2),
    num_grupos integer,
    repechaje boolean not null,
    configurado_en timestamp(6),
    created_at timestamp(6) not null,
    publicado_en timestamp(6),
    id uuid not null default gen_random_uuid(),
    estado varchar(20) not null check (estado in ('BORRADOR','PUBLICADO','EN_CURSO','FINALIZADO')),
    formato varchar(30) check (formato in ('LIGA','ELIMINACION_DIRECTA','GRUPOS_ELIMINATORIAS','CHAMPIONS')),
    banco_destino varchar(80),
    cuenta_destino varchar(120),
    nombre varchar(150) not null,
    titular_cuenta varchar(150),
    rondas_playoff varchar(200),
    primary key (id)
);

create table finanzas.comprobantes (
    fecha_resolucion timestamp(6),
    fecha_subida timestamp(6) not null,
    id uuid not null default gen_random_uuid(),
    referencia_id uuid not null,
    aprobado_por varchar(20),
    cedula_uploader varchar(20) not null,
    tipo varchar(20) not null check (tipo in ('INSCRIPCION','MULTA')),
    estado varchar(25) not null check (estado in ('PENDIENTE_REVISION','APROBADO','RECHAZADO')),
    motivo_rechazo varchar(500),
    url_archivo varchar(500) not null,
    primary key (id)
);

create table finanzas.multas (
    habilitado_manual boolean not null,
    monto numeric(12,2) not null,
    partidos_suspension integer not null,
    partidos_suspension_restantes integer,
    fecha_generacion timestamp(6) not null,
    fecha_pago timestamp(6),
    habilitado_en timestamp(6),
    equipo_torneo_id uuid not null,
    id uuid not null default gen_random_uuid(),
    partido_id uuid not null,
    partido_pago_id uuid,
    torneo_id uuid not null,
    cedula varchar(20) not null,
    estado varchar(20) not null check (estado in ('PENDIENTE','EN_REVISION','PAGADA','CONDONADA')),
    habilitado_por_cedula varchar(20),
    pagado_por_cedula varchar(20),
    tipo_sancion varchar(30) not null check (tipo_sancion in ('AMARILLA','AZUL','ROJA','ACUMULACION_AMARILLAS','ROJA_DIRECTA')),
    motivo_habilitacion varchar(200),
    primary key (id)
);

create table identidad.cuenta_roles (
    fecha_resolucion timestamp(6),
    fecha_solicitud timestamp(6) not null,
    cuenta_id uuid not null,
    id uuid not null default gen_random_uuid(),
    rol varchar(20) not null check (rol in ('ADMINISTRADOR','ARBITRO','DELEGADO','JUGADOR')),
    estado varchar(25) not null check (estado in ('PENDIENTE','PENDIENTE_VALIDACION','APROBADO','RECHAZADO')),
    motivo_rechazo varchar(500),
    motivo_solicitud varchar(500),
    primary key (id),
    constraint cuenta_rol_unique unique (cuenta_id, rol)
);

create table identidad.cuentas (
    fecha_creacion timestamp(6) not null,
    id uuid not null default gen_random_uuid(),
    persona_id uuid unique,
    estado varchar(20) not null check (estado in ('ACTIVA','BLOQUEADA')),
    proveedor varchar(30) not null,
    proveedor_sub varchar(100) not null,
    correo varchar(150),
    primary key (id),
    constraint cuentas_proveedor_sub_uk unique (proveedor, proveedor_sub)
);

create table identidad.invitaciones (
    created_at timestamp(6) not null,
    fecha_resolucion timestamp(6),
    aceptada_por uuid,
    creada_por uuid,
    id uuid not null default gen_random_uuid(),
    estado varchar(20) not null check (estado in ('PENDIENTE','ACEPTADA','REVOCADA')),
    rol varchar(20) not null check (rol in ('ADMINISTRADOR','ARBITRO','DELEGADO','JUGADOR')),
    token varchar(64) not null unique,
    correo varchar(150) not null,
    primary key (id)
);

create table identidad.personas (
    created_at timestamp(6) not null,
    id uuid not null default gen_random_uuid(),
    numero_documento varchar(20),
    tipo_documento varchar(20) check (tipo_documento in ('CC','TI','CE','PPT','PASAPORTE')),
    apellidos varchar(100),
    nombres varchar(100) not null,
    primary key (id),
    constraint personas_documento_uk unique (tipo_documento, numero_documento)
);


-- Llaves foráneas declaradas en las entidades
alter table competicion.equipo_torneo add constraint equipo_torneo_equipo_id_fk foreign key (equipo_id) references competicion.equipos;
alter table competicion.equipo_torneo add constraint equipo_torneo_torneo_id_fk foreign key (torneo_id) references competicion.torneos;
alter table competicion.eventos_partido add constraint eventos_partido_equipo_torneo_id_fk foreign key (equipo_torneo_id) references competicion.equipo_torneo;
alter table competicion.eventos_partido add constraint eventos_partido_partido_id_fk foreign key (partido_id) references competicion.partidos;
alter table competicion.jugador_equipo add constraint jugador_equipo_equipo_torneo_id_fk foreign key (equipo_torneo_id) references competicion.equipo_torneo;
alter table competicion.jugador_equipo add constraint jugador_equipo_torneo_id_fk foreign key (torneo_id) references competicion.torneos;
alter table competicion.partido_jugador add constraint partido_jugador_equipo_torneo_id_fk foreign key (equipo_torneo_id) references competicion.equipo_torneo;
alter table competicion.partido_jugador add constraint partido_jugador_cedula_fk foreign key (cedula) references competicion.jugadores;
alter table competicion.partido_jugador add constraint partido_jugador_partido_id_fk foreign key (partido_id) references competicion.partidos;
alter table competicion.partidos add constraint partidos_equipo_local_torneo_id_fk foreign key (equipo_local_torneo_id) references competicion.equipo_torneo;
alter table competicion.partidos add constraint partidos_equipo_visitante_torneo_id_fk foreign key (equipo_visitante_torneo_id) references competicion.equipo_torneo;
alter table competicion.partidos add constraint partidos_siguiente_partido_id_fk foreign key (siguiente_partido_id) references competicion.partidos;
alter table competicion.partidos add constraint partidos_torneo_id_fk foreign key (torneo_id) references competicion.torneos;
alter table competicion.solicitudes_equipo add constraint solicitudes_equipo_equipo_torneo_id_fk foreign key (equipo_torneo_id) references competicion.equipo_torneo;
alter table competicion.solicitudes_equipo add constraint solicitudes_equipo_torneo_id_fk foreign key (torneo_id) references competicion.torneos;
alter table competicion.titulos add constraint titulos_equipo_torneo_id_fk foreign key (equipo_torneo_id) references competicion.equipo_torneo;
alter table competicion.titulos add constraint titulos_torneo_id_fk foreign key (torneo_id) references competicion.torneos;
alter table identidad.cuenta_roles add constraint cuenta_roles_cuenta_id_fk foreign key (cuenta_id) references identidad.cuentas;
alter table identidad.cuentas add constraint cuentas_persona_id_fk foreign key (persona_id) references identidad.personas;
alter table identidad.invitaciones add constraint invitaciones_aceptada_por_fk foreign key (aceptada_por) references identidad.cuentas;
alter table identidad.invitaciones add constraint invitaciones_creada_por_fk foreign key (creada_por) references identidad.cuentas;

-- ─────────────────────────────────────────────────────────────────────────────
-- Reglas de la BD de supercopa que las entidades no declaran
-- (db/oficiales/SQL_MS2_Schema-Supercopa.sql y migraciones iter1/iter7).
-- El código cuenta con ellas: por ejemplo, garantiza la fila en jugadores
-- antes de crear un equipo o una membresía.
-- ─────────────────────────────────────────────────────────────────────────────
alter table competicion.equipos add constraint equipos_delegado_cedula_fk foreign key (delegado_cedula) references competicion.jugadores;
alter table competicion.jugador_equipo add constraint jugador_equipo_cedula_fk foreign key (cedula) references competicion.jugadores;
alter table competicion.solicitudes_equipo add constraint solicitudes_equipo_cedula_fk foreign key (cedula) references competicion.jugadores;
alter table competicion.eventos_partido add constraint eventos_partido_cedula_fk foreign key (cedula) references competicion.jugadores;
alter table competicion.premio_asignado add constraint premio_asignado_cedula_fk foreign key (cedula) references competicion.jugadores;
alter table competicion.premio_asignado add constraint premio_asignado_torneo_id_fk foreign key (torneo_id) references competicion.torneos;
alter table competicion.premio_asignado add constraint premio_asignado_equipo_torneo_id_fk foreign key (equipo_torneo_id) references competicion.equipo_torneo;
alter table competicion.torneo_premio add constraint torneo_premio_torneo_id_fk foreign key (torneo_id) references competicion.torneos;
alter table competicion.torneo_premio add constraint torneo_premio_premio_id_fk foreign key (premio_id) references competicion.premio_catalogo;
alter table competicion.multa_config add constraint multa_config_torneo_id_fk foreign key (torneo_id) references competicion.torneos on delete cascade;
alter table finanzas.multas add constraint multas_partido_pago_id_fk foreign key (partido_pago_id) references competicion.partidos;

alter table competicion.jugador_equipo add constraint jugador_equipo_numero_camiseta_check check (numero_camiseta is null or numero_camiseta > 0);
alter table competicion.partido_jugador add constraint partido_jugador_goles_check check (goles >= 0);
alter table competicion.eventos_partido add constraint eventos_partido_orden_check check (orden > 0);

create index idx_equipo_torneo_torneo_estado on competicion.equipo_torneo (torneo_id, estado_inscripcion);
create index idx_eventos_partido_cedula on competicion.eventos_partido (cedula);
create index idx_jugador_equipo_cedula on competicion.jugador_equipo (cedula);
create index idx_partido_jugador_cedula on competicion.partido_jugador (cedula);
create index idx_solicitudes_equipo_equipo_torneo_estado on competicion.solicitudes_equipo (equipo_torneo_id, estado);
create index idx_multas_cedula_torneo_estado on finanzas.multas (cedula, torneo_id, estado);

-- ─────────────────────────────────────────────────────────────────────────────
-- Identidad
-- ─────────────────────────────────────────────────────────────────────────────
-- El documento va completo (tipo y número) o no va. Varias personas sin documento: OK.
alter table identidad.personas add constraint personas_documento_completo_check
    check ((tipo_documento is null) = (numero_documento is null));
create index idx_invitaciones_correo_estado on identidad.invitaciones (correo, estado);
