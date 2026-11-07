package com.dinoco.oficina.financeiro.application.usecases.commands.estornar;

import com.dinoco.oficina.financeiro.application.gateways.PagamentoGateway;

import java.util.NoSuchElementException;
import java.util.UUID;

public class EstornarPagamentoHandler implements EstornarPagamentoUseCase {
    private final PagamentoGateway gateway;

    public EstornarPagamentoHandler(PagamentoGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void executar(UUID sagaId) {
        var pagamento = gateway.bloquearPorSaga(sagaId)
                .orElseThrow(() -> new NoSuchElementException("Pagamento não encontrado."));

        if (pagamento.estornoSolicitado())
            return;

        gateway.salvar(pagamento.solicitarEstorno());
    }
}
