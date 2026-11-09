package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.TokenRedefinicaoSenha;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.TokenRedefinicaoSenhaRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.SegurancaUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * "Esqueci a senha": gera um link de uso único enviado por e-mail e redefine a senha.
 *
 * Regras de segurança:
 *  - A resposta para quem pede é SEMPRE a mesma, exista ou não o usuário/e-mail (não revela quem tem conta).
 *  - O token tem 256 bits aleatórios (SecureRandom); no banco fica só o hash SHA-256.
 *  - Validade de 30 minutos e uso único; pedir de novo invalida o link anterior.
 *  - Contra abuso: um novo e-mail para o mesmo usuário só depois de 2 minutos.
 *  - O endereço do link vem da configuração (pediupatrao.url-base), nunca do cabeçalho Host da requisição,
 *    para que ninguém consiga gerar e-mails com links apontando para outro site.
 *  - Ao redefinir: senha validada pelas mesmas regras do cadastro, bloqueio de tentativas liberado,
 *    sessões abertas do usuário encerradas e tudo registrado na auditoria (sem a senha).
 *
 * Estas operações são públicas (a pessoa não está logada), por isso não têm @PreAuthorize.
 */
@Service
public class RecuperacaoSenhaService {

    public static final Duration VALIDADE = Duration.ofMinutes(30);
    static final Duration INTERVALO_MINIMO = Duration.ofMinutes(2);
    static final String LINK_INVALIDO = "Este link é inválido, já foi usado ou expirou. Peça um novo link.";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final TokenRedefinicaoSenhaRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditoriaService auditoriaService;
    private final SessaoService sessaoService;
    private final TentativasLoginService tentativasLogin;
    private final String urlBase;
    private final Clock relogio;

    @Autowired
    public RecuperacaoSenhaService(UsuarioRepository usuarioRepository, TokenRedefinicaoSenhaRepository tokenRepository,
            PasswordEncoder passwordEncoder, EmailService emailService, AuditoriaService auditoriaService,
            SessaoService sessaoService, TentativasLoginService tentativasLogin,
            @Value("${pediupatrao.url-base:http://localhost:8080}") String urlBase) {
        this(usuarioRepository, tokenRepository, passwordEncoder, emailService, auditoriaService, sessaoService,
                tentativasLogin, urlBase, Clock.systemUTC());
    }

    RecuperacaoSenhaService(UsuarioRepository usuarioRepository, TokenRedefinicaoSenhaRepository tokenRepository,
            PasswordEncoder passwordEncoder, EmailService emailService, AuditoriaService auditoriaService,
            SessaoService sessaoService, TentativasLoginService tentativasLogin, String urlBase, Clock relogio) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditoriaService = auditoriaService;
        this.sessaoService = sessaoService;
        this.tentativasLogin = tentativasLogin;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.relogio = relogio;
    }

    /**
     * Pedido de redefinição a partir do nome de usuário ou do e-mail.
     * Não lança erro nem devolve nada que revele se a conta existe.
     */
    public void solicitar(String identificador) {
        String informado = SegurancaUtil.textoSeguro(identificador);
        Optional<Usuario> encontrado = buscarUsuario(identificador);

        if (encontrado.isEmpty() || encontrado.get().getEmail() == null || encontrado.get().getEmail().isBlank()) {
            auditoriaService.registrar(informado, "-", "SENHA_RECUPERACAO_IGNORADA", "Usuario", null,
                    "Pedido de redefinição para usuário inexistente ou sem e-mail cadastrado");
            return;
        }
        Usuario usuario = encontrado.get();
        Instant agora = relogio.instant();

        boolean pedidoRecente = tokenRepository.findByUsuarioId(usuario.getId()).stream()
                .anyMatch(t -> t.isValido(agora) && t.getCriadoEm().plus(INTERVALO_MINIMO).isAfter(agora));
        if (pedidoRecente) {
            auditoriaService.registrar(usuario.getUsername(), "-", "SENHA_RECUPERACAO_IGNORADA", "Usuario", usuario.getId(),
                    "Novo pedido em menos de " + INTERVALO_MINIMO.toMinutes() + " minutos; e-mail não reenviado");
            return;
        }

        // Um link por vez: o pedido novo invalida os anteriores
        tokenRepository.deleteByUsuarioId(usuario.getId());
        String token = gerarToken();
        TokenRedefinicaoSenha registro = new TokenRedefinicaoSenha();
        registro.setUsuarioId(usuario.getId());
        registro.setTokenHash(hash(token));
        registro.setCriadoEm(agora);
        registro.setExpiraEm(agora.plus(VALIDADE));
        tokenRepository.save(registro);

        emailService.enviarRedefinicaoSenha(usuario.getEmail(), usuario.getUsername(),
                urlBase + "/redefinir-senha?token=" + token, VALIDADE.toMinutes());
        auditoriaService.registrar(usuario.getUsername(), "-", "SENHA_RECUPERACAO_SOLICITADA", "Usuario", usuario.getId(),
                "Link de redefinição enviado para " + mascarar(usuario.getEmail()));
    }

    /** O link ainda pode ser usado? (para a tela decidir se mostra o formulário ou o aviso de link inválido) */
    public boolean tokenValido(String token) {
        return buscarTokenValido(token).isPresent();
    }

    /** Define a nova senha a partir de um link válido. */
    public void redefinir(String token, String novaSenha, String confirmacao) {
        TokenRedefinicaoSenha registro = buscarTokenValido(token)
                .orElseThrow(() -> new RegraNegocioException(LINK_INVALIDO));
        if (novaSenha == null || !novaSenha.equals(confirmacao)) {
            throw new RegraNegocioException("A confirmação não confere com a nova senha.");
        }
        UsuarioService.validarSenha(novaSenha); // mesmas regras do cadastro: 8+ caracteres, letras e números

        Usuario usuario = usuarioRepository.findById(registro.getUsuarioId())
                .orElseThrow(() -> new RegraNegocioException(LINK_INVALIDO));
        usuario.setPassword(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

        tokenRepository.deleteByUsuarioId(usuario.getId()); // uso único: nenhum link desse usuário vale mais
        tentativasLogin.limpar(usuario.getUsername());       // libera o bloqueio por senhas erradas
        sessaoService.encerrarSessoesDe(usuario.getUsername()); // quem estiver logado com a senha antiga sai
        auditoriaService.registrar(usuario.getUsername(), "-", "SENHA_REDEFINIDA", "Usuario", usuario.getId(),
                "Senha redefinida pelo link enviado por e-mail");
    }

    // ===================== Auxiliares =====================

    private Optional<Usuario> buscarUsuario(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return Optional.empty();
        }
        String valor = identificador.trim();
        if (valor.contains("@")) {
            return usuarioRepository.findByEmail(valor.toLowerCase(Locale.ROOT));
        }
        return usuarioRepository.findByUsername(valor);
    }

    private Optional<TokenRedefinicaoSenha> buscarTokenValido(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        Instant agora = relogio.instant();
        return tokenRepository.findByTokenHash(hash(token.trim())).filter(t -> t.isValido(agora));
    }

    private static String gerarToken() {
        byte[] bytes = new byte[32]; // 256 bits
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** "joao.silva@pizzaria.com" → "jo***@pizzaria.com" (para a auditoria não expor o e-mail inteiro). */
    static String mascarar(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, arroba)) + "***" + email.substring(arroba);
    }
}
