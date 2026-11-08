-- ═════════════════════════════════════════════════════════════════════════════
-- V5 · Identidad por persona (spec 0022, M2 parte 1)
--
-- La llave de la competición deja de ser el número de documento y pasa a ser
-- identidad.personas.id. Los datos existentes se conservan:
--
--   participantes (plantel, alineación, eventos, solicitudes, premios, multas,
--   delegados)  →  persona_id, FK a identidad.personas
--   actores (quién aprobó, expulsó, subió, resolvió, pagó o habilitó)
--               →  *_cuenta_id, FK a identidad.cuentas; null si no hay cuenta
--               (por ejemplo, el texto 'seed')
--
-- competicion.jugadores desaparece: el nombre ya vive en identidad.personas y los
-- datos deportivos pasan a competicion.perfil_deportivo.
--
-- Si una cédula de participante no se puede resolver a una persona, la migración
-- falla entera (NOT NULL). En Supabase #1 se comprobó antes que no hay ninguna.
-- ═════════════════════════════════════════════════════════════════════════════


-- ─── 0. Personas ─────────────────────────────────────────────────────────────
-- El delegado puede escribir nombres de hasta 150 caracteres (spec 0018).
alter table identidad.personas alter column nombres type varchar(150);

-- El número de documento no se repite aunque cambie el tipo: en Colombia la TI y
-- la CC comparten el NUIP. El registro ya lo exigía; ahora también la BD.
create unique index personas_numero_documento_uk
    on identidad.personas (numero_documento) where numero_documento is not null;


-- ─── 1. Miembros sin cuenta (spec 0018) → personas sin cuenta ─────────────────
-- La 0018 no guardaba el tipo de documento: se asume CC.
insert into identidad.personas (nombres, tipo_documento, numero_documento, created_at)
select j.nombre, 'CC', j.cedula, j.created_at
from competicion.jugadores j
where not exists (select 1 from identidad.personas p where p.numero_documento = j.cedula);


-- ─── 2. Perfil deportivo (ex competicion.jugadores) ──────────────────────────
create table competicion.perfil_deportivo (
    altura_cm integer,
    created_at timestamp(6) not null,
    persona_id uuid not null,
    pierna_habil varchar(20),
    posicion varchar(30),
    apodo varchar(60),
    foto_url varchar(500),
    primary key (persona_id)
);
alter table competicion.perfil_deportivo add constraint perfil_deportivo_persona_id_fk
    foreign key (persona_id) references identidad.personas;

insert into competicion.perfil_deportivo (persona_id, altura_cm, pierna_habil, posicion, apodo, foto_url, created_at)
select p.id, j.altura_cm, j.pierna_habil, j.posicion, j.apodo, j.foto_url, j.created_at
from competicion.jugadores j
join identidad.personas p on p.numero_documento = j.cedula
where j.altura_cm is not null or j.pierna_habil is not null or j.posicion is not null
   or j.apodo is not null or j.foto_url is not null;


-- ─── 3. Equipos: delegado ────────────────────────────────────────────────────
-- Sin unique (plan §7): la regla "un delegado, un equipo base" sigue en el código.
alter table competicion.equipos add column delegado_persona_id uuid;
update competicion.equipos e set delegado_persona_id = p.id
from identidad.personas p where p.numero_documento = e.delegado_cedula;
alter table competicion.equipos alter column delegado_persona_id set not null;
alter table competicion.equipos drop column delegado_cedula;
alter table competicion.equipos add constraint equipos_delegado_persona_id_fk
    foreign key (delegado_persona_id) references identidad.personas;


-- ─── 4. Inscripciones: delegado y actores ────────────────────────────────────
alter table competicion.equipo_torneo
    add column delegado_persona_id uuid,
    add column aprobado_por_cuenta_id uuid,
    add column expulsado_por_cuenta_id uuid;
update competicion.equipo_torneo et set delegado_persona_id = p.id
from identidad.personas p where p.numero_documento = et.delegado_cedula;
update competicion.equipo_torneo et set aprobado_por_cuenta_id = c.id
from identidad.personas p join identidad.cuentas c on c.persona_id = p.id
where p.numero_documento = et.aprobado_por;
update competicion.equipo_torneo et set expulsado_por_cuenta_id = c.id
from identidad.personas p join identidad.cuentas c on c.persona_id = p.id
where p.numero_documento = et.expulsado_por;
alter table competicion.equipo_torneo alter column delegado_persona_id set not null;
alter table competicion.equipo_torneo
    drop column delegado_cedula,
    drop column aprobado_por,
    drop column expulsado_por;
alter table competicion.equipo_torneo
    add constraint equipo_torneo_torneo_delegado_uk unique (torneo_id, delegado_persona_id),
    add constraint equipo_torneo_delegado_persona_id_fk foreign key (delegado_persona_id) references identidad.personas,
    add constraint equipo_torneo_aprobado_por_cuenta_id_fk foreign key (aprobado_por_cuenta_id) references identidad.cuentas,
    add constraint equipo_torneo_expulsado_por_cuenta_id_fk foreign key (expulsado_por_cuenta_id) references identidad.cuentas;


-- ─── 5. Plantel ──────────────────────────────────────────────────────────────
alter table competicion.jugador_equipo add column persona_id uuid;
update competicion.jugador_equipo je set persona_id = p.id
from identidad.personas p where p.numero_documento = je.cedula;
alter table competicion.jugador_equipo alter column persona_id set not null;
alter table competicion.jugador_equipo drop column cedula;
alter table competicion.jugador_equipo
    add constraint jugador_equipo_persona_torneo_uk unique (persona_id, torneo_id),
    add constraint jugador_equipo_persona_id_fk foreign key (persona_id) references identidad.personas;


-- ─── 6. Alineación ───────────────────────────────────────────────────────────
alter table competicion.partido_jugador add column persona_id uuid;
update competicion.partido_jugador pj set persona_id = p.id
from identidad.personas p where p.numero_documento = pj.cedula;
alter table competicion.partido_jugador alter column persona_id set not null;
alter table competicion.partido_jugador drop column cedula;
alter table competicion.partido_jugador
    add constraint partido_jugador_partido_persona_uk unique (partido_id, persona_id),
    add constraint partido_jugador_persona_id_fk foreign key (persona_id) references identidad.personas;
create index idx_partido_jugador_persona on competicion.partido_jugador (persona_id);


-- ─── 7. Eventos (los goles de W.O. no tienen jugador) ────────────────────────
alter table competicion.eventos_partido add column persona_id uuid;
update competicion.eventos_partido ev set persona_id = p.id
from identidad.personas p where p.numero_documento = ev.cedula;
alter table competicion.eventos_partido drop column cedula;
alter table competicion.eventos_partido add constraint eventos_partido_persona_id_fk
    foreign key (persona_id) references identidad.personas;
create index idx_eventos_partido_persona on competicion.eventos_partido (persona_id);


-- ─── 8. Solicitudes de ingreso ───────────────────────────────────────────────
-- nombre y correo eran copias que nunca se leían: el nombre y el correo los tiene identidad.
alter table competicion.solicitudes_equipo add column persona_id uuid;
update competicion.solicitudes_equipo s set persona_id = p.id
from identidad.personas p where p.numero_documento = s.cedula;
alter table competicion.solicitudes_equipo alter column persona_id set not null;
alter table competicion.solicitudes_equipo
    drop column cedula,
    drop column nombre,
    drop column correo;
alter table competicion.solicitudes_equipo add constraint solicitudes_equipo_persona_id_fk
    foreign key (persona_id) references identidad.personas;
create index idx_solicitudes_equipo_persona_torneo on competicion.solicitudes_equipo (persona_id, torneo_id);


-- ─── 9. Premios individuales ─────────────────────────────────────────────────
alter table competicion.premio_asignado add column persona_id uuid;
update competicion.premio_asignado pa set persona_id = p.id
from identidad.personas p where p.numero_documento = pa.cedula;
alter table competicion.premio_asignado drop column cedula;
alter table competicion.premio_asignado add constraint premio_asignado_persona_id_fk
    foreign key (persona_id) references identidad.personas;


-- ─── 10. Multas: jugador sancionado y actores ────────────────────────────────
alter table finanzas.multas
    add column persona_id uuid,
    add column pagado_por_cuenta_id uuid,
    add column habilitado_por_cuenta_id uuid;
update finanzas.multas m set persona_id = p.id
from identidad.personas p where p.numero_documento = m.cedula;
update finanzas.multas m set pagado_por_cuenta_id = c.id
from identidad.personas p join identidad.cuentas c on c.persona_id = p.id
where p.numero_documento = m.pagado_por_cedula;
update finanzas.multas m set habilitado_por_cuenta_id = c.id
from identidad.personas p join identidad.cuentas c on c.persona_id = p.id
where p.numero_documento = m.habilitado_por_cedula;
alter table finanzas.multas alter column persona_id set not null;
alter table finanzas.multas
    drop column cedula,
    drop column pagado_por_cedula,
    drop column habilitado_por_cedula;
alter table finanzas.multas
    add constraint multas_persona_id_fk foreign key (persona_id) references identidad.personas,
    add constraint multas_pagado_por_cuenta_id_fk foreign key (pagado_por_cuenta_id) references identidad.cuentas,
    add constraint multas_habilitado_por_cuenta_id_fk foreign key (habilitado_por_cuenta_id) references identidad.cuentas;
create index idx_multas_persona_torneo_estado on finanzas.multas (persona_id, torneo_id, estado);


-- ─── 11. Comprobantes: quién lo subió y quién lo resolvió ────────────────────
-- aprobado_por se llenaba también al rechazar: pasa a resuelto_por_cuenta_id.
alter table finanzas.comprobantes
    add column subido_por_cuenta_id uuid,
    add column resuelto_por_cuenta_id uuid;
update finanzas.comprobantes co set subido_por_cuenta_id = c.id
from identidad.personas p join identidad.cuentas c on c.persona_id = p.id
where p.numero_documento = co.cedula_uploader;
update finanzas.comprobantes co set resuelto_por_cuenta_id = c.id
from identidad.personas p join identidad.cuentas c on c.persona_id = p.id
where p.numero_documento = co.aprobado_por;
alter table finanzas.comprobantes alter column subido_por_cuenta_id set not null;
alter table finanzas.comprobantes
    drop column cedula_uploader,
    drop column aprobado_por;
alter table finanzas.comprobantes
    add constraint comprobantes_subido_por_cuenta_id_fk foreign key (subido_por_cuenta_id) references identidad.cuentas,
    add constraint comprobantes_resuelto_por_cuenta_id_fk foreign key (resuelto_por_cuenta_id) references identidad.cuentas;
create index idx_comprobantes_subido_por on finanzas.comprobantes (subido_por_cuenta_id);


-- ─── 12. Snapshot de goleadores (se reconstruye: lo que no resuelva, se borra) ─
alter table analytics.snapshot_goleador add column persona_id uuid;
update analytics.snapshot_goleador s set persona_id = p.id
from identidad.personas p where p.numero_documento = s.cedula;
delete from analytics.snapshot_goleador where persona_id is null;
alter table analytics.snapshot_goleador alter column persona_id set not null;
alter table analytics.snapshot_goleador drop column cedula;
alter table analytics.snapshot_goleador add constraint snapshot_goleador_torneo_persona_uk
    unique (torneo_id, persona_id);


-- ─── 13. Fin de competicion.jugadores ────────────────────────────────────────
-- Sus FKs entrantes cayeron con las columnas de cédula.
drop table competicion.jugadores;
