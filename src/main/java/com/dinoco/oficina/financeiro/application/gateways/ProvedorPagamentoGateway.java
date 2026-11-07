package com.dinoco.oficina.financeiro.application.gateways;

import com.dinoco.oficina.financeiro.domain.Pagamento;
import com.dinoco.oficina.financeiro.domain.StatusPagamento;

public interface ProvedorPagamentoGateway {
    String criar(Pagamento pagamento);

    StatusPagamento consultar(Pagamento pagamento);

    String estornar(Pagamento pagamento);
}
