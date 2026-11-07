package com.dinoco.oficina.financeiro.application.usecases.commands.processar;

import com.dinoco.oficina.financeiro.application.gateways.*;
import com.dinoco.oficina.financeiro.domain.StatusPagamento;

public class ProcessarPagamentoHandler implements ProcessarPagamentoUseCase {
    private final PagamentoGateway gateway;
    private final ProvedorPagamentoGateway provedor;
    private final PagamentoEventPublisher eventos;

    public ProcessarPagamentoHandler(PagamentoGateway gateway, ProvedorPagamentoGateway provedor, PagamentoEventPublisher eventos) {
        this.gateway = gateway;
        this.provedor = provedor;
        this.eventos = eventos;
    }

    @Override
    public void executar(com.dinoco.oficina.financeiro.domain.Pagamento pagamento) {
        if (pagamento.provedorId() == null) {
            pagamento = pagamento.vincular(provedor.criar(pagamento));
            gateway.salvar(pagamento);
            eventos.publicar("pagamento.criado.v1", pagamento);
        } else if (pagamento.estornoSolicitado()) {
            pagamento = pagamento.estornar(provedor.estornar(pagamento));
            gateway.salvar(pagamento);
            eventos.publicar("pagamento.estornado.v1", pagamento);
        } else if (provedor.consultar(pagamento) == StatusPagamento.APROVADO) {
            pagamento = pagamento.aprovar();
            gateway.salvar(pagamento);
            eventos.publicar("pagamento.aprovado.v1", pagamento);
        }
    }
}