package com.dinoco.oficina.financeiro.application;

import com.dinoco.oficina.financeiro.application.gateways.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.criar.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.estornar.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.processar.*;
import com.dinoco.oficina.financeiro.application.usecases.queries.buscar.*;
import com.dinoco.oficina.financeiro.domain.*;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PagamentoCasosComplementaresTest {

    private final PagamentoGateway gateway = mock(PagamentoGateway.class);
    private final ProvedorPagamentoGateway provedor = mock(ProvedorPagamentoGateway.class);
    private final PagamentoEventPublisher eventos = mock(PagamentoEventPublisher.class);
    private final ProcessarPagamentoHandler processar = new ProcessarPagamentoHandler(gateway, provedor, eventos);

    @Test
    void deveRegistrarPagamentoComDadosDaSolicitacao() {
        var sagaId = UUID.randomUUID();
        when(gateway.registrar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var p = new CriarPagamentoHandler(gateway).executar(new CriarPagamentoCommand(sagaId, 42L, new BigDecimal("125.50"), "BRL"));
        var captor = ArgumentCaptor.forClass(Pagamento.class);
        verify(gateway).registrar(captor.capture());
        assertThat(captor.getValue()).isEqualTo(p);
        assertThat(p.id()).isNotNull();
        assertThat(p.sagaId()).isEqualTo(sagaId);
        assertThat(p.osId()).isEqualTo(42L);
        assertThat(p.valor()).isEqualByComparingTo("125.50");
        assertThat(p.status()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(p.provedorId()).isNull();
    }

    @Test
    void deveRejeitarCriacaoInvalidaAntesDePersistir() {
        assertThatThrownBy(() -> new CriarPagamentoHandler(gateway)
                .executar(new CriarPagamentoCommand(UUID.randomUUID(), 1L, BigDecimal.ZERO, "BRL")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void deveRejeitarSagaReutilizadaComDadosDiferentes() {
        var p = pagamento();
        when(gateway.registrar(any())).thenReturn(p);
        assertThatThrownBy(() -> new CriarPagamentoHandler(gateway)
                .executar(new CriarPagamentoCommand(p.sagaId(), 99L, p.valor(), p.moeda())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devePropagarFalhaDeRegistro() {
        var falha = new IllegalStateException("registro indisponível");
        when(gateway.registrar(any())).thenThrow(falha);
        assertThatThrownBy(() -> new CriarPagamentoHandler(gateway)
                .executar(new CriarPagamentoCommand(UUID.randomUUID(), 1L, BigDecimal.TEN, "BRL"))).isSameAs(falha);
    }

    @Test
    void deveConsultarPelaSagaSolicitada() {
        var p = pagamento();
        when(gateway.buscarPorSaga(p.sagaId())).thenReturn(Optional.of(p));
        assertThat(new BuscarPagamentoHandler(gateway).executar(p.sagaId())).isEqualTo(p);
        verify(gateway).buscarPorSaga(p.sagaId());
    }

    @Test
    void deveRejeitarConsultaInexistente() {
        assertThatThrownBy(() -> new BuscarPagamentoHandler(gateway).executar(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void devePropagarFalhaDeConsulta() {
        var saga = UUID.randomUUID();
        var falha = new IllegalStateException("consulta indisponível");
        when(gateway.buscarPorSaga(saga)).thenThrow(falha);
        assertThatThrownBy(() -> new BuscarPagamentoHandler(gateway).executar(saga)).isSameAs(falha);
    }

    @Test
    void deveSolicitarEstornoDePagamentoAprovado() {
        var p = pagamento().vincular("p-1").aprovar();
        when(gateway.bloquearPorSaga(p.sagaId())).thenReturn(Optional.of(p));
        new EstornarPagamentoHandler(gateway).executar(p.sagaId());
        verify(gateway).salvar(p.solicitarEstorno());
    }

    @Test
    void deveRejeitarEstornoDePagamentoInexistente() {
        assertThatThrownBy(() -> new EstornarPagamentoHandler(gateway).executar(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
        verify(gateway, never()).salvar(any());
    }

    @Test
    void deveRejeitarEstornoPendenteSemPersistir() {
        var p = pagamento();
        when(gateway.bloquearPorSaga(p.sagaId())).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> new EstornarPagamentoHandler(gateway).executar(p.sagaId()))
                .isInstanceOf(IllegalStateException.class);
        verify(gateway, never()).salvar(any());
    }

    @Test
    void deveRejeitarIdentificadorVazioDoProvedorSemSalvarOuPublicar() {
        var p = pagamento();
        when(provedor.criar(p)).thenReturn(" ");
        assertThatThrownBy(() -> processar.executar(p)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway, eventos);
    }

    @Test
    void deveImpedirEventoQuandoGravacaoDoVinculoFalha() {
        var p = pagamento();
        when(provedor.criar(p)).thenReturn("p-1");
        var falha = new IllegalStateException("banco indisponível");
        doThrow(falha).when(gateway).salvar(any());
        assertThatThrownBy(() -> processar.executar(p)).isSameAs(falha);
        verifyNoInteractions(eventos);
    }

    @Test
    void devePropagarFalhaDoEventoParaTransacaoExterna() {
        var p = pagamento();
        when(provedor.criar(p)).thenReturn("p-1");
        var falha = new IllegalStateException("outbox indisponível");
        doThrow(falha).when(eventos).publicar(anyString(), any());
        assertThatThrownBy(() -> processar.executar(p)).isSameAs(falha);
    }

    @Test
    void devePropagarFalhaDeConsultaDoProvedorSemAlterarPagamento() {
        var p = pagamento().vincular("p-1");
        var falha = new IllegalStateException("timeout");
        when(provedor.consultar(p)).thenThrow(falha);
        assertThatThrownBy(() -> processar.executar(p)).isSameAs(falha);
        verifyNoInteractions(gateway, eventos);
    }

    @Test
    void devePersistirAprovacaoAntesDePublicar() {
        var p = pagamento().vincular("p-1");
        when(provedor.consultar(p)).thenReturn(StatusPagamento.APROVADO);
        processar.executar(p);
        var ordem = inOrder(gateway, eventos);
        ordem.verify(gateway).salvar(p.aprovar());
        ordem.verify(eventos).publicar("pagamento.aprovado.v1", p.aprovar());
        verify(provedor, never()).criar(any());
    }

    @Test
    void devePriorizarEstornoSemConsultarOuCriarCobranca() {
        var p = pagamento().vincular("p-1").aprovar().solicitarEstorno();
        when(provedor.estornar(p)).thenReturn("e-1");
        processar.executar(p);
        var ordem = inOrder(gateway, eventos);
        ordem.verify(gateway).salvar(p.estornar("e-1"));
        ordem.verify(eventos).publicar("pagamento.estornado.v1", p.estornar("e-1"));
        verify(provedor, never()).consultar(any());
        verify(provedor, never()).criar(any());
    }

    @Test
    void devePropagarFalhaDeEstornoSemSalvarOuPublicar() {
        var p = pagamento().vincular("p-1").aprovar().solicitarEstorno();
        var falha = new IllegalStateException("timeout estorno");
        when(provedor.estornar(p)).thenThrow(falha);
        assertThatThrownBy(() -> processar.executar(p)).isSameAs(falha);
        verifyNoInteractions(gateway, eventos);
    }

    @Test
    void deveRejeitarConfirmacaoDeEstornoVazia() {
        var p = pagamento().vincular("p-1").aprovar().solicitarEstorno();
        when(provedor.estornar(p)).thenReturn("");
        assertThatThrownBy(() -> processar.executar(p)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(gateway, eventos);
    }

    private Pagamento pagamento() {
        return Pagamento.criar(UUID.randomUUID(), 1L, BigDecimal.TEN, "BRL");
    }
}
