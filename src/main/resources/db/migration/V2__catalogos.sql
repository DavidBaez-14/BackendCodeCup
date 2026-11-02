-- V2 · Catálogos fijos (spec 0017). Idempotente.
insert into competicion.premio_catalogo (codigo, nombre, descripcion) values
    ('CAMPEON',               'Campeón',               'Equipo ganador del torneo'),
    ('SUBCAMPEON',            'Subcampeón',            'Segundo puesto'),
    ('TERCERO',               'Tercer puesto',         'Tercer puesto deportivo'),
    ('GOLEADOR',              'Goleador',              'Máximo anotador del torneo'),
    ('PORTERO_MENOS_VENCIDO', 'Portero menos vencido', 'Portero del equipo con menor promedio GC'),
    ('MVP',                   'MVP',                   'Jugador más valioso (asignación manual)'),
    ('OTRO',                  'Otro',                  'Premio custom; usar campo titulo para diferenciar')
on conflict (codigo) do nothing;
