package com.dinoco.oficina.financeiro.application.gateways;

import com.dinoco.oficina.financeiro.domain.Pagamento;

public interface PagamentoEventPublisher {
    void publicar(String tipo, Pagamento pagamento);
}
