package com.dinoco.oficina.financeiro;

import com.dinoco.oficina.financeiro.application.gateways.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.criar.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.estornar.*;
import com.dinoco.oficina.financeiro.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SolicitacoesPagamentoTest {
    @Test
    void repetirCriacaoRetornaPagamentoExistente() {
        var gateway = mock(PagamentoGateway.class);
        var p = Pagamento.criar(UUID.randomUUID(), 1L, BigDecimal.TEN, "BRL");
        when(gateway.registrar(any())).thenReturn(p);
        var output = new CriarPagamentoHandler(gateway).executar(new CriarPagamentoCommand(p.sagaId(), p.osId(), p.valor(), p.moeda()));
        assertThat(output.id()).isEqualTo(p.id());
    }

    @Test
    void repetirEstornoNaoAgendaNovamente() {
        var gateway = mock(PagamentoGateway.class);
        var p = Pagamento.criar(UUID.randomUUID(), 1L, BigDecimal.TEN, "BRL").vincular("p").aprovar().solicitarEstorno();
        when(gateway.bloquearPorSaga(p.sagaId())).thenReturn(Optional.of(p));
        new EstornarPagamentoHandler(gateway).executar(p.sagaId());
        verify(gateway, never()).salvar(any());
    }
}
