package com.dinoco.oficina.financeiro.application.usecases.commands.criar;

import com.dinoco.oficina.financeiro.application.gateways.PagamentoGateway;
import com.dinoco.oficina.financeiro.domain.Pagamento;

public class CriarPagamentoHandler implements CriarPagamentoUseCase {
    private final PagamentoGateway gateway;

    public CriarPagamentoHandler(PagamentoGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Pagamento executar(CriarPagamentoCommand command) {
        var pagamento = gateway.registrar(Pagamento.criar(command.sagaId(), command.osId(), command.valor(), command.moeda()));
        pagamento.validarMesmaSolicitacao(command.osId(), command.valor(), command.moeda());
        return pagamento;
    }
}
