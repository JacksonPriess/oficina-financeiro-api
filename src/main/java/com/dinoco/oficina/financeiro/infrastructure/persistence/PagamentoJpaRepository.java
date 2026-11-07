package com.dinoco.oficina.financeiro.infrastructure.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoJpaRepository extends JpaRepository<PagamentoEntity, UUID> {
    Optional<PagamentoEntity> findBySagaId(UUID sagaId);

    @Query(value = "SELECT * FROM pagamento WHERE saga_id=:sagaId FOR UPDATE", nativeQuery = true)
    Optional<PagamentoEntity> buscarPorSagaParaAlteracao(@Param("sagaId") UUID sagaId);

    @Query(value = """
            SELECT * FROM pagamento
            WHERE proxima_consulta <= CURRENT_TIMESTAMP
              AND (status='PENDENTE' OR (estorno_solicitado AND estorno_id IS NULL))
            ORDER BY proxima_consulta, criado_em LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<PagamentoEntity> buscarProximoPendente();

    // INSERT nativo mantém idempotência inclusive quando duas instâncias recebem a mesma saga.
    @Modifying
    @Query(value = """
            INSERT INTO pagamento(id,saga_id,os_id,valor,moeda,status) VALUES (:id,:sagaId,:osId,:valor,:moeda,'PENDENTE')
            ON CONFLICT (saga_id) DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(@Param("id") UUID id, @Param("sagaId") UUID sagaId, @Param("osId") Long osId,
                         @Param("valor") BigDecimal valor, @Param("moeda") String moeda);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE pagamento SET proxima_consulta=CURRENT_TIMESTAMP + INTERVAL '10 seconds',
                tentativas=0, atualizado_em=CURRENT_TIMESTAMP WHERE id=:id
            """, nativeQuery = true)
    void agendarConsulta(@Param("id") UUID id);

    // Após rollback, só reagenda se nenhuma outra instância já processou/reagendou a linha.
    @Modifying
    @Query(value = """
            UPDATE pagamento SET tentativas=tentativas+1,
                proxima_consulta=CURRENT_TIMESTAMP + INTERVAL '10 seconds' * LEAST(tentativas+1,30)
            WHERE id=:id AND proxima_consulta <= CURRENT_TIMESTAMP
              AND (status='PENDENTE' OR (estorno_solicitado AND estorno_id IS NULL))
            """, nativeQuery = true)
    int agendarRecuperacao(@Param("id") UUID id);
}
