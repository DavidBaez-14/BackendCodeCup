-- ═════════════════════════════════════════════════════════════════════════════
-- V9 · Delegado sin rol global (spec 0026, M3 parte 3)
--
-- El delegado ya no necesita el rol global DELEGADO: lo es quien figura en
-- equipo_torneo.delegado_persona_id. Eso no pide cambios de esquema.
--
-- Camino (b) del plan §5.2: el admin crea el cupo de un equipo en su torneo e
-- invita al delegado. La invitación (ámbito TORNEO, rol DELEGADO) guarda el nombre
-- del equipo en dato_extra; al aceptarla se crean el equipo y la inscripción.
-- ═════════════════════════════════════════════════════════════════════════════

alter table identidad.invitaciones add column dato_extra varchar(150);
