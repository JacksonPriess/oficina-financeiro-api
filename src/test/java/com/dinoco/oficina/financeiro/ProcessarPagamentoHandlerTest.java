package com.dinoco.oficina.financeiro;

import com.dinoco.oficina.financeiro.application.gateways.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.processar.ProcessarPagamentoHandler;
import com.dinoco.oficina.financeiro.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ProcessarPagamentoHandlerTest {
    @Mock PagamentoGateway gateway;
    @Mock ProvedorPagamentoGateway provedor;
    @Mock PagamentoEventPublisher eventos;
    @InjectMocks ProcessarPagamentoHandler handler;

    private Pagamento pagamento() { return Pagamento.criar(UUID.randomUUID(), 1L, BigDecimal.TEN, "BRL"); }

    @Test
    void criaNoProvedorERegistraEvento() {
        var p = pagamento();
        when(provedor.criar(p)).thenReturn("externo-1");
        handler.executar(p);
        verify(gateway).salvar(p.vincular("externo-1"));
        verify(eventos).publicar("pagamento.criado.v1", p.vincular("externo-1"));
    }

    @Test
    void pendenciaNaoGeraNovoEventoNemNovaCobranca() {
        var p = pagamento().vincular("externo-1");
        when(provedor.consultar(p)).thenReturn(StatusPagamento.PENDENTE);
        handler.executar(p);
        verifyNoInteractions(gateway, eventos);
        verify(provedor, never()).criar(any());
    }

    @Test
    void aprovaERegistraEvento() {
        var p = pagamento().vincular("externo-1");
        when(provedor.consultar(p)).thenReturn(StatusPagamento.APROVADO);
        handler.executar(p);
        verify(gateway).salvar(p.aprovar());
        verify(eventos).publicar("pagamento.aprovado.v1", p.aprovar());
    }

    @Test
    void confirmaEstornoAntesDePublicar() {
        var p = pagamento().vincular("externo-1").aprovar().solicitarEstorno();
        when(provedor.estornar(p)).thenReturn("estorno-1");
        handler.executar(p);
        verify(eventos).publicar("pagamento.estornado.v1", p.estornar("estorno-1"));
    }

    @Test
    void falhaDoProvedorNaoGeraFalsoSucesso() {
        var p = pagamento();
        when(provedor.criar(p)).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> handler.executar(p)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(gateway, eventos);
    }
}
