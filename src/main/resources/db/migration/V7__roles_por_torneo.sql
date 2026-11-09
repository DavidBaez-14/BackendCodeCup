-- ═════════════════════════════════════════════════════════════════════════════
-- V7 · Roles por torneo (spec 0024, M3 parte 1)
--
-- No hay administradores globales: cada torneo tiene sus administradores en
-- competicion.torneo_roles. Quien crea un torneo queda como su ADMIN.
--
-- Migración de los roles globales (decisión Q1 de la 0024, opción a):
--   ADMINISTRADOR  →  ADMIN de todos los torneos que ya existen, y SUPERADMIN.
--                     SUPERADMIN solo abre el panel de Usuarios de la web; no da
--                     acceso a ningún torneo.
-- Los torneos que se creen después son solo de quien los cree.
--
-- Las invitaciones pasan a tener ámbito: una invitación de ámbito TORNEO hace al
-- invitado co-admin (o árbitro, desde la V8) de ese torneo y de ningún otro. Si no
-- lleva correo, la acepta cualquier cuenta que tenga el enlace (un solo uso).
-- ═════════════════════════════════════════════════════════════════════════════


-- ─── 1. Roles por torneo ─────────────────────────────────────────────────────
create table competicion.torneo_roles (
    creado_en timestamp(6) not null,
    creado_por_cuenta_id uuid,
    cuenta_id uuid not null,
    id uuid not null default gen_random_uuid(),
    torneo_id uuid not null,
    rol varchar(20) not null,
    primary key (id),
    constraint torneo_roles_torneo_cuenta_rol_uk unique (torneo_id, cuenta_id, rol),
    constraint torneo_roles_rol_check check (rol in ('ADMIN'))
);
alter table competicion.torneo_roles
    add constraint torneo_roles_torneo_id_fk foreign key (torneo_id) references competicion.torneos,
    add constraint torneo_roles_cuenta_id_fk foreign key (cuenta_id) references identidad.cuentas,
    add constraint torneo_roles_creado_por_fk foreign key (creado_por_cuenta_id) references identidad.cuentas;
create index idx_torneo_roles_cuenta_rol on competicion.torneo_roles (cuenta_id, rol);


-- ─── 2. SUPERADMIN ───────────────────────────────────────────────────────────
alter table identidad.cuenta_roles drop constraint cuenta_roles_rol_check;
alter table identidad.cuenta_roles add constraint cuenta_roles_rol_check
    check (rol in ('SUPERADMIN','ADMINISTRADOR','ARBITRO','DELEGADO','JUGADOR'));


-- ─── 3. Migración de los ADMINISTRADOR globales ──────────────────────────────
insert into identidad.cuenta_roles (cuenta_id, rol, estado, fecha_solicitud, fecha_resolucion, motivo_solicitud)
select cr.cuenta_id, 'SUPERADMIN', 'APROBADO', now(), now(), 'Migración M3 (spec 0024)'
from identidad.cuenta_roles cr
where cr.rol = 'ADMINISTRADOR' and cr.estado = 'APROBADO'
  and not exists (select 1 from identidad.cuenta_roles x
                  where x.cuenta_id = cr.cuenta_id and x.rol = 'SUPERADMIN');

insert into competicion.torneo_roles (torneo_id, cuenta_id, rol, creado_en)
select t.id, cr.cuenta_id, 'ADMIN', now()
from competicion.torneos t
cross join identidad.cuenta_roles cr
where cr.rol = 'ADMINISTRADOR' and cr.estado = 'APROBADO';


-- ─── 4. Invitaciones con ámbito ──────────────────────────────────────────────
alter table identidad.invitaciones alter column correo drop not null;
alter table identidad.invitaciones
    add column ambito varchar(20),
    add column ambito_id uuid,
    add column ambito_descripcion varchar(200);
alter table identidad.invitaciones
    add constraint invitaciones_ambito_check check (ambito in ('TORNEO')),
    add constraint invitaciones_ambito_completo_check
        check ((ambito is null and ambito_id is null) or (ambito is not null and ambito_id is not null)),
    -- Las invitaciones a la plataforma (sin ámbito) siguen yendo a un correo.
    add constraint invitaciones_correo_o_ambito_check check (correo is not null or ambito is not null);
create index idx_invitaciones_ambito on identidad.invitaciones (ambito, ambito_id, estado);
