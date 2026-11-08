package com.dinoco.oficina.financeiro.infrastructure.jobs;

import com.dinoco.oficina.financeiro.application.gateways.PagamentoGateway;
import com.dinoco.oficina.financeiro.application.usecases.commands.processar.ProcessarPagamentoUseCase;
import com.dinoco.oficina.financeiro.domain.Pagamento;
import com.dinoco.oficina.financeiro.infrastructure.persistence.PagamentoJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
@ConditionalOnProperty(name = "financeiro.workers.enabled", havingValue = "true", matchIfMissing = true)
public class PagamentoWorker {
    private final TransactionTemplate tx;
    private final PagamentoGateway gateway;
    private final ProcessarPagamentoUseCase useCase;
    private final PagamentoJpaRepository repository;

    public PagamentoWorker(PlatformTransactionManager manager, PagamentoGateway gateway,
                           ProcessarPagamentoUseCase useCase, PagamentoJpaRepository repository) {
        this.tx = new TransactionTemplate(manager);
        this.gateway = gateway;
        this.useCase = useCase;
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${financeiro.consulta.intervalo-ms:2000}")
    public void processar() {
        var selecionado = new AtomicReference<Pagamento>();
        try {
            var atualizado = tx.execute(status -> {
                var candidato = gateway.proximoPendente();
                if (candidato.isEmpty()) return null;
                var p = candidato.get();
                selecionado.set(p);
                useCase.executar(p);
                repository.agendarConsulta(p.id());
                var resultado = gateway.buscarPorSaga(p.sagaId()).orElseThrow();
                return resultado.equals(p) ? null : resultado;
            });
            if (atualizado != null)
                log.info("evento=pagamento_estado_persistido sagaId={} osId={} pagamentoId={} status={} estornado={}",
                        atualizado.sagaId(), atualizado.osId(), atualizado.id(), atualizado.status(), atualizado.estornoId() != null);
        } catch (RuntimeException e) {
            // JpaTransactionManager não restaura o contexto de persistência com savepoints JDBC.
            // A transação inteira já foi revertida; recuperação usa uma transação nova.
            var p = selecionado.get();
            if (p == null) {
                log.error("evento=pagamento_transacao_falha erroTipo={}", e.getClass().getSimpleName());
                return;
            }
            log.warn("evento=pagamento_processamento_falha sagaId={} pagamentoId={} erroTipo={}", p.sagaId(), p.id(), e.getClass().getSimpleName());
            try {
                tx.executeWithoutResult(status -> repository.agendarRecuperacao(p.id()));
            } catch (RuntimeException recovery) {
                log.error("evento=pagamento_recuperacao_falha pagamentoId={} erroTipo={}", p.id(), recovery.getClass().getSimpleName());
            }
        }
    }
}
