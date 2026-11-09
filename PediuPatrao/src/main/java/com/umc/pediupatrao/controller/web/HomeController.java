package com.umc.pediupatrao.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Login, página inicial e páginas gerais.
 */
@Controller
public class HomeController extends WebControllerSupport {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        return pagina(model, "home :: content");
    }

    // Dashboard administrativo: a página só monta o layout; os dados vêm da API /api/dashboard via JavaScript
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        return pagina(model, "dashboard :: content");
    }

    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        return pagina(model, "configuracoes :: content");
    }

    // Painel de demonstração (AdminLTE), restrito ao ADMIN no SecurityConfig
    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
