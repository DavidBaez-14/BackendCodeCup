-- Spec 0021: borra las tablas del stack viejo (AuthCodeCup / Appwrite) que quedaron en public de Supabase #1.
-- Ningún código de BackendCodeCup las usa, ninguna tabla de los schemas nuevos las referencia y desde el
-- 2-nov-2026 tenían RLS sin políticas. Sus datos eran de prueba en su mayoría; el usuario decidió no exportarlos.
-- IF EXISTS: en H2 y en el Postgres embebido de los tests (o en una BD nueva) no existen.

DROP TABLE IF EXISTS public.cuenta_roles;
DROP TABLE IF EXISTS public.cuentas;
DROP TABLE IF EXISTS public.jugadores;
