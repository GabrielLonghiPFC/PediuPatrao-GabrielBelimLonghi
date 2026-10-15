package com.umc.pediupatrao.service;

import com.umc.pediupatrao.config.ControlePerfil;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
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

    public Cliente novoCliente(Cliente cliente) {
        controlePerfil.verificar("ATENDENTE");
        return clienteRepository.save(cliente);
    }

    public List<Cliente> listarClientes() {
        controlePerfil.verificar("GERENTE", "ATENDENTE");
        return clienteRepository.findAll();
    }

    public void excluir(String id) {
        controlePerfil.verificar("ATENDENTE");
        clienteRepository.deleteById(id);
    }

    public Optional<Cliente> buscarPorId(String id) {
        controlePerfil.verificar("GERENTE", "ATENDENTE");
        return clienteRepository.findById(id);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    public Cliente salvar(Cliente cliente) {
        controlePerfil.verificar("ATENDENTE");
        // Se o cliente não tem ID (novo cliente), salva como novo
        if (cliente.getId() == null) {
            return clienteRepository.save(cliente);  // Cria um novo cliente
        } // Se já tem ID (cliente existente), atualiza
        else {
            // Verifica se o cliente existe antes de atualizar
            if (clienteRepository.existsById(cliente.getId())) {
                return clienteRepository.save(cliente);  // Atualiza o cliente existente
            } else {
                throw new IllegalArgumentException("Cliente não encontrado para atualização.");
            }
        }
    }

}
