package com.dinoco.oficina.financeiro.application.usecases.queries.buscar;

import com.dinoco.oficina.financeiro.domain.Pagamento;

import java.util.UUID;

public interface BuscarPagamentoUseCase {
    Pagamento executar(UUID sagaId);
}
