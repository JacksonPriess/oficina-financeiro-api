package com.dinoco.oficina.financeiro.application.usecases.queries.buscar;

import com.dinoco.oficina.financeiro.application.gateways.PagamentoGateway;
import com.dinoco.oficina.financeiro.domain.Pagamento;

import java.util.NoSuchElementException;
import java.util.UUID;

public class BuscarPagamentoHandler implements BuscarPagamentoUseCase {
    private final PagamentoGateway gateway;

    public BuscarPagamentoHandler(PagamentoGateway gateway) {
        this.gateway = gateway;
    }

    public Pagamento executar(UUID sagaId) {
        return gateway.buscarPorSaga(sagaId).orElseThrow(() -> new NoSuchElementException("Pagamento não encontrado."));
    }
}
