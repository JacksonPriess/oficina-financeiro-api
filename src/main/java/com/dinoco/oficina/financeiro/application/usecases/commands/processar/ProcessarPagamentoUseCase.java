package com.dinoco.oficina.financeiro.application.usecases.commands.processar;

import com.dinoco.oficina.financeiro.domain.Pagamento;

public interface ProcessarPagamentoUseCase {
    void executar(Pagamento pagamento);
}
