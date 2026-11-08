package com.dinoco.oficina.financeiro.infrastructure.gateways;

import com.dinoco.oficina.financeiro.application.gateways.PagamentoGateway;
import com.dinoco.oficina.financeiro.domain.Pagamento;
import com.dinoco.oficina.financeiro.infrastructure.persistence.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Component
public class PagamentoGatewayImpl implements PagamentoGateway {
    private final PagamentoJpaRepository jpaRepository;

    public PagamentoGatewayImpl(PagamentoJpaRepository jpaRepository) { this.jpaRepository = jpaRepository; }

    @Override
    @Transactional
    public Pagamento registrar(Pagamento pagamento) {
        jpaRepository.inserirSeAusente(pagamento.id(), pagamento.sagaId(), pagamento.osId(), pagamento.valor(), pagamento.moeda());
        return buscarPorSaga(pagamento.sagaId()).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Pagamento> buscarPorSaga(UUID sagaId) {
        return jpaRepository.findBySagaId(sagaId).map(this::mapearParaDominio);
    }

    @Override
    public Optional<Pagamento> bloquearPorSaga(UUID sagaId) {
        return jpaRepository.buscarPorSagaParaAlteracao(sagaId).map(this::mapearParaDominio);
    }

    @Override
    public Optional<Pagamento> proximoPendente() {
        return jpaRepository.buscarProximoPendente().map(this::mapearParaDominio);
    }

    @Override
    @Transactional
    public void salvar(Pagamento pagamento) {
        jpaRepository.saveAndFlush(mapearParaEntity(pagamento));
    }

    private PagamentoEntity mapearParaEntity(Pagamento p) {
        return new PagamentoEntity(p.id(), p.sagaId(), p.osId(), p.valor(), p.moeda(), p.status(),
                p.provedorId(), p.estornoSolicitado(), p.estornoId());
    }

    private Pagamento mapearParaDominio(PagamentoEntity e) {
        return new Pagamento(e.getId(), e.getSagaId(), e.getOsId(), e.getValor(), e.getMoeda(), e.getStatus(),
                e.getProvedorId(), e.isEstornoSolicitado(), e.getEstornoId());
    }
}
