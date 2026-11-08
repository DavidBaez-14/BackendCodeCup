package terminus.co.edu.ufps.codecup.identidad.api;

import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;

/**
 * El nombre de una persona en los endpoints públicos (plan §7, spec 0022).
 *
 * <p>Con tarjeta de identidad (TI, menor de edad) sale como "Nombres A.": los nombres y la
 * inicial del primer apellido. Si la persona no tiene apellidos aparte (un miembro sin cuenta,
 * cuyo nombre escribió el delegado en un solo campo), se toma la primera palabra y la inicial
 * de la siguiente. Con cualquier otro documento, o sin documento, sale el nombre completo.
 */
public final class NombreVisible {

    private NombreVisible() {
    }

    public static String de(TipoDocumento tipo, String nombres, String apellidos) {
        String n = limpio(nombres);
        String a = limpio(apellidos);
        if (tipo != TipoDocumento.TI) {
            if (n == null) return a;
            return a == null ? n : n + " " + a;
        }
        if (n == null) return a == null ? null : inicial(a);
        if (a != null) return n + " " + inicial(a);
        String[] palabras = n.split("\\s+");
        return palabras.length < 2 ? palabras[0] : palabras[0] + " " + inicial(palabras[1]);
    }

    private static String inicial(String palabra) {
        return palabra.substring(0, 1).toUpperCase() + ".";
    }

    private static String limpio(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
