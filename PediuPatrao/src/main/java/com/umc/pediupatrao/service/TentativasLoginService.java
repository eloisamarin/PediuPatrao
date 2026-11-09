package com.umc.pediupatrao.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Proteção contra tentativa de adivinhar senhas (força bruta): depois de MAXIMO_FALHAS logins errados
 * seguidos para o mesmo usuário, ele fica bloqueado por TEMPO_BLOQUEIO. Um login correto zera o contador.
 * A partir de FALHAS_PARA_SUGERIR_REDEFINICAO, a tela de login sugere usar o "Esqueci a senha".
 *
 * Fica em memória (reiniciar o sistema libera os bloqueios), o que é suficiente para uma instância única.
 */
@Service
public class TentativasLoginService {

    public static final int MAXIMO_FALHAS = 5;
    public static final int FALHAS_PARA_SUGERIR_REDEFINICAO = 3;
    public static final Duration TEMPO_BLOQUEIO = Duration.ofMinutes(15);

    private record Situacao(int falhas, Instant ultimaFalha, Instant bloqueadoAte) { }

    private final Map<String, Situacao> situacoes = new ConcurrentHashMap<>();
    private final Clock relogio;

    public TentativasLoginService() {
        this(Clock.systemDefaultZone());
    }

    TentativasLoginService(Clock relogio) { // usado nos testes
        this.relogio = relogio;
    }

    /** Registra uma falha. Retorna true se, com ela, o usuário acabou de ser bloqueado. */
    public boolean registrarFalha(String username) {
        String chave = chave(username);
        if (chave.isEmpty()) {
            return false;
        }
        Instant agora = relogio.instant();
        Situacao nova = situacoes.compute(chave, (k, atual) -> {
            // Falhas antigas (fora da janela) não contam mais
            int falhas = (atual == null || atual.ultimaFalha().plus(TEMPO_BLOQUEIO).isBefore(agora)) ? 1 : atual.falhas() + 1;
            Instant bloqueadoAte = falhas >= MAXIMO_FALHAS ? agora.plus(TEMPO_BLOQUEIO) : null;
            return new Situacao(falhas, agora, bloqueadoAte);
        });
        limparExpirados(agora);
        return nova.falhas() == MAXIMO_FALHAS;
    }

    /** Quantas falhas seguidas (dentro da janela de TEMPO_BLOQUEIO) o usuário tem agora. */
    public int falhas(String username) {
        Situacao s = situacoes.get(chave(username));
        if (s == null || s.ultimaFalha().plus(TEMPO_BLOQUEIO).isBefore(relogio.instant())) {
            return 0;
        }
        return s.falhas();
    }

    public boolean estaBloqueado(String username) {
        Situacao s = situacoes.get(chave(username));
        return s != null && s.bloqueadoAte() != null && relogio.instant().isBefore(s.bloqueadoAte());
    }

    public void limpar(String username) {
        situacoes.remove(chave(username));
    }

    private static String chave(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    // Evita que o mapa cresça sem limite com nomes de usuário inventados
    private void limparExpirados(Instant agora) {
        if (situacoes.size() > 1000) {
            situacoes.entrySet().removeIf(e -> e.getValue().ultimaFalha().plus(TEMPO_BLOQUEIO).isBefore(agora));
        }
    }
}
