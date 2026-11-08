package com.dinoco.oficina.financeiro;

import com.dinoco.oficina.financeiro.infrastructure.security.ApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class ApiKeyFilterTest {
    @Test
    void protegeConsultaCriacaoEstornoEHealth() throws Exception {
        for (var path : new String[]{"/api/pagamentos", "/api/pagamentos/1/estornos", "/actuator/health/readiness"}) {
            var request = new MockHttpServletRequest("POST", path);
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            new ApiKeyFilter("segredo").doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
        }
    }

    @Test
    void permiteChaveCorreta() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/pagamentos");
        request.addHeader("X-API-Key", "segredo");
        var chain = new MockFilterChain();
        new ApiKeyFilter("segredo").doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isSameAs(request);
    }
    @Test
    void documentacaoAbreSemChaveMasNaoLiberaRotasParecidas() throws Exception {
        for (var path : new String[]{"/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs/swagger-config"}) {
            var request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            var chain = new MockFilterChain();
            new ApiKeyFilter("segredo").doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isSameAs(request);
        }
        var request = new MockHttpServletRequest("GET", "/v3/api-docs-privado");
        request.setServletPath("/v3/api-docs-privado");
        var response = new MockHttpServletResponse();
        new ApiKeyFilter("segredo").doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
