-- ═════════════════════════════════════════════════════════════════════════════
-- V8 · Árbitros por partido (spec 0025, M3 parte 2)
--
-- Los árbitros se asignan por partido. Un árbitro del torneo solo opera todos los
-- partidos si el admin le da el permiso todos_los_partidos.
--
-- Migración del rol global (decisión Q1 de la 0025, opción a):
--   ARBITRO  →  árbitro de todos los torneos que ya existen, con todos_los_partidos,
--               que es lo que podía hacer hasta hoy. Los torneos nuevos los invitan.
--
-- Además, el partido guarda dónde se juega (lugar), para la lista del árbitro.
-- ═════════════════════════════════════════════════════════════════════════════


-- ─── 1. torneo_roles admite ARBITRO y su permiso total ───────────────────────
alter table competicion.torneo_roles drop constraint torneo_roles_rol_check;
alter table competicion.torneo_roles add constraint torneo_roles_rol_check check (rol in ('ADMIN','ARBITRO'));
alter table competicion.torneo_roles add column todos_los_partidos boolean not null default false;


-- ─── 2. Árbitros asignados a cada partido ────────────────────────────────────
create table competicion.partido_arbitros (
    asignado_en timestamp(6) not null,
    asignado_por_cuenta_id uuid,
    cuenta_id uuid not null,
    id uuid not null default gen_random_uuid(),
    partido_id uuid not null,
    primary key (id),
    constraint partido_arbitros_partido_cuenta_uk unique (partido_id, cuenta_id)
);
alter table competicion.partido_arbitros
    add constraint partido_arbitros_partido_id_fk foreign key (partido_id) references competicion.partidos,
    add constraint partido_arbitros_cuenta_id_fk foreign key (cuenta_id) references identidad.cuentas,
    add constraint partido_arbitros_asignado_por_fk foreign key (asignado_por_cuenta_id) references identidad.cuentas;
create index idx_partido_arbitros_cuenta on competicion.partido_arbitros (cuenta_id);


-- ─── 3. Lugar del partido ────────────────────────────────────────────────────
alter table competicion.partidos add column lugar varchar(120);


-- ─── 4. Migración de los ARBITRO globales ────────────────────────────────────
insert into competicion.torneo_roles (torneo_id, cuenta_id, rol, todos_los_partidos, creado_en)
select t.id, cr.cuenta_id, 'ARBITRO', true, now()
from competicion.torneos t
cross join identidad.cuenta_roles cr
where cr.rol = 'ARBITRO' and cr.estado = 'APROBADO'
  and not exists (select 1 from competicion.torneo_roles r
                  where r.torneo_id = t.id and r.cuenta_id = cr.cuenta_id and r.rol = 'ARBITRO');
