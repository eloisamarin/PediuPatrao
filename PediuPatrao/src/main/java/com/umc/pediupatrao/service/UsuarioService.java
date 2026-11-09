package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Cadastro e gestão de usuários e perfis. TODAS as operações exigem ADMIN (@PreAuthorize na classe),
 * mesmo que alguém chame a URL ou a API diretamente.
 *
 * Regras contra atribuição indevida de privilégios:
 * - só aceita os perfis ADMIN, GERENTE e ATENDENTE;
 * - o ADMIN não altera o próprio perfil nem se exclui;
 * - o sistema nunca fica sem ADMIN;
 * - ao mudar perfil/senha ou excluir, as sessões abertas daquele usuário são encerradas.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    // 3 a 30 caracteres: letras, números, ponto, hífen e sublinhado
    static final Pattern USERNAME_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");
    static final int SENHA_MINIMA = 8;
    static final int SENHA_MAXIMA_BYTES = 72; // limite do BCrypt: acima disso o restante seria ignorado
    static final Pattern EMAIL_VALIDO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final SessaoService sessaoService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService, SessaoService sessaoService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.sessaoService = sessaoService;
    }

    public Usuario buscarPorId(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    // Cria um novo usuário validando nome, senha e perfil; a senha é gravada só como hash BCrypt
    public Usuario criarUsuario(UsuarioForm dados) {
        String username = validarUsername(dados.getUsername());
        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com esse nome.");
        }
        validarSenha(dados.getPassword());
        Perfil perfil = perfilValido(dados.getRole());
        String email = emailDisponivel(dados.getEmail(), null);

        Usuario novo = new Usuario();
        novo.setUsername(username);
        novo.setPassword(passwordEncoder.encode(dados.getPassword()));
        novo.setRole(perfil.name());
        novo.setEmail(email);
        usuarioRepository.save(novo);

        auditoriaService.registrarAlteracao("CRIAR_USUARIO", "Usuario", novo.getId(), Alteracoes.nova()
                .sempre("username", null, username)
                .sempre("perfil", null, perfil.name())
                .se("email", null, email)
                .sempre("senha", null, "definida") // o AuditoriaService grava apenas [PROTEGIDO]
                .lista(), null, null);
        return novo;
    }

    // Atualiza perfil e/ou senha. O username não muda: ele identifica o usuário na auditoria.
    public Usuario atualizarUsuario(String id, UsuarioForm dados) {
        Usuario existente = buscarPorId(id);
        Perfil novoPerfil = perfilValido(dados.getRole());
        Optional<Perfil> perfilAtual = Perfil.de(existente.getRole());
        boolean mudouPerfil = !perfilAtual.equals(Optional.of(novoPerfil));
        boolean mudouSenha = dados.getPassword() != null && !dados.getPassword().isEmpty();

        if (mudouPerfil) {
            if (existente.getUsername().equals(usuarioLogado())) {
                throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
            }
            if (perfilAtual.orElse(null) == Perfil.ADMIN && usuarioRepository.countByRole(Perfil.ADMIN.name()) <= 1) {
                throw new RegraNegocioException("Não é possível remover o perfil do último ADMIN.");
            }
        }
        if (mudouSenha) {
            validarSenha(dados.getPassword());
        }
        String novoEmail = emailDisponivel(dados.getEmail(), existente.getId());
        boolean mudouEmail = !java.util.Objects.equals(novoEmail, existente.getEmail());

        // Só altera depois de todas as validações (nada é gravado pela metade)
        if (mudouPerfil) {
            auditoriaService.registrarAlteracao("ALTERAR_PERFIL", "Usuario", existente.getId(), Alteracoes.nova()
                    .sempre("perfil", perfilAtual.map(Enum::name).orElse(existente.getRole()), novoPerfil.name())
                    .lista(), null, "Usuário '" + existente.getUsername() + "'");
            existente.setRole(novoPerfil.name());
        }
        if (mudouSenha) {
            existente.setPassword(passwordEncoder.encode(dados.getPassword()));
            auditoriaService.registrarAlteracao("ALTERAR_SENHA", "Usuario", existente.getId(), Alteracoes.nova()
                    .sempre("senha", "anterior", "nova") // gravado como [PROTEGIDO] → [PROTEGIDO]
                    .lista(), null, "Usuário '" + existente.getUsername() + "'");
        }
        if (mudouEmail) {
            auditoriaService.registrarAlteracao("ALTERAR_EMAIL", "Usuario", existente.getId(), Alteracoes.nova()
                    .sempre("email", existente.getEmail(), novoEmail)
                    .lista(), null, "Usuário '" + existente.getUsername() + "'");
            existente.setEmail(novoEmail);
        }
        Usuario salvo = usuarioRepository.save(existente);

        // O usuário precisa entrar de novo para valer o novo perfil/senha (evita privilégio antigo na sessão aberta)
        if ((mudouPerfil || mudouSenha) && !existente.getUsername().equals(usuarioLogado())) {
            sessaoService.encerrarSessoesDe(existente.getUsername());
        }
        return salvo;
    }

    public void deletarUsuario(String id) {
        Usuario existente = buscarPorId(id);
        if (existente.getUsername().equals(usuarioLogado())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (Perfil.ADMIN.name().equals(existente.getRole()) && usuarioRepository.countByRole(Perfil.ADMIN.name()) <= 1) {
            throw new RegraNegocioException("Não é possível excluir o último ADMIN.");
        }
        usuarioRepository.deleteById(id);
        sessaoService.encerrarSessoesDe(existente.getUsername());
        auditoriaService.registrarAlteracao("EXCLUIR_USUARIO", "Usuario", id, Alteracoes.nova()
                .sempre("username", existente.getUsername(), null)
                .sempre("perfil", existente.getRole(), null)
                .lista(), null, null);
    }

    // ===== Validações =====

    /**
     * E-mail é opcional (sem ele o usuário só não consegue usar o "Esqueci a senha").
     * Quando informado: formato válido, gravado em minúsculo e único entre os usuários.
     */
    String emailDisponivel(String email, String idAtual) {
        String normalizado = validarEmail(email);
        if (normalizado == null) {
            return null;
        }
        usuarioRepository.findByEmail(normalizado)
                .filter(outro -> !outro.getId().equals(idAtual))
                .ifPresent(outro -> { throw new RegraNegocioException("Esse e-mail já está em uso por outro usuário."); });
        return normalizado;
    }

    static String validarEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalizado = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalizado.length() > 254 || !EMAIL_VALIDO.matcher(normalizado).matches()) {
            throw new RegraNegocioException("E-mail inválido.");
        }
        return normalizado;
    }

    static String validarUsername(String username) {
        String nome = username == null ? "" : username.trim();
        if (!USERNAME_VALIDO.matcher(nome).matches()) {
            throw new RegraNegocioException(
                    "Nome de usuário inválido: use de 3 a 30 caracteres (letras, números, ponto, hífen ou sublinhado).");
        }
        return nome;
    }

    static void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA) {
            throw new RegraNegocioException("A senha deve ter pelo menos " + SENHA_MINIMA + " caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > SENHA_MAXIMA_BYTES) {
            throw new RegraNegocioException("A senha deve ter no máximo " + SENHA_MAXIMA_BYTES + " caracteres.");
        }
        if (!senha.chars().anyMatch(Character::isLetter) || !senha.chars().anyMatch(Character::isDigit)) {
            throw new RegraNegocioException("A senha deve conter letras e números.");
        }
    }

    private static Perfil perfilValido(String role) {
        return Perfil.de(role)
                .orElseThrow(() -> new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE."));
    }

    private static String usuarioLogado() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
