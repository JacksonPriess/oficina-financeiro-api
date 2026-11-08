package com.dinoco.oficina.financeiro.infrastructure.events;

import com.dinoco.oficina.financeiro.application.gateways.PagamentoEventPublisher;
import com.dinoco.oficina.financeiro.domain.Pagamento;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.UUID;

@Component
public class PagamentoEventPublisherImpl implements PagamentoEventPublisher {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public PagamentoEventPublisherImpl(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }

    public void publicar(String tipo, Pagamento p) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Publicação exige transação ativa.");
        var evento = new Evento(UUID.randomUUID(), tipo, p.sagaId(), p.osId(), p.id(), p.valor(), p.moeda(),
                p.provedorId(), p.estornoId(), Instant.now().toString());
        jdbc.update("INSERT INTO financeiro_outbox(event_id,pagamento_id,tipo,payload) VALUES (?,?,?,?) ON CONFLICT (pagamento_id,tipo) DO NOTHING",
                evento.eventId(), p.id(), tipo, json.writeValueAsString(evento));
    }

    public record Evento(UUID eventId, String tipo, UUID sagaId, Long osId, UUID pagamentoId,
                         java.math.BigDecimal valor, String moeda, String provedorId, String estornoId, String ocorridoEm) {}
}
