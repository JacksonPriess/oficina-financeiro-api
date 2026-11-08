package com.dinoco.oficina.financeiro.application.usecases.commands.criar;

import com.dinoco.oficina.financeiro.domain.Pagamento;

public interface CriarPagamentoUseCase {
    Pagamento executar(CriarPagamentoCommand command);
}
