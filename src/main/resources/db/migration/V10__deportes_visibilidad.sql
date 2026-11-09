-- ═════════════════════════════════════════════════════════════════════════════
-- V10 · Deportes, visibilidad y cupos (spec 0029, M4 mínima)
--
-- 1. Catálogo de deportes. Cada torneo es de un deporte; los que ya existen son de
--    fútbol sala (lo único que había).
-- 2. Visibilidad (plan §6): PUBLICO aparece en Explorar y se ve sin cuenta;
--    NO_LISTADO solo con el enlace; PRIVADO solo para sus participantes. Los torneos
--    que ya existen quedan PUBLICO: hoy cualquier cuenta los ve en las listas, y así
--    no cambia nada para la web. El organizador lo cambia cuando quiera.
-- 3. enlace_token: aleatorio y regenerable, para compartir y revocar un enlace.
-- 4. cupos (máximo de inscripciones aprobadas, null = sin límite), cierre de
--    inscripciones y reglas del torneo (texto libre).
-- ═════════════════════════════════════════════════════════════════════════════

create table competicion.deportes (
    activo boolean not null default true,
    orden integer not null,
    codigo varchar(30) not null,
    nombre varchar(60) not null,
    tipo_competencia varchar(20) not null,
    primary key (codigo),
    constraint deportes_tipo_competencia_check check (tipo_competencia in ('EQUIPO','INDIVIDUAL'))
);

insert into competicion.deportes (codigo, nombre, tipo_competencia, orden) values
    ('FUTSAL',     'Fútbol sala',   'EQUIPO',     1),
    ('VOLEIBOL',   'Voleibol',      'EQUIPO',     2),
    ('BALONCESTO', 'Baloncesto',    'EQUIPO',     3),
    ('TENIS_MESA', 'Tenis de mesa', 'INDIVIDUAL', 4)
on conflict (codigo) do nothing;

alter table competicion.torneos
    add column deporte varchar(30) not null default 'FUTSAL',
    add column visibilidad varchar(20) not null default 'PUBLICO',
    add column enlace_token varchar(64),
    add column cupos integer,
    add column cierre_inscripciones date,
    add column reglas varchar(2000);

update competicion.torneos set enlace_token = replace(gen_random_uuid()::text, '-', '') where enlace_token is null;

alter table competicion.torneos
    alter column enlace_token set not null,
    -- Por si un torneo se crea con SQL directo (scripts, respaldos): también queda con enlace.
    alter column enlace_token set default replace(gen_random_uuid()::text, '-', ''),
    add constraint torneos_enlace_token_uk unique (enlace_token),
    add constraint torneos_deporte_fk foreign key (deporte) references competicion.deportes,
    add constraint torneos_visibilidad_check check (visibilidad in ('PUBLICO','NO_LISTADO','PRIVADO')),
    add constraint torneos_cupos_check check (cupos is null or (cupos between 2 and 64));

create index idx_torneos_visibilidad_deporte on competicion.torneos (visibilidad, deporte);
