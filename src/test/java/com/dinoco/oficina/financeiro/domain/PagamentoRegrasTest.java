package com.dinoco.oficina.financeiro.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PagamentoRegrasTest {

    @Test
    void deveRejeitarIdentificadoresInvalidos() {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> new Pagamento(null, id, 1L, BigDecimal.TEN, "BRL", StatusPagamento.PENDENTE, null, false, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pagamento.criar(null, 1L, BigDecimal.TEN, "BRL")).isInstanceOf(IllegalArgumentException.class);
        for (var osId : new Long[]{null, 0L, -1L}) {
            assertThatThrownBy(() -> Pagamento.criar(id, osId, BigDecimal.TEN, "BRL")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void deveRejeitarValorNuloOuAcimaDoLimite() {
        for (var valor : new BigDecimal[]{null, new BigDecimal("100000000000000000.00")}) {
            assertThatThrownBy(() -> Pagamento.criar(UUID.randomUUID(), 1L, valor, "BRL"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void deveAceitarValorLimiteEZeroAdicionalNaEscala() {
        var p = Pagamento.criar(UUID.randomUUID(), 1L, new BigDecimal("99999999999999999.99"), "BRL");
        assertThat(p.valor()).isEqualByComparingTo("99999999999999999.99");
        var decimal = Pagamento.criar(UUID.randomUUID(), 1L, new BigDecimal("1.2300"), "BRL");
        assertThatCode(() -> decimal.validarMesmaSolicitacao(1L, new BigDecimal("1.23"), "BRL")).doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarMoedaInvalidaOuStatusNulo() {
        for (var moeda : new String[]{null, "USD", "brl"}) {
            assertThatThrownBy(() -> Pagamento.criar(UUID.randomUUID(), 1L, BigDecimal.TEN, moeda))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new Pagamento(UUID.randomUUID(), UUID.randomUUID(), 1L, BigDecimal.TEN, "BRL", null, null, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarVinculoSemIdentificador() {
        for (var id : new String[]{null, "", " "}) {
            assertThatThrownBy(() -> novo().vincular(id)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void deveRejeitarEstornoSemSolicitacaoOuConfirmacao() {
        var aprovado = novo().vincular("p-1").aprovar();
        assertThatThrownBy(() -> aprovado.estornar("e-1")).isInstanceOf(IllegalStateException.class);
        for (var id : new String[]{null, "", " "}) {
            assertThatThrownBy(() -> aprovado.solicitarEstorno().estornar(id)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void devePreservarIdentidadeEValorAoAprovarEEstornar() {
        var inicial = novo();
        var finalizado = inicial.vincular("p-1").aprovar().solicitarEstorno().estornar("e-1");
        assertThat(finalizado.id()).isEqualTo(inicial.id());
        assertThat(finalizado.sagaId()).isEqualTo(inicial.sagaId());
        assertThat(finalizado.osId()).isEqualTo(inicial.osId());
        assertThat(finalizado.valor()).isEqualTo(inicial.valor());
        assertThat(finalizado.moeda()).isEqualTo("BRL");
        assertThat(finalizado.provedorId()).isEqualTo("p-1");
        assertThat(finalizado.estornoSolicitado()).isTrue();
        assertThat(finalizado.estornoId()).isEqualTo("e-1");
        assertThat(inicial.status()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(inicial.provedorId()).isNull();
    }

    @Test
    void deveRejeitarSolicitacaoRepetidaComValorNulo() {
        assertThatThrownBy(() -> novo().validarMesmaSolicitacao(1L, null, "BRL"))
                .isInstanceOf(IllegalStateException.class);
    }

    private Pagamento novo() {
        return Pagamento.criar(UUID.randomUUID(), 1L, BigDecimal.TEN, "BRL");
    }
}
