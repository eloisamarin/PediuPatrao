package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Clientes: o ATENDENTE cadastra e atualiza; o GERENTE apenas consulta.
 * Toda inclusão e alteração é auditada campo a campo (endereço e telefone incluídos).
 */
@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Cadastra um novo cliente (o id enviado é ignorado para não sobrescrever outro registro)
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente cadastrar(Cliente cliente) {
        cliente.setId(null);
        Cliente salvo = clienteRepository.save(cliente);
        auditoriaService.registrarAlteracao("CRIAR_CLIENTE", "Cliente", salvo.getId(),
                diferencas(new Cliente(), salvo).lista(), null, null);
        return salvo;
    }

    // Atualiza um cliente existente, auditando cada campo que mudou (valor anterior → novo)
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente atualizar(String id, Cliente cliente) {
        Cliente anterior = id == null ? null : clienteRepository.findById(id).orElse(null);
        if (anterior == null) {
            throw new RecursoNaoEncontradoException("Cliente não encontrado para atualização.");
        }
        Alteracoes alteracoes = diferencas(anterior, cliente);
        cliente.setId(id);
        Cliente salvo = clienteRepository.save(cliente);
        if (!alteracoes.vazia()) {
            auditoriaService.registrarAlteracao("ATUALIZAR_CLIENTE", "Cliente", id, alteracoes.lista(), null, null);
        }
        return salvo;
    }

    // Compara todos os dados do cliente
    static Alteracoes diferencas(Cliente antes, Cliente depois) {
        return Alteracoes.nova()
                .se("nome", antes.getNome(), depois.getNome())
                .se("telefone", antes.getTelefone(), depois.getTelefone())
                .se("cep", antes.getCep(), depois.getCep())
                .se("logradouro", antes.getLogradouro(), depois.getLogradouro())
                .se("numero", antes.getNumero(), depois.getNumero())
                .se("complemento", antes.getComplemento(), depois.getComplemento())
                .se("bairro", antes.getBairro(), depois.getBairro())
                .se("cidade", antes.getCidade(), depois.getCidade())
                .se("estado", antes.getEstado(), depois.getEstado())
                .se("endereco", antes.getEndereco(), depois.getEndereco());
    }
}
