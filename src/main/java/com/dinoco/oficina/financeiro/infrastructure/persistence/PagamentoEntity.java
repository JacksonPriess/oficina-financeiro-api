package com.dinoco.oficina.financeiro.infrastructure.persistence;

import com.dinoco.oficina.financeiro.domain.StatusPagamento;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pagamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PagamentoEntity {
    @Id
    private UUID id;

    @Column(name = "saga_id", nullable = false, unique = true)
    private UUID sagaId;

    @Column(name = "os_id", nullable = false)
    private Long osId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 3)
    private String moeda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPagamento status;

    @Column(name = "provedor_id", length = 100)
    private String provedorId;

    @Column(name = "estorno_solicitado", nullable = false)
    private boolean estornoSolicitado;

    @Column(name = "estorno_id", length = 100)
    private String estornoId;
}
