package com.dinoco.oficina.financeiro.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
@Profile("rabbit")
public class FinanceiroRabbitConfig {
    public static final String EXCHANGE = "oficina.financeiro";
    public static final String CRIAR = "financeiro.pagamento.criar";
    public static final String ESTORNAR = "financeiro.pagamento.estornar";
    public static final String EVENTOS = "nucleo.financeiro.eventos";

    @Bean
    public Declarables financeiroTopology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var dlx = new DirectExchange(EXCHANGE + ".dlx", true, false);
        var criar = fila(CRIAR);
        var estornar = fila(ESTORNAR);
        var eventos = fila(EVENTOS);
        var criarDlq = new Queue(CRIAR + ".dlq", true);
        var estornarDlq = new Queue(ESTORNAR + ".dlq", true);
        var eventosDlq = new Queue(EVENTOS + ".dlq", true);
        return new Declarables(exchange, dlx, criar, estornar, eventos, criarDlq, estornarDlq, eventosDlq,
                BindingBuilder.bind(criar).to(exchange).with("pagamento.criar.v1"),
                BindingBuilder.bind(estornar).to(exchange).with("pagamento.estornar.v1"),
                BindingBuilder.bind(eventos).to(exchange).with("pagamento.criado.v1"),
                BindingBuilder.bind(eventos).to(exchange).with("pagamento.aprovado.v1"),
                BindingBuilder.bind(eventos).to(exchange).with("pagamento.estornado.v1"),
                BindingBuilder.bind(criarDlq).to(dlx).with(CRIAR),
                BindingBuilder.bind(estornarDlq).to(dlx).with(ESTORNAR),
                BindingBuilder.bind(eventosDlq).to(dlx).with(EVENTOS));
    }

    private Queue fila(String nome) {
        return QueueBuilder.durable(nome).deadLetterExchange(EXCHANGE + ".dlx").deadLetterRoutingKey(nome).build();
    }
}
