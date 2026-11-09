package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Auditoria persistente (RF05).
 *
 * - Grava com insert (nunca update) e não oferece exclusão: os registros não podem ser alterados.
 * - Consulta só para ADMIN e GERENTE.
 * - Nunca grava senhas, tokens ou credenciais: campos com esses nomes têm o valor substituído.
 * - Minimiza dados pessoais: telefones são gravados mascarados (só os 4 últimos dígitos),
 *   o suficiente para a finalidade da auditoria (saber que o telefone mudou e de qual para qual).
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    static final String PROTEGIDO = "[PROTEGIDO]";
    private static final Set<String> CAMPOS_SENSIVEIS = Set.of("senha", "password", "token", "credencial", "_csrf", "secret");
    private static final Set<String> CAMPOS_TELEFONE = Set.of("telefone", "celular", "fone");

    private final AuditoriaRepository auditoriaRepository;
    private final MongoTemplate mongoTemplate;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, MongoTemplate mongoTemplate) {
        this.auditoriaRepository = auditoriaRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // ========================
    // GRAVAÇÃO
    // ========================

    // Operação sem campos alterados (ex.: login, acesso negado)
    public void registrar(String acao, String entidade, String entidadeId, String detalhes) {
        registrarAlteracao(acao, entidade, entidadeId, List.of(), null, detalhes);
    }

    // Operação do usuário logado, com os campos alterados (anterior → novo) e a justificativa, se houver
    public void registrarAlteracao(String acao, String entidade, String entidadeId, List<CampoAlterado> campos,
            String justificativa, String detalhes) {
        String[] quem = identificar(SecurityContextHolder.getContext().getAuthentication());
        gravar(quem[0], quem[1], acao, entidade, entidadeId, campos, justificativa, detalhes);
    }

    // A partir de uma autenticação específica (ex.: no momento do login)
    public void registrar(Authentication auth, String acao, String entidade, String entidadeId, String detalhes) {
        String[] quem = identificar(auth);
        gravar(quem[0], quem[1], acao, entidade, entidadeId, List.of(), null, detalhes);
    }

    // Quando ainda não há usuário autenticado (ex.: login sem sucesso)
    public void registrar(String usuario, String perfil, String acao, String entidade, String entidadeId, String detalhes) {
        gravar(usuario, perfil, acao, entidade, entidadeId, List.of(), null, detalhes);
    }

    private void gravar(String usuario, String perfil, String acao, String entidade, String entidadeId,
            List<CampoAlterado> campos, String justificativa, String detalhes) {
        List<CampoAlterado> protegidos = campos == null ? List.of() : campos.stream()
                .map(AuditoriaService::proteger)
                .toList();
        Auditoria registro = new Auditoria(LocalDateTime.now(), usuario, perfil, acao, entidade, entidadeId,
                protegidos, justificativa, detalhes);
        try {
            mongoTemplate.insert(registro); // insert: falha se o id já existir, nunca sobrescreve
        } catch (RuntimeException e) {
            // A falha da auditoria não deve derrubar a operação principal, mas fica no log (sem os valores)
            log.error("Falha ao gravar auditoria: {} {} {}", acao, entidade, entidadeId, e);
        }
    }

    // Substitui credenciais e mascara telefones antes de gravar
    static CampoAlterado proteger(CampoAlterado c) {
        String nome = c.getCampo() == null ? "" : c.getCampo().toLowerCase(Locale.ROOT);
        if (CAMPOS_SENSIVEIS.stream().anyMatch(nome::contains)) {
            return new CampoAlterado(c.getCampo(),
                    c.getValorAnterior() == null ? null : PROTEGIDO,
                    c.getValorNovo() == null ? null : PROTEGIDO);
        }
        if (CAMPOS_TELEFONE.stream().anyMatch(nome::contains)) {
            return new CampoAlterado(c.getCampo(), mascararTelefone(c.getValorAnterior()), mascararTelefone(c.getValorNovo()));
        }
        return c;
    }

    // "(11) 98765-4321" → "(**) *****-4321": mantém só os 4 últimos dígitos
    static String mascararTelefone(String telefone) {
        if (telefone == null) {
            return null;
        }
        long totalDigitos = telefone.chars().filter(Character::isDigit).count();
        StringBuilder sb = new StringBuilder();
        long vistos = 0;
        for (char ch : telefone.toCharArray()) {
            if (Character.isDigit(ch)) {
                vistos++;
                sb.append(vistos > totalDigitos - 4 ? ch : '*');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // [usuário, perfil] de quem está autenticado
    private String[] identificar(Authentication auth) {
        String usuario = "anônimo";
        String perfil = "-";
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            usuario = auth.getName();
            perfil = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(a -> a.replace("ROLE_", ""))
                    .collect(Collectors.joining(","));
        }
        return new String[] { usuario, perfil };
    }

    // ========================
    // CONSULTA (somente ADMIN e GERENTE)
    // ========================
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<Auditoria> listarRecentes() {
        return auditoriaRepository.findTop500ByOrderByDataHoraDesc();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<Auditoria> listarPorEntidade(String entidade) {
        return auditoriaRepository.findTop500ByEntidadeOrderByDataHoraDesc(entidade);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<Auditoria> listarPorRegistro(String entidade, String entidadeId) {
        return auditoriaRepository.findByEntidadeAndEntidadeIdOrderByDataHoraDesc(entidade, entidadeId);
    }
}
