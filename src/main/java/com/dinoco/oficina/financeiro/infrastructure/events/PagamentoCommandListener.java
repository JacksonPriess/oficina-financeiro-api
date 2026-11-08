package com.dinoco.oficina.financeiro.infrastructure.events;

import com.dinoco.oficina.financeiro.application.usecases.commands.criar.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.estornar.EstornarPagamentoUseCase;
import com.dinoco.oficina.financeiro.infrastructure.config.FinanceiroRabbitConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;

@Component
@Profile("rabbit")
@Slf4j
public class PagamentoCommandListener {
    private final CriarPagamentoUseCase criar;
    private final EstornarPagamentoUseCase estornar;
    private final JsonMapper json;
    public PagamentoCommandListener(CriarPagamentoUseCase criar, EstornarPagamentoUseCase estornar, JsonMapper json) {
        this.criar = criar; this.estornar = estornar; this.json = json;
    }

    @RabbitListener(queues = FinanceiroRabbitConfig.CRIAR)
    public void criar(Message message) {
        var command = json.readValue(message.getBody(), CriarPagamentoCommand.class);
        var p = criar.executar(command);
        log.info("evento=pagamento_solicitacao_registrada sagaId={} osId={} pagamentoId={}", p.sagaId(), p.osId(), p.id());
    }

    @RabbitListener(queues = FinanceiroRabbitConfig.ESTORNAR)
    public void estornar(Message message) {
        var command = json.readValue(message.getBody(), EstornarCommand.class);
        if (command.sagaId() == null) throw new IllegalArgumentException("sagaId é obrigatório.");
        estornar.executar(command.sagaId());
        log.info("evento=pagamento_estorno_solicitado sagaId={}", command.sagaId());
    }

    public record EstornarCommand(UUID sagaId) {}
}
