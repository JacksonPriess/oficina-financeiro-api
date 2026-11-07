package com.dinoco.oficina;

import com.dinoco.oficina.financeiro.application.gateways.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.criar.*;
import com.dinoco.oficina.financeiro.application.usecases.commands.estornar.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.profiles.active=local", "financeiro.workers.enabled=false"})
@EnabledIfEnvironmentVariable(named="FINANCEIRO_INTEGRATION_TESTS", matches="true")
class FinanceiroApiApplicationTests {
    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    private int port;
    @Autowired CriarPagamentoUseCase criar;
    @Autowired EstornarPagamentoUseCase estornar;
    @Autowired PagamentoGateway gateway;
    @Autowired PagamentoEventPublisher eventos;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.dinoco.oficina.financeiro.infrastructure.persistence.PagamentoJpaRepository repository;

    @BeforeEach
    void limparBancoDeTeste() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).endsWith("_test");
        jdbc.execute("TRUNCATE financeiro_outbox, pagamento");
    }

    @Test
    void migrationsEIdempotenciaComPostgresReal() {
        var command = new CriarPagamentoCommand(UUID.randomUUID(), 1L, new BigDecimal("100.50"), "BRL");
        var primeiro = criar.executar(command);
        assertThat(criar.executar(command).id()).isEqualTo(primeiro.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pagamento", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> criar.executar(new CriarPagamentoCommand(command.sagaId(), 1L, BigDecimal.TEN, "BRL")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(gateway.buscarPorSaga(command.sagaId()).orElseThrow().valor()).isEqualByComparingTo("100.50");
    }

    @Test
    void rollbackRemovePagamentoEEventoJuntos() {
        var tx = new TransactionTemplate(manager);
        var saga = UUID.randomUUID();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            var p = criar.executar(new CriarPagamentoCommand(saga, 2L, BigDecimal.TEN, "BRL"));
            eventos.publicar("pagamento.criado.v1", p);
            throw new IllegalStateException("falha simulada");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(gateway.buscarPorSaga(saga)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financeiro_outbox", Integer.class)).isZero();
    }

    @Test
    void duasSagasNaoCriamDuasCobrancasAtivasParaMesmaOs() {
        criar.executar(new CriarPagamentoCommand(UUID.randomUUID(), 3L, BigDecimal.TEN, "BRL"));
        assertThatThrownBy(() -> criar.executar(new CriarPagamentoCommand(UUID.randomUUID(), 3L, BigDecimal.TEN, "BRL")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pagamento", Integer.class)).isEqualTo(1);
    }

    @Test
    void estornoPendenteNaoApagaPagamentoEEventoNaoDuplica() {
        var p = criar.executar(new CriarPagamentoCommand(UUID.randomUUID(), 4L, BigDecimal.TEN, "BRL"));
        var tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            var aprovado = p.vincular("provedor-4").aprovar();
            gateway.salvar(aprovado);
            eventos.publicar("pagamento.aprovado.v1", aprovado);
            eventos.publicar("pagamento.aprovado.v1", aprovado);
        });
        estornar.executar(p.sagaId());
        estornar.executar(p.sagaId());
        assertThat(gateway.buscarPorSaga(p.sagaId()).orElseThrow().estornoSolicitado()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financeiro_outbox", Integer.class)).isEqualTo(1);
    }

    @Test
    void falhaDepoisDoEfeitoExternoReverteDadosLocaisEAgendaRecuperacao() {
        var p = criar.executar(new CriarPagamentoCommand(UUID.randomUUID(), 5L, BigDecimal.TEN, "BRL"));
        var provedor = org.mockito.Mockito.mock(ProvedorPagamentoGateway.class);
        org.mockito.Mockito.when(provedor.criar(org.mockito.ArgumentMatchers.any())).thenReturn("externo-5");
        PagamentoEventPublisher falhaDepoisDoEvento = (tipo, pagamento) -> {
            eventos.publicar(tipo, pagamento);
            throw new IllegalStateException("falha depois de inserir outbox");
        };
        var handler = new com.dinoco.oficina.financeiro.application.usecases.commands.processar.ProcessarPagamentoHandler(
                gateway, provedor, falhaDepoisDoEvento);
        var worker = new com.dinoco.oficina.financeiro.infrastructure.jobs.PagamentoWorker(manager, gateway, handler, repository);
        worker.processar();
        assertThat(gateway.buscarPorSaga(p.sagaId()).orElseThrow().provedorId()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financeiro_outbox", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT tentativas FROM pagamento WHERE id=?", Integer.class, p.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT proxima_consulta > CURRENT_TIMESTAMP FROM pagamento WHERE id=?", Boolean.class, p.id())).isTrue();
    }

    @Test
    void swaggerGeraContratoComApiKeyESemExporOperacoesAnonimas() throws Exception {
        var http = java.net.http.HttpClient.newHttpClient();
        var base = "http://127.0.0.1:" + port;
        var docs = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/v3/api-docs")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(docs.statusCode()).isEqualTo(200);
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(docs.body());
        assertThat(json.at("/components/securitySchemes/FinanceiroApiKey/name").asText()).isEqualTo("X-API-Key");
        assertThat(json.at("/paths/~1api~1pagamentos/post/tags/0").asText()).isEqualTo("Financeiro");
        assertThat(json.at("/paths/~1api~1pagamentos/post/responses/202").isMissingNode()).isFalse();
        var ui = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/swagger-ui/index.html")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(ui.statusCode()).isEqualTo(200);
        assertThat(ui.body()).contains("Swagger UI");
        var blocked = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/pagamentos/" + UUID.randomUUID())).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(blocked.statusCode()).isEqualTo(401);
    }
}
