-- ═════════════════════════════════════════════════════════════════════════════
-- V6 · Documento opcional y vinculación verificada (spec 0023, M2 parte 2)
--
-- 1. Cada torneo decide si exige documento. Por defecto no: en otros deportes no
--    se suele pedir (decisión del usuario, 8-nov-2026).
-- 2. Reclamar el historial de una persona sin cuenta ya no es automático: la cuenta
--    abre una solicitud de vínculo y la aprueba el delegado de un equipo donde esa
--    persona estuvo, o un admin.
-- ═════════════════════════════════════════════════════════════════════════════

alter table competicion.torneos add column exige_documento boolean not null default false;

create table identidad.vinculos_persona (
    creado_en timestamp(6) not null,
    resuelto_en timestamp(6),
    cuenta_id uuid not null,
    id uuid not null default gen_random_uuid(),
    persona_id uuid not null,
    resuelto_por_cuenta_id uuid,
    estado varchar(20) not null check (estado in ('PENDIENTE','APROBADA','RECHAZADA')),
    motivo_rechazo varchar(500),
    primary key (id)
);
alter table identidad.vinculos_persona
    add constraint vinculos_persona_cuenta_id_fk foreign key (cuenta_id) references identidad.cuentas,
    add constraint vinculos_persona_persona_id_fk foreign key (persona_id) references identidad.personas,
    add constraint vinculos_persona_resuelto_por_fk foreign key (resuelto_por_cuenta_id) references identidad.cuentas;

-- Una cuenta tiene a lo sumo una solicitud pendiente.
create unique index vinculos_persona_pendiente_uk
    on identidad.vinculos_persona (cuenta_id) where estado = 'PENDIENTE';
create index idx_vinculos_persona_persona_estado on identidad.vinculos_persona (persona_id, estado);
