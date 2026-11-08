-- V4 · Un torneo no repite premios del catálogo (bug C5).
--
-- 1. Un premio por tipo en cada torneo: dos CAMPEON, no.
-- 2. OTRO sí se repite, porque cada uno lleva su título, pero dos OTRO del mismo torneo no comparten
--    título. Se compara sin mayúsculas ni espacios de los extremos, como en PremioService.
--
-- PremioService valida lo mismo antes de guardar y responde 409 PREMIO_DUPLICADO. Estos índices cubren lo
-- que esa validación no alcanza a ver, como dos clics seguidos en "Agregar" (sale como 409 CONFLICTO_DATOS).
--
-- El id de OTRO lo generó V2 con gen_random_uuid(), así que cambia de una base a otra, y el WHERE de un
-- índice parcial no admite subconsultas: por eso se busca aquí y queda escrito como literal. Ese id no
-- cambia: el catálogo es fijo y torneo_premio lo referencia con una FK.
--
-- Al escribir esta migración, torneo_premio estaba vacía en Supabase #1. Si tuviera duplicados, el índice
-- no se crearía y la migración fallaría entera: habría que limpiarlos antes, no borrar nada desde aquí.
do $$
declare
    otro uuid;
begin
    select id into strict otro from competicion.premio_catalogo where codigo = 'OTRO';

    execute format(
        'create unique index torneo_premio_tipo_uk on competicion.torneo_premio (torneo_id, premio_id) '
            || 'where premio_id <> %L::uuid',
        otro);

    execute format(
        'create unique index torneo_premio_otro_titulo_uk on competicion.torneo_premio (torneo_id, lower(btrim(titulo))) '
            || 'where premio_id = %L::uuid',
        otro);
end
$$;
