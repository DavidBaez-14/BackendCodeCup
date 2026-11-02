package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.Locale;

public final class Documentos {

    private Documentos() {
    }

    /** "1.090 123" y "1090123" son el mismo documento. Los pasaportes llevan letras: se pasan a mayúsculas. */
    public static String normalizar(String numero) {
        if (numero == null) return null;
        String limpio = numero.replaceAll("[\\s.]", "").toUpperCase(Locale.ROOT);
        return limpio.isEmpty() ? null : limpio;
    }

    public static String normalizarCorreo(String correo) {
        if (correo == null || correo.isBlank()) return null;
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}
