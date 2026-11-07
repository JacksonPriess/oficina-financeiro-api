package com.dinoco.oficina.financeiro.infrastructure.jobs;

import com.dinoco.oficina.financeiro.infrastructure.config.FinanceiroRabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Profile("rabbit")
@ConditionalOnProperty(name="financeiro.workers.enabled", havingValue="true", matchIfMissing=true)
@Slf4j
public class OutboxWorker {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;
    public OutboxWorker(JdbcTemplate jdbc, RabbitTemplate rabbit, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.rabbit = rabbit; this.tx = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString="${financeiro.outbox.intervalo-ms:2000}")
    public void publicar() {
        try {
            tx.executeWithoutResult(status -> {
                var rows = jdbc.query("""
                        SELECT event_id,tipo,payload FROM financeiro_outbox
                        WHERE publicado_em IS NULL AND proxima_tentativa <= CURRENT_TIMESTAMP
                        ORDER BY criado_em LIMIT 1 FOR UPDATE SKIP LOCKED
                        """, (rs, row) -> new Pendente(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
                if (rows.isEmpty()) return;
                var e = rows.getFirst();
                try {
                    var properties = new MessageProperties();
                    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    properties.setContentEncoding("UTF-8");
                    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    properties.setMessageId(e.id().toString());
                    properties.setType(e.tipo());
                    var correlation = new CorrelationData(UUID.randomUUID().toString());
                    rabbit.send(FinanceiroRabbitConfig.EXCHANGE, e.tipo(),
                            new Message(e.payload().getBytes(StandardCharsets.UTF_8), properties), correlation);
                    var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                    if (!confirm.ack() || correlation.getReturned() != null)
                        throw new IllegalStateException("Publicação não confirmada ou sem destino.");
                    jdbc.update("UPDATE financeiro_outbox SET publicado_em=CURRENT_TIMESTAMP WHERE event_id=?", e.id());
                    log.info("evento=financeiro_publicacao_confirmada eventoId={} tipo={}", e.id(), e.tipo());
                } catch (Exception failure) {
                    if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                    jdbc.update("UPDATE financeiro_outbox SET tentativas=tentativas+1, proxima_tentativa=CURRENT_TIMESTAMP + INTERVAL '10 seconds' WHERE event_id=?", e.id());
                    log.warn("evento=financeiro_publicacao_falha eventoId={} erroTipo={}", e.id(), failure.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException failure) {
            log.error("evento=financeiro_outbox_transacao_falha erroTipo={}", failure.getClass().getSimpleName());
        }
    }

    private record Pendente(UUID id, String tipo, String payload) {}
}
