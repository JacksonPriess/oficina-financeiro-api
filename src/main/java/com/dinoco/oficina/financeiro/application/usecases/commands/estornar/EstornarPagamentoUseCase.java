package com.dinoco.oficina.financeiro.application.usecases.commands.estornar;

import java.util.UUID;

public interface EstornarPagamentoUseCase {
    void executar(UUID sagaId);
}
