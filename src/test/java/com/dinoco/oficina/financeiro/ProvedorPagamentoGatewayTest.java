package com.dinoco.oficina.financeiro;

import com.dinoco.oficina.financeiro.domain.*;
import com.dinoco.oficina.financeiro.infrastructure.gateways.ProvedorPagamentoGatewayImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ProvedorPagamentoGatewayTest {
    @Test
    void autenticaComTokenDoProvedorEValidaPagamento() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var autenticacoes = new AtomicInteger();
        var chamadasAutorizadas = new AtomicInteger();
        var p = Pagamento.criar(UUID.randomUUID(), 1L, new BigDecimal("10.00"), "BRL");
        server.createContext("/oauth/token", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("client_id") && body.contains("client_secret")) autenticacoes.incrementAndGet();
            var response = "{\"access_token\":\"token-provedor\",\"expires_in\":300,\"token_type\":\"Bearer\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/pagamentos", exchange -> {
            if ("Bearer token-provedor".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    && p.sagaId().toString().equals(exchange.getRequestHeaders().getFirst("Idempotency-Key")))
                chamadasAutorizadas.incrementAndGet();
            var response = ("{\"id\":\"externo-1\",\"referencia\":\"" + p.sagaId()
                    + "\",\"valor\":\"10.00\",\"moeda\":\"BRL\",\"status\":\"PENDENTE\",\"estornoId\":null}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new ProvedorPagamentoGatewayImpl("http://127.0.0.1:" + server.getAddress().getPort(), "cliente", "segredo");
            assertThat(gateway.criar(p)).isEqualTo("externo-1");
            assertThat(autenticacoes.get()).isEqualTo(1);
            assertThat(chamadasAutorizadas.get()).isEqualTo(1);
            assertThatThrownBy(() -> gateway.criar(Pagamento.criar(UUID.randomUUID(), 1L, BigDecimal.TEN, "BRL")))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            server.stop(0);
        }
    }
}
