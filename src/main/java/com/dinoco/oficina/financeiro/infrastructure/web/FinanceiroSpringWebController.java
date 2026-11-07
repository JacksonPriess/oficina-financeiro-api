package com.dinoco.oficina.financeiro.infrastructure.web;

import com.dinoco.oficina.financeiro.application.usecases.commands.criar.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.estornar.EstornarPagamentoUseCase;
import com.dinoco.oficina.financeiro.application.usecases.queries.buscar.BuscarPagamentoUseCase;
import com.dinoco.oficina.financeiro.controllers.FinanceiroControllerClean;
import com.dinoco.oficina.financeiro.domain.Pagamento;
import com.dinoco.oficina.financeiro.infrastructure.web.dto.CriarPagamentoRequest;
import com.dinoco.oficina.financeiro.infrastructure.web.dto.PagamentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

@Tag(name = "Financeiro", description = "Controle de pagamentos")
@SecurityRequirement(name = "FinanceiroApiKey")
@RestController
@RequestMapping("/api/pagamentos")
public class FinanceiroSpringWebController {
    private final FinanceiroControllerClean controller;

    public FinanceiroSpringWebController(CriarPagamentoUseCase criar, BuscarPagamentoUseCase buscar, EstornarPagamentoUseCase estornar) {
        this.controller = new FinanceiroControllerClean(criar, buscar, estornar);
    }

    @Operation(summary = "Solicitar pagamento", description = "Registra a solicitação por sagaId. O provedorId pode permanecer nulo até o processamento assíncrono.")
    @ApiResponse(responseCode = "202", description = "Solicitação registrada ou já existente")
    @ApiResponse(responseCode = "409", description = "Dados conflitantes ou outra cobrança ativa para a OS")
    @PostMapping
    public ResponseEntity<PagamentoResponse> criar(@Valid @RequestBody CriarPagamentoRequest request) {
        CriarPagamentoCommand command = new CriarPagamentoCommand(request.sagaId(), request.osId(), request.valor(), request.moeda());
        var pagamento = controller.criar(command);
        return ResponseEntity.accepted().location(URI.create("/api/pagamentos/" + pagamento.sagaId())).body(PagamentoResponse.de(pagamento));
    }

    @Operation(summary = "Consultar pagamento por sagaId", description = "APROVADO com estorno solicitado ou confirmado não autoriza a entrega da OS.")
    @ApiResponse(responseCode = "200", description = "Situação atual do pagamento")
    @ApiResponse(responseCode = "404", description = "Pagamento não encontrado")
    @GetMapping("/{sagaId}")
    public PagamentoResponse buscar(@PathVariable UUID sagaId) {
        return PagamentoResponse.de(controller.buscar(sagaId));
    }

    @Operation(summary = "Solicitar estorno integral", description = "Exige pagamento aprovado. Acompanhe estornoId para confirmar a execução no provedor.")
    @ApiResponse(responseCode = "202", description = "Estorno solicitado")
    @ApiResponse(responseCode = "409", description = "Pagamento não permite estorno")
    @PostMapping("/{sagaId}/estornos")
    public ResponseEntity<Void> estornar(@PathVariable UUID sagaId) {
        controller.estornar(sagaId);
        return ResponseEntity.accepted().build();
    }

}