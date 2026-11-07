package com.dinoco.oficina.financeiro.controllers;

import com.dinoco.oficina.financeiro.application.usecases.commands.criar.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.estornar.EstornarPagamentoUseCase;
import com.dinoco.oficina.financeiro.application.usecases.queries.buscar.BuscarPagamentoUseCase;
import com.dinoco.oficina.financeiro.domain.Pagamento;

import java.util.UUID;

public class FinanceiroControllerClean {
    private final CriarPagamentoUseCase criarPagamentoUseCase;
    private final BuscarPagamentoUseCase buscarPagamentoUseCase;
    private final EstornarPagamentoUseCase estornarPagamentoUseCase;

    public FinanceiroControllerClean(CriarPagamentoUseCase criarPagamentoUseCase,
                                     BuscarPagamentoUseCase buscarPagamentoUseCase,
                                     EstornarPagamentoUseCase estornarPagamentoUseCase) {
        this.criarPagamentoUseCase = criarPagamentoUseCase;
        this.buscarPagamentoUseCase = buscarPagamentoUseCase;
        this.estornarPagamentoUseCase = estornarPagamentoUseCase;
    }

    public Pagamento criar(CriarPagamentoCommand command) {
        return criarPagamentoUseCase.executar(command);
    }

    public Pagamento buscar(UUID sagaId) {
        return buscarPagamentoUseCase.executar(sagaId);
    }

    public void estornar(UUID sagaId) {
        estornarPagamentoUseCase.executar(sagaId);
    }
}
