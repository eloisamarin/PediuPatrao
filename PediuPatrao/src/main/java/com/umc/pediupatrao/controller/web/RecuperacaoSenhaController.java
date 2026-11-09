package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.RecuperacaoSenhaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Telas públicas do "Esqueci a senha" (liberadas no SecurityConfig; os POST exigem token CSRF).
 *
 *   GET  /esqueci-senha              formulário: usuário ou e-mail
 *   POST /esqueci-senha              envia o link e mostra sempre a mesma mensagem
 *   GET  /redefinir-senha?token=...  formulário da nova senha (ou aviso de link inválido)
 *   POST /redefinir-senha            grava a nova senha e volta para o login
 */
@Controller
public class RecuperacaoSenhaController {

    private final RecuperacaoSenhaService recuperacaoSenha;

    public RecuperacaoSenhaController(RecuperacaoSenhaService recuperacaoSenha) {
        this.recuperacaoSenha = recuperacaoSenha;
    }

    @GetMapping("/esqueci-senha")
    public String esqueciSenha() {
        return "esqueci-senha";
    }

    @PostMapping("/esqueci-senha")
    public String solicitar(@RequestParam(required = false) String identificador) {
        recuperacaoSenha.solicitar(identificador);
        // Mesma resposta exista ou não a conta (não revela quem é usuário do sistema)
        return "redirect:/esqueci-senha?enviado";
    }

    @GetMapping("/redefinir-senha")
    public String redefinirSenha(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valido", recuperacaoSenha.tokenValido(token));
        return "redefinir-senha";
    }

    @PostMapping("/redefinir-senha")
    public String redefinir(@RequestParam(required = false) String token, @RequestParam(required = false) String senha,
            @RequestParam(required = false) String confirmacao, Model model) {
        try {
            recuperacaoSenha.redefinir(token, senha, confirmacao);
            return "redirect:/login?senhaRedefinida";
        } catch (RegraNegocioException e) {
            model.addAttribute("token", token);
            model.addAttribute("valido", recuperacaoSenha.tokenValido(token));
            model.addAttribute("erro", e.getMessage());
            return "redefinir-senha";
        }
    }
}
