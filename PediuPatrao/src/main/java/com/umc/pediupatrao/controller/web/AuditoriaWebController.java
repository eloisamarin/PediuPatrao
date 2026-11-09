package com.umc.pediupatrao.controller.web;

import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Consulta da auditoria — somente ADMIN e GERENTE, somente leitura (só existe GET).
 */
@Controller
public class AuditoriaWebController extends WebControllerSupport {

    private final AuditoriaService auditoriaService;

    public AuditoriaWebController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    // Filtro opcional por entidade (Pedido, Cliente...) e por registro
    @GetMapping("/auditoria")
    public String consultar(@RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId, Model model) {
        boolean temEntidade = entidade != null && !entidade.isBlank();
        boolean temId = entidadeId != null && !entidadeId.isBlank();
        model.addAttribute("registros", temEntidade && temId ? auditoriaService.listarPorRegistro(entidade, entidadeId)
                : temEntidade ? auditoriaService.listarPorEntidade(entidade)
                : auditoriaService.listarRecentes());
        model.addAttribute("filtroEntidade", temEntidade ? entidade : "");
        model.addAttribute("filtroId", temId ? entidadeId : "");
        return pagina(model, "auditoria :: content");
    }
}
