package com.dinoco.oficina.financeiro.infrastructure.web.dto;

import com.dinoco.oficina.financeiro.domain.Pagamento;

import java.math.BigDecimal;
import java.util.UUID;

public record PagamentoResponse(UUID pagamentoId,
                                UUID sagaId,
                                Long osId,
                                BigDecimal valor,
                                String moeda,
                                String status,
                                String provedorId,
                                boolean estornoSolicitado,
                                String estornoId) {

    public static PagamentoResponse de(Pagamento p) {
        return new PagamentoResponse(p.id(), p.sagaId(), p.osId(), p.valor(), p.moeda(), p.status().name(),
                p.provedorId(), p.estornoSolicitado(), p.estornoId());
    }
}