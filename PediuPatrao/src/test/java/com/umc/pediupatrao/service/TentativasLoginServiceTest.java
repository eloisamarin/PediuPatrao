package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TentativasLoginServiceTest {

    // Relógio controlado pelo teste
    private static final class RelogioFalso extends Clock {
        private Instant agora = Instant.parse("2026-10-16T12:00:00Z");
        void avancar(Duration d) { agora = agora.plus(d); }
        @Override public Instant instant() { return agora; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    @Test
    void bloqueiaNaQuintaFalhaELiberaDepoisDoTempo() {
        RelogioFalso relogio = new RelogioFalso();
        TentativasLoginService service = new TentativasLoginService(relogio);

        for (int i = 1; i < TentativasLoginService.MAXIMO_FALHAS; i++) {
            assertFalse(service.registrarFalha("gerente"));
            assertFalse(service.estaBloqueado("gerente"));
        }
        assertTrue(service.registrarFalha("gerente"), "a 5ª falha deve bloquear");
        assertTrue(service.estaBloqueado("gerente"));
        assertTrue(service.estaBloqueado("GERENTE "), "não diferencia maiúsculas/espaços");
        assertFalse(service.estaBloqueado("atendente"), "bloqueio é por usuário");

        relogio.avancar(TentativasLoginService.TEMPO_BLOQUEIO.minusSeconds(1));
        assertTrue(service.estaBloqueado("gerente"));
        relogio.avancar(Duration.ofSeconds(2));
        assertFalse(service.estaBloqueado("gerente"), "bloqueio expira");
    }

    @Test
    void loginCorretoZeraContador() {
        TentativasLoginService service = new TentativasLoginService(new RelogioFalso());
        for (int i = 0; i < 4; i++) {
            service.registrarFalha("maria");
        }
        service.limpar("maria");
        for (int i = 0; i < 4; i++) {
            assertFalse(service.registrarFalha("maria"));
        }
        assertFalse(service.estaBloqueado("maria"));
    }

    @Test
    void falhasAntigasNaoSeAcumulam() {
        RelogioFalso relogio = new RelogioFalso();
        TentativasLoginService service = new TentativasLoginService(relogio);
        for (int i = 0; i < 4; i++) {
            service.registrarFalha("jose");
        }
        relogio.avancar(Duration.ofHours(1));
        assertFalse(service.registrarFalha("jose"), "depois da janela a contagem recomeça");
        assertFalse(service.estaBloqueado("jose"));
    }
}
