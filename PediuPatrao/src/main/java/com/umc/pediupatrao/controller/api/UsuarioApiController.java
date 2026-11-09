package com.umc.pediupatrao.controller.api;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.dto.UsuarioResposta;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API de usuários — somente ADMIN (SecurityConfig + @PreAuthorize no UsuarioService).
 * Entrada: UsuarioForm (username, password, role). Saída: UsuarioResposta (sem hash de senha).
 * Requisições que alteram dados exigem o cabeçalho X-CSRF-TOKEN.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioApiController {

    private final UsuarioService usuarioService;

    public UsuarioApiController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioResposta> listar() {
        return usuarioService.listarTodos().stream().map(UsuarioResposta::de).toList();
    }

    @GetMapping("/{id}")
    public UsuarioResposta buscar(@PathVariable String id) {
        return UsuarioResposta.de(usuarioService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<UsuarioResposta> criar(@RequestBody UsuarioForm form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResposta.de(usuarioService.criarUsuario(form)));
    }

    // Atualiza perfil e/ou senha (o username não muda)
    @PutMapping("/{id}")
    public UsuarioResposta atualizar(@PathVariable String id, @RequestBody UsuarioForm form) {
        return UsuarioResposta.de(usuarioService.atualizarUsuario(id, form));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable String id) {
        usuarioService.deletarUsuario(id);
        return ResponseEntity.noContent().build();
    }
}
