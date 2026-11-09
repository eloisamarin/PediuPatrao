package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.CampoAlterado;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Monta a lista de campos alterados de uma operação auditada.
 *
 *   Alteracoes.nova()
 *       .se("logradouro", antigo.getLogradouro(), novo.getLogradouro())  // só entra se mudou
 *       .sempre("status", "RECEBIDO", "EM_PREPARACAO")                      // entra sempre
 *       .lista();
 */
public final class Alteracoes {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final List<CampoAlterado> campos = new ArrayList<>();

    private Alteracoes() {
    }

    public static Alteracoes nova() {
        return new Alteracoes();
    }

    // Registra o campo apenas se o valor mudou (vazio e nulo são tratados como iguais)
    public Alteracoes se(String campo, Object anterior, Object novo) {
        String a = texto(anterior);
        String n = texto(novo);
        if (!Objects.equals(a, n)) {
            campos.add(new CampoAlterado(campo, a, n));
        }
        return this;
    }

    // Registra o campo mesmo que o valor seja igual (ex.: inclusões)
    public Alteracoes sempre(String campo, Object anterior, Object novo) {
        campos.add(new CampoAlterado(campo, texto(anterior), texto(novo)));
        return this;
    }

    public List<CampoAlterado> lista() {
        return campos;
    }

    public boolean vazia() {
        return campos.isEmpty();
    }

    static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        String s;
        if (valor instanceof Double d) {
            s = String.format("%.2f", d);
        } else if (valor instanceof LocalDateTime dt) {
            s = dt.format(DATA_HORA);
        } else {
            s = String.valueOf(valor).trim();
        }
        return s.isEmpty() ? null : s;
    }
}
