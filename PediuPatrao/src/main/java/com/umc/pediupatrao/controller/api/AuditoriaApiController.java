package com.umc.pediupatrao.controller.api;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de CONSULTA da auditoria — somente ADMIN e GERENTE.
 * Não existe POST, PUT ou DELETE: os registros não podem ser criados, alterados ou excluídos por aqui
 * (o SecurityConfig também nega qualquer método diferente de GET nesta rota).
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaApiController {

    private final AuditoriaService auditoriaService;

    public AuditoriaApiController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    // Ex.: /api/auditoria?entidade=Cliente&entidadeId=123
    @GetMapping
    public List<Auditoria> consultar(@RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId) {
        boolean temEntidade = entidade != null && !entidade.isBlank();
        boolean temId = entidadeId != null && !entidadeId.isBlank();
        if (temEntidade && temId) {
            return auditoriaService.listarPorRegistro(entidade, entidadeId);
        }
        return temEntidade ? auditoriaService.listarPorEntidade(entidade) : auditoriaService.listarRecentes();
    }
}
