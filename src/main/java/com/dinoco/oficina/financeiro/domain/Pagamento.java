package com.dinoco.oficina.financeiro.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Pagamento(UUID id, UUID sagaId, Long osId, BigDecimal valor, String moeda,
                        StatusPagamento status, String provedorId, boolean estornoSolicitado, String estornoId) {

    public Pagamento {
        if (id == null || sagaId == null || osId == null || osId <= 0)
            throw new IllegalArgumentException("Identificadores do pagamento são obrigatórios.");

        if (valor == null || valor.signum() <= 0 || valor.stripTrailingZeros().scale() > 2 || valor.compareTo(new BigDecimal("99999999999999999.99")) > 0)
            throw new IllegalArgumentException("Valor deve ser positivo e possuir no máximo duas casas decimais.");

        if (!"BRL".equals(moeda) || status == null)
            throw new IllegalArgumentException("Moeda deve ser BRL e status é obrigatório.");
    }

    public static Pagamento criar(UUID sagaId, Long osId, BigDecimal valor, String moeda) {
        return new Pagamento(UUID.randomUUID(), sagaId, osId, valor, moeda, StatusPagamento.PENDENTE, null, false, null);
    }

    public void validarMesmaSolicitacao(Long osId, BigDecimal valor, String moeda) {
        if (!this.osId.equals(osId) || valor == null || this.valor.compareTo(valor) != 0 || !this.moeda.equals(moeda))
            throw new IllegalStateException("Saga já possui pagamento com dados diferentes.");
    }

    public Pagamento vincular(String provedorId) {
        if (provedorId == null || provedorId.isBlank())
            throw new IllegalArgumentException("Identificador do provedor ausente.");
        return new Pagamento(id, sagaId, osId, valor, moeda, status, provedorId, estornoSolicitado, estornoId);
    }

    public Pagamento aprovar() {
        if (provedorId == null) throw new IllegalStateException("Pagamento ainda não criado no provedor.");
        return new Pagamento(id, sagaId, osId, valor, moeda, StatusPagamento.APROVADO, provedorId, estornoSolicitado, estornoId);
    }

    public Pagamento solicitarEstorno() {
        if (status != StatusPagamento.APROVADO)
            throw new IllegalStateException("Somente pagamento aprovado permite estorno.");
        return new Pagamento(id, sagaId, osId, valor, moeda, status, provedorId, true, estornoId);
    }

    public Pagamento estornar(String identificador) {
        if (!estornoSolicitado || identificador == null || identificador.isBlank())
            throw new IllegalStateException("Estorno não solicitado ou confirmação inválida.");
        return new Pagamento(id, sagaId, osId, valor, moeda, status, provedorId, true, identificador);
    }
}
