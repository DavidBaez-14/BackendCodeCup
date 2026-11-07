package terminus.co.edu.ufps.codecup.seed;

import java.util.ArrayList;
import java.util.List;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/**
 * El "mundo" del seed (spec 0019): 30 usuarios @prueba.local y el reparto de equipos.
 *
 * <p>Los documentos empiezan por 900000 para reconocerlos de un vistazo:
 * admin 9000000001, árbitros 90000001xx, delegados 90000002xx y jugadores 90000003xx.
 */
final class DatosDePrueba {

    static final String DOMINIO = "@prueba.local";
    static final String TORNEO_INSCRIPCIONES = "Copa Prueba 2026";
    static final String TORNEO_ARBITRO = "Liga Relámpago (prueba)";

    /** Perfil deportivo. null = sin perfil (prueba que el perfil es opcional, spec 0020). */
    record Perfil(int alturaCm, String piernaHabil, String posicion) {
    }

    record UsuarioPrueba(String alias, String nombres, String apellidos, String documento,
                         List<Rol> roles, Perfil perfil) {
        String correo() {
            return alias + DOMINIO;
        }

        String nombreCompleto() {
            return nombres + " " + apellidos;
        }
    }

    /** Equipo base de un delegado y quiénes están en su plantel (alias de jugadores). */
    record EquipoPrueba(String nombre, String aliasDelegado, List<String> plantel) {
    }

    private static final String[] NOMBRES = {
        "Juan", "Sofía", "Mateo", "Isabella", "Sebastián", "Mariana", "Nicolás", "Gabriela", "Daniel", "Paula",
        "Alejandro", "Natalia", "David", "Sara", "Felipe", "Juliana", "Tomás", "Daniela", "Samuel", "Lucía",
        "Miguel", "Valeria", "Esteban",
    };
    private static final String[] APELLIDOS = {
        "Castro", "Moreno", "Jiménez", "Rincón", "Pardo", "Suárez", "Ortiz", "Duarte", "Peña", "Silva",
        "Cárdenas", "Ospina", "Mejía", "Salazar", "Rubio", "Acosta", "Cruz", "Navarro", "Quintero", "Becerra",
        "Fajardo", "Ibáñez", "León",
    };
    private static final String[] POSICIONES = {"PORTERO", "DEFENSA", "DEFENSA", "MEDIOCAMPISTA", "MEDIOCAMPISTA", "DELANTERO"};
    private static final String[] PIERNAS = {"DERECHA", "DERECHA", "IZQUIERDA", "AMBIDIESTRO"};

    private DatosDePrueba() {
    }

    static List<UsuarioPrueba> usuarios() {
        List<UsuarioPrueba> lista = new ArrayList<>();
        lista.add(new UsuarioPrueba("admin01", "Laura", "Méndez", "9000000001",
                List.of(Rol.ADMINISTRADOR, Rol.JUGADOR), perfil(0)));
        lista.add(new UsuarioPrueba("arbitro01", "Carlos", "Ramírez", "9000000101",
                List.of(Rol.ARBITRO, Rol.JUGADOR), perfil(1)));
        lista.add(new UsuarioPrueba("arbitro02", "Diana", "Torres", "9000000102",
                List.of(Rol.ARBITRO, Rol.JUGADOR), perfil(2)));
        String[][] delegados = {{"Andrés", "Gómez"}, {"Valentina", "Ruiz"}, {"Santiago", "Herrera"}, {"Camila", "Vargas"}};
        for (int i = 0; i < delegados.length; i++) {
            lista.add(new UsuarioPrueba(String.format("delegado%02d", i + 1), delegados[i][0], delegados[i][1],
                    String.format("90000002%02d", i + 1), List.of(Rol.DELEGADO, Rol.JUGADOR), perfil(3 + i)));
        }
        for (int i = 0; i < NOMBRES.length; i++) {
            int numero = i + 1;
            // jugador22 y jugador23 quedan sin perfil deportivo: prueban que es opcional (spec 0020).
            Perfil perfil = numero >= 22 ? null : perfil(10 + i);
            lista.add(new UsuarioPrueba(String.format("jugador%02d", numero), NOMBRES[i], APELLIDOS[i],
                    String.format("90000003%02d", numero), List.of(Rol.JUGADOR), perfil));
        }
        return lista;
    }

    /** Los 4 equipos de los delegados. jugador14 a jugador23 quedan libres para pedir unirse. */
    static List<EquipoPrueba> equipos() {
        return List.of(
                new EquipoPrueba("Halcones FC", "delegado01", jugadores(1, 5)),
                new EquipoPrueba("Tiburones FC", "delegado02", jugadores(6, 10)),
                new EquipoPrueba("Cóndores FC", "delegado03", jugadores(11, 12)),
                new EquipoPrueba("Pumas FC", "delegado04", jugadores(13, 13)));
    }

    private static List<String> jugadores(int desde, int hasta) {
        List<String> alias = new ArrayList<>();
        for (int n = desde; n <= hasta; n++) alias.add(String.format("jugador%02d", n));
        return alias;
    }

    private static Perfil perfil(int i) {
        return new Perfil(165 + (i * 7) % 26, PIERNAS[i % PIERNAS.length], POSICIONES[i % POSICIONES.length]);
    }
}
