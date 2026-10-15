package com.umc.pediupatrao.entity;

import jakarta.persistence.Entity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pedidos")
@Entity
public class Pedido {

    @Id
    private String id;
    private String clienteId;
    private List<ItemPedido> itens = new ArrayList<>();
    private Double descontoPercentual;
    private Double valorTotal;
    private String responsavelRegistro;
    private LocalDateTime dataHoraEntrada;
    private LocalDateTime dataHoraSaida;
    private String responsavelSaida;
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }

    public Double getDescontoPercentual() { return descontoPercentual; }
    public void setDescontoPercentual(Double descontoPercentual) { this.descontoPercentual = descontoPercentual; }

    public Double getValorTotal() { return valorTotal; }
    public void setValorTotal(Double valorTotal) { this.valorTotal = valorTotal; }

    public String getResponsavelRegistro() { return responsavelRegistro; }
    public void setResponsavelRegistro(String responsavelRegistro) { this.responsavelRegistro = responsavelRegistro; }

    public LocalDateTime getDataHoraEntrada() { return dataHoraEntrada; }
    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) { this.dataHoraEntrada = dataHoraEntrada; }

    public LocalDateTime getDataHoraSaida() { return dataHoraSaida; }
    public void setDataHoraSaida(LocalDateTime dataHoraSaida) { this.dataHoraSaida = dataHoraSaida; }

    public String getResponsavelSaida() { return responsavelSaida; }
    public void setResponsavelSaida(String responsavelSaida) { this.responsavelSaida = responsavelSaida; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
