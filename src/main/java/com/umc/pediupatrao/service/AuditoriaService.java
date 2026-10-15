package com.umc.pediupatrao.service;

import com.umc.pediupatrao.config.ControlePerfil;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    public static final int REGISTROS_POR_PAGINA = 10;

    private final AuditoriaRepository auditoriaRepository;
    private final ControlePerfil controlePerfil;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, ControlePerfil controlePerfil) {
        this.auditoriaRepository = auditoriaRepository;
        this.controlePerfil = controlePerfil;
    }

    public void registrar(String operacao, String entidade, String entidadeId, String campoAlterado,
            String valorAnterior, String valorNovo, String justificativa) {
        RegistroAuditoria registro = new RegistroAuditoria();
        registro.setId(UUID.randomUUID().toString());
        registro.setUsuario(controlePerfil.usuarioLogado());
        registro.setPerfil(controlePerfil.perfilLogado());
        registro.setDataHora(LocalDateTime.now());
        registro.setOperacao(operacao);
        registro.setEntidade(entidade);
        registro.setEntidadeId(entidadeId);
        registro.setCampoAlterado(campoAlterado);
        registro.setValorAnterior(valorAnterior);
        registro.setValorNovo(valorNovo);
        registro.setJustificativa(justificativa);
        auditoriaRepository.save(registro);
    }

    public Page<RegistroAuditoria> listarRegistros(int pagina) {
        controlePerfil.verificar("ADMIN", "GERENTE");
        if (pagina < 0) {
            pagina = 0;
        }
        return auditoriaRepository.findAll(
                PageRequest.of(pagina, REGISTROS_POR_PAGINA, Sort.by(Sort.Direction.DESC, "dataHora")));
    }
}
