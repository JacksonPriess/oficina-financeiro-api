package com.dinoco.oficina.financeiro.application.usecases.commands.criar;

import java.math.BigDecimal;
import java.util.UUID;

public record CriarPagamentoCommand(
        UUID sagaId,
        Long osId,
        BigDecimal valor,
        String moeda
) {}