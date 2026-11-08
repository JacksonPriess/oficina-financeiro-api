package com.dinoco.oficina.financeiro.application.gateways;

import com.dinoco.oficina.financeiro.domain.Pagamento;

import java.util.Optional;
import java.util.UUID;

public interface PagamentoGateway {
    Pagamento registrar(Pagamento pagamento);

    Optional<Pagamento> buscarPorSaga(UUID sagaId);

    Optional<Pagamento> bloquearPorSaga(UUID sagaId);

    Optional<Pagamento> proximoPendente();

    void salvar(Pagamento pagamento);
}
