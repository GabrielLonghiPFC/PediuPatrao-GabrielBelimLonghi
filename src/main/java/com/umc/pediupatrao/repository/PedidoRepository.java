package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import java.time.LocalDateTime;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);

    @Query("{ 'dataHoraEntrada': { $gte: ?0, $lt: ?1 } }")
    List<Pedido> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim);

    @Query("{ 'dataHoraEntrada': { $gte: ?0, $lt: ?1 }, 'status': { $in: ?2 } }")
    List<Pedido> buscarPorPeriodoEStatus(LocalDateTime inicio, LocalDateTime fim, List<String> status);
}
