package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.TokenRedefinicaoSenha;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.TokenRedefinicaoSenhaRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Regras do "Esqueci a senha": token seguro, validade, uso único e respostas que não revelam contas. */
class RecuperacaoSenhaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-08T15:00:00Z");

    private UsuarioRepository usuarios;
    private TokenRedefinicaoSenhaRepository tokens;
    private EmailService email;
    private AuditoriaService auditoria;
    private SessaoService sessoes;
    private TentativasLoginService tentativas;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final List<TokenRedefinicaoSenha> banco = new ArrayList<>();
    private Usuario joao;

    private RecuperacaoSenhaService servico(Instant agora) {
        return new RecuperacaoSenhaService(usuarios, tokens, encoder, email, auditoria, sessoes, tentativas,
                "https://pediupatrao.com.br/", Clock.fixed(agora, ZoneOffset.UTC));
    }

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        tokens = mock(TokenRedefinicaoSenhaRepository.class);
        email = mock(EmailService.class);
        auditoria = mock(AuditoriaService.class);
        sessoes = mock(SessaoService.class);
        tentativas = new TentativasLoginService();
        banco.clear();

        joao = new Usuario();
        joao.setId("u1");
        joao.setUsername("joao");
        joao.setEmail("joao@pizzaria.com");
        joao.setPassword(encoder.encode("SenhaAntiga1"));
        joao.setRole("ATENDENTE");
        when(usuarios.findByUsername("joao")).thenReturn(Optional.of(joao));
        when(usuarios.findByEmail("joao@pizzaria.com")).thenReturn(Optional.of(joao));
        when(usuarios.findById("u1")).thenReturn(Optional.of(joao));

        // "Banco" de tokens em memória
        when(tokens.save(any())).thenAnswer(inv -> { banco.add(inv.getArgument(0)); return inv.getArgument(0); });
        when(tokens.findByUsuarioId("u1")).thenAnswer(inv -> List.copyOf(banco));
        when(tokens.findByTokenHash(anyString())).thenAnswer(inv ->
                banco.stream().filter(t -> t.getTokenHash().equals(inv.getArgument(0))).findFirst());
        org.mockito.Mockito.doAnswer(inv -> { banco.clear(); return null; }).when(tokens).deleteByUsuarioId("u1");
    }

    /** Pede o link e devolve o token que foi enviado por e-mail. */
    private String pedirLink(String identificador) {
        servico(AGORA).solicitar(identificador);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(email).enviarRedefinicaoSenha(eq("joao@pizzaria.com"), eq("joao"), link.capture(), eq(30L));
        return link.getValue().substring(link.getValue().indexOf("token=") + 6);
    }

    @Test
    void solicitar_porUsuarioOuEmail_enviaLinkComUrlConfiguradaEValidade30Min() {
        String token = pedirLink("joao");
        assertEquals(43, token.length()); // 32 bytes em Base64 URL = 256 bits
        assertEquals(AGORA.plusSeconds(30 * 60), banco.get(0).getExpiraEm());

        setUp();
        String token2 = pedirLink("  JOAO@pizzaria.com ");
        assertNotEquals(token, token2);
    }

    @Test
    void solicitar_linkUsaUrlDaConfiguracao() {
        servico(AGORA).solicitar("joao");
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(email).enviarRedefinicaoSenha(anyString(), anyString(), link.capture(), anyLong());
        assertTrue(link.getValue().startsWith("https://pediupatrao.com.br/redefinir-senha?token="));
    }

    @Test
    void solicitar_bancoGuardaSoOHashDoToken() {
        String token = pedirLink("joao");
        TokenRedefinicaoSenha gravado = banco.get(0);
        assertNotEquals(token, gravado.getTokenHash());
        assertEquals(RecuperacaoSenhaService.hash(token), gravado.getTokenHash());
        assertEquals(64, gravado.getTokenHash().length()); // SHA-256 em hexadecimal
    }

    @Test
    void solicitar_usuarioInexistenteOuSemEmail_naoEnviaENaoDaErro() {
        servico(AGORA).solicitar("fulano");
        servico(AGORA).solicitar("ninguem@x.com");
        servico(AGORA).solicitar("");
        joao.setEmail(null);
        servico(AGORA).solicitar("joao");

        verify(email, never()).enviarRedefinicaoSenha(anyString(), anyString(), anyString(), anyLong());
        assertTrue(banco.isEmpty());
    }

    @Test
    void solicitar_duasVezesEmMenosDe2Minutos_naoReenvia() {
        pedirLink("joao");
        servico(AGORA.plusSeconds(60)).solicitar("joao");
        verify(email, org.mockito.Mockito.times(1)).enviarRedefinicaoSenha(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void solicitar_novoPedidoInvalidaOLinkAnterior() {
        String primeiro = pedirLink("joao");
        servico(AGORA.plusSeconds(180)).solicitar("joao");
        assertFalse(servico(AGORA.plusSeconds(181)).tokenValido(primeiro));
        assertEquals(1, banco.size());
    }

    @Test
    void redefinir_comLinkValido_trocaSenhaLiberaBloqueioEncerraSessoes() {
        String token = pedirLink("joao");
        for (int i = 0; i < 5; i++) tentativas.registrarFalha("joao");
        assertTrue(tentativas.estaBloqueado("joao"));

        servico(AGORA.plusSeconds(600)).redefinir(token, "NovaSenha2026", "NovaSenha2026");

        assertTrue(encoder.matches("NovaSenha2026", joao.getPassword()));
        assertFalse(tentativas.estaBloqueado("joao"));
        verify(sessoes).encerrarSessoesDe("joao");
        verify(auditoria).registrar(eq("joao"), eq("-"), eq("SENHA_REDEFINIDA"), eq("Usuario"), eq("u1"), anyString());
    }

    @Test
    void redefinir_linkSoPodeSerUsadoUmaVez() {
        String token = pedirLink("joao");
        servico(AGORA).redefinir(token, "NovaSenha2026", "NovaSenha2026");
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico(AGORA).redefinir(token, "OutraSenha99", "OutraSenha99"));
        assertEquals(RecuperacaoSenhaService.LINK_INVALIDO, e.getMessage());
    }

    @Test
    void redefinir_linkExpiradoDepoisDe30Minutos() {
        String token = pedirLink("joao");
        assertTrue(servico(AGORA.plusSeconds(29 * 60)).tokenValido(token));
        assertFalse(servico(AGORA.plusSeconds(30 * 60)).tokenValido(token));
        assertThrows(RegraNegocioException.class,
                () -> servico(AGORA.plusSeconds(31 * 60)).redefinir(token, "NovaSenha2026", "NovaSenha2026"));
        assertTrue(encoder.matches("SenhaAntiga1", joao.getPassword()));
    }

    @Test
    void redefinir_tokenInventado_eRecusado() {
        assertFalse(servico(AGORA).tokenValido("abc"));
        assertFalse(servico(AGORA).tokenValido(null));
        assertThrows(RegraNegocioException.class, () -> servico(AGORA).redefinir("abc", "NovaSenha2026", "NovaSenha2026"));
    }

    @Test
    void redefinir_senhaFracaOuConfirmacaoDiferente_naoAlteraEMantemLinkValido() {
        String token = pedirLink("joao");
        assertEquals("A confirmação não confere com a nova senha.", assertThrows(RegraNegocioException.class,
                () -> servico(AGORA).redefinir(token, "NovaSenha2026", "NovaSenha2027")).getMessage());
        assertThrows(RegraNegocioException.class, () -> servico(AGORA).redefinir(token, "curta1", "curta1"));
        assertThrows(RegraNegocioException.class, () -> servico(AGORA).redefinir(token, "semnumeros", "semnumeros"));

        assertTrue(encoder.matches("SenhaAntiga1", joao.getPassword()));
        assertTrue(servico(AGORA).tokenValido(token)); // pode tentar de novo com uma senha válida
        verify(usuarios, never()).save(any());
    }

    @Test
    void mascararEmailParaAuditoria() {
        assertEquals("jo***@pizzaria.com", RecuperacaoSenhaService.mascarar("joao@pizzaria.com"));
        assertEquals("a***@x.com", RecuperacaoSenhaService.mascarar("a@x.com"));
    }
}
