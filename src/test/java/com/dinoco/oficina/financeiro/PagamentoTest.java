package com.dinoco.oficina.financeiro;

import com.dinoco.oficina.financeiro.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PagamentoTest {
    private Pagamento pagamento() { return Pagamento.criar(UUID.randomUUID(), 1L, new BigDecimal("125.50"), "BRL"); }

    @Test
    void criaPendenteSemProvedor() {
        var p = pagamento();
        assertThat(p.status()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(p.provedorId()).isNull();
        assertThat(p.estornoSolicitado()).isFalse();
    }

    @Test
    void rejeitaValorInvalido() {
        for (var valor : new String[]{"0", "-1", "1.001"})
            assertThatThrownBy(() -> Pagamento.criar(UUID.randomUUID(), 1L, new BigDecimal(valor), "BRL"))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaMudancaDeDadosNaMesmaSaga() {
        var p = pagamento();
        assertThatThrownBy(() -> p.validarMesmaSolicitacao(2L, p.valor(), "BRL")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> p.validarMesmaSolicitacao(1L, BigDecimal.TEN, "BRL")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> p.validarMesmaSolicitacao(1L, p.valor(), "USD")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void naoAprovaAntesDeCriarNoProvedor() {
        assertThatThrownBy(() -> pagamento().aprovar()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void naoEstornaPagamentoPendente() {
        assertThatThrownBy(() -> pagamento().solicitarEstorno()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registraEstornoSeparadoDaAprovacao() {
        var p = pagamento().vincular("externo-1").aprovar().solicitarEstorno().estornar("estorno-1");
        assertThat(p.status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(p.estornoId()).isEqualTo("estorno-1");
    }
}
