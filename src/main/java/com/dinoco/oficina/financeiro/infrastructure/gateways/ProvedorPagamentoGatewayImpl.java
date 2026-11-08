package com.dinoco.oficina.financeiro.infrastructure.gateways;

import com.dinoco.oficina.financeiro.application.gateways.ProvedorPagamentoGateway;
import com.dinoco.oficina.financeiro.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Component
public class ProvedorPagamentoGatewayImpl implements ProvedorPagamentoGateway {
    private final RestClient client;
    private final String clientId;
    private final String clientSecret;

    public ProvedorPagamentoGatewayImpl(@Value("${financeiro.provedor.url}") String url,
                                        @Value("${financeiro.provedor.client-id}") String clientId,
                                        @Value("${financeiro.provedor.client-secret}") String clientSecret) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    private String token() {
        var response = client.post().uri("/oauth/token").body(Map.of("client_id", clientId, "client_secret", clientSecret))
                .retrieve().body(Token.class);
        if (response == null || response.access_token() == null || response.access_token().isBlank())
            throw new IllegalStateException("Provedor não retornou token.");
        return response.access_token();
    }

    public String criar(Pagamento p) {
        var response = client.post().uri("/pagamentos").headers(h -> h.setBearerAuth(token()))
                .header("Idempotency-Key", p.sagaId().toString())
                .body(Map.of("referencia", p.sagaId().toString(), "osId", p.osId(), "valor", p.valor(), "moeda", p.moeda()))
                .retrieve().body(Resposta.class);
        return validar(response, p).id();
    }

    public StatusPagamento consultar(Pagamento p) {
        var response = client.get().uri("/pagamentos/{id}", p.provedorId()).headers(h -> h.setBearerAuth(token()))
                .retrieve().body(Resposta.class);
        validar(response, p);
        if (!p.provedorId().equals(response.id())) throw new IllegalStateException("Identificador divergente no provedor.");
        return StatusPagamento.valueOf(response.status());
    }

    public String estornar(Pagamento p) {
        var response = client.post().uri("/pagamentos/{id}/estornos", p.provedorId()).headers(h -> h.setBearerAuth(token()))
                .header("Idempotency-Key", "estorno:" + p.sagaId()).body(Map.of("valor", p.valor()))
                .retrieve().body(Estorno.class);
        if (response == null || !p.provedorId().equals(response.pagamentoId()) || response.id() == null)
            throw new IllegalStateException("Confirmação de estorno inválida.");
        return response.id();
    }

    private Resposta validar(Resposta response, Pagamento p) {
        if (response == null || response.id() == null || !p.sagaId().toString().equals(response.referencia())
                || response.valor() == null || p.valor().compareTo(response.valor()) != 0 || !p.moeda().equals(response.moeda()))
            throw new IllegalStateException("Dados do pagamento divergentes no provedor.");
        return response;
    }

    public record Token(String access_token, long expires_in, String token_type) {}
    public record Resposta(String id, String referencia, java.math.BigDecimal valor, String moeda, String status, String estornoId) {}
    public record Estorno(String id, String pagamentoId) {}
}
