package com.dinoco.oficina.financeiro.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CriarPagamentoRequest(@Schema(example = "b1000000-0000-4000-8000-000000000001") @NotNull UUID sagaId,
                                    @Schema(example = "1") @NotNull @Positive Long osId,
                                    @Schema(example = "250.50") @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal valor,
                                    @Schema(example = "BRL") @NotNull @Pattern(regexp = "BRL") String moeda) {
}
