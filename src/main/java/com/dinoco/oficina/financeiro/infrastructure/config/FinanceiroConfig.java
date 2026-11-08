package com.dinoco.oficina.financeiro.infrastructure.config;

import com.dinoco.oficina.financeiro.application.gateways.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.criar.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.estornar.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.processar.*;
import com.dinoco.oficina.financeiro.application.usecases.queries.buscar.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
public class FinanceiroConfig {
    @Bean
    public CriarPagamentoUseCase criarPagamentoUseCase(PagamentoGateway gateway, PlatformTransactionManager manager) {
        var handler = new CriarPagamentoHandler(gateway);
        var tx = new TransactionTemplate(manager);
        return command -> tx.execute(status -> handler.executar(command));
    }

    @Bean
    public EstornarPagamentoUseCase estornarPagamentoUseCase(PagamentoGateway gateway, PlatformTransactionManager manager) {
        var handler = new EstornarPagamentoHandler(gateway);
        var tx = new TransactionTemplate(manager);
        return sagaId -> tx.executeWithoutResult(status -> handler.executar(sagaId));
    }

    @Bean
    public BuscarPagamentoUseCase buscarPagamentoUseCase(PagamentoGateway gateway) { return new BuscarPagamentoHandler(gateway); }

    @Bean
    public ProcessarPagamentoUseCase processarPagamentoUseCase(PagamentoGateway gateway, ProvedorPagamentoGateway provedor,
                                                              PagamentoEventPublisher eventos) {
        return new ProcessarPagamentoHandler(gateway, provedor, eventos);
    }
}
