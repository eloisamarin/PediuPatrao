package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.UsuarioService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Cadastro e gestão de usuários — somente ADMIN.
 * Protegido em três pontos: rota (SecurityConfig), service (@PreAuthorize) e token CSRF nos formulários.
 * Os formulários usam UsuarioForm (só username, senha e perfil), nunca a entidade Usuario.
 */
@Controller
@RequestMapping("/usuarios")
public class UsuarioWebController extends WebControllerSupport {

    private static final String LISTA = "/usuarios";

    private final UsuarioService usuarioService;

    public UsuarioWebController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String listar(Model model, Principal principal) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("usuarioLogado", principal.getName());
        return pagina(model, "usuarios/lista :: content");
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        return formulario(model, new UsuarioForm(), null);
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model, RedirectAttributes redirect) {
        try {
            Usuario usuario = usuarioService.buscarPorId(id);
            return formulario(model, UsuarioForm.de(usuario), id); // a senha nunca vai para a tela
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute(ERRO, e.getMessage());
            return "redirect:" + LISTA;
        }
    }

    @PostMapping("/salvar")
    public String criar(@ModelAttribute("usuario") UsuarioForm form, RedirectAttributes redirect) {
        return executar(redirect, LISTA, "Usuário criado com sucesso!", () -> usuarioService.criarUsuario(form));
    }

    @PostMapping("/editar/{id}")
    public String atualizar(@PathVariable String id, @ModelAttribute("usuario") UsuarioForm form, RedirectAttributes redirect) {
        return executar(redirect, LISTA, "Usuário atualizado com sucesso!", () -> usuarioService.atualizarUsuario(id, form));
    }

    @PostMapping("/deletar/{id}")
    public String excluir(@PathVariable String id, RedirectAttributes redirect) {
        return executar(redirect, LISTA, "Usuário removido com sucesso!", () -> usuarioService.deletarUsuario(id));
    }

    private String formulario(Model model, UsuarioForm form, String id) {
        model.addAttribute("usuario", form);
        model.addAttribute("usuarioId", id);
        model.addAttribute("perfis", Perfil.values());
        return pagina(model, "usuarios/form :: content");
    }
}
