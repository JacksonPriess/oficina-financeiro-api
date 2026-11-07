package com.dinoco.oficina.financeiro;

import com.dinoco.oficina.financeiro.domain.*;
import com.dinoco.oficina.financeiro.infrastructure.gateways.PagamentoGatewayImpl;
import com.dinoco.oficina.financeiro.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PagamentoGatewayJpaTest {
    @Test
    void converteEntityParaDominioSemPerderConfirmacaoDoEstorno() {
        var repo = mock(PagamentoJpaRepository.class);
        var entity = new PagamentoEntity(UUID.randomUUID(), UUID.randomUUID(), 1L, new BigDecimal("250.50"),
                "BRL", StatusPagamento.APROVADO, "provedor-1", true, "estorno-1");
        when(repo.findBySagaId(entity.getSagaId())).thenReturn(Optional.of(entity));
        var p = new PagamentoGatewayImpl(repo).buscarPorSaga(entity.getSagaId()).orElseThrow();
        assertThat(p).isEqualTo(new Pagamento(entity.getId(), entity.getSagaId(), 1L, new BigDecimal("250.50"),
                "BRL", StatusPagamento.APROVADO, "provedor-1", true, "estorno-1"));
    }

    @Test
    void salvaDominioEmEntityMantendoOsIdentificadores() {
        var repo = mock(PagamentoJpaRepository.class);
        var p = Pagamento.criar(UUID.randomUUID(), 2L, BigDecimal.TEN, "BRL").vincular("p-2").aprovar();
        new PagamentoGatewayImpl(repo).salvar(p);
        var capture = ArgumentCaptor.forClass(PagamentoEntity.class);
        verify(repo).saveAndFlush(capture.capture());
        var entity = capture.getValue();
        assertThat(entity.getId()).isEqualTo(p.id());
        assertThat(entity.getSagaId()).isEqualTo(p.sagaId());
        assertThat(entity.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(entity.getProvedorId()).isEqualTo("p-2");
    }
}
