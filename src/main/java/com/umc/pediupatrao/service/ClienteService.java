package com.umc.pediupatrao.service;

import com.umc.pediupatrao.config.ControlePerfil;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.validations.ClienteAuditoria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ControlePerfil controlePerfil;

    @Autowired
    private AuditoriaService auditoriaService;

    public Cliente novoCliente(Cliente cliente) {
        controlePerfil.verificar("ATENDENTE");
        Cliente salvo = clienteRepository.save(cliente);
        ClienteAuditoria.registrar(auditoriaService, null, salvo, true);
        return salvo;
    }

    public List<Cliente> listarClientes() {
        controlePerfil.verificar("GERENTE", "ATENDENTE");
        return clienteRepository.findAll();
    }

    public void excluir(String id) {
        controlePerfil.verificar("ATENDENTE");
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));
        clienteRepository.deleteById(id);
        ClienteAuditoria.registrarExclusao(auditoriaService, cliente);
    }

    public Optional<Cliente> buscarPorId(String id) {
        controlePerfil.verificar("GERENTE", "ATENDENTE");
        return clienteRepository.findById(id);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    public Cliente salvar(Cliente cliente) {
        controlePerfil.verificar("ATENDENTE");
        if (cliente.getId() == null) {
            Cliente salvo = clienteRepository.save(cliente);
            ClienteAuditoria.registrar(auditoriaService, null, salvo, true);
            return salvo;
        }
        Cliente anterior = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado para atualização."));
        Cliente salvo = clienteRepository.save(cliente);
        ClienteAuditoria.registrar(auditoriaService, anterior, salvo, false);
        return salvo;
    }

}
