CREATE TABLE pagamento (
    id UUID PRIMARY KEY,
    saga_id UUID NOT NULL UNIQUE,
    os_id BIGINT NOT NULL CHECK (os_id > 0),
    valor NUMERIC(19,2) NOT NULL CHECK (valor > 0),
    moeda VARCHAR(3) NOT NULL CHECK (moeda = 'BRL'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE', 'APROVADO')),
    provedor_id VARCHAR(100) UNIQUE,
    estorno_solicitado BOOLEAN NOT NULL DEFAULT FALSE,
    estorno_id VARCHAR(100) UNIQUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    proxima_consulta TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tentativas INTEGER NOT NULL DEFAULT 0,
    CHECK (status = 'PENDENTE' OR provedor_id IS NOT NULL),
    CHECK (NOT estorno_solicitado OR status = 'APROVADO'),
    CHECK (estorno_id IS NULL OR estorno_solicitado)
);
CREATE UNIQUE INDEX pagamento_os_ativo ON pagamento(os_id) WHERE estorno_id IS NULL;
CREATE INDEX pagamento_pendente ON pagamento(proxima_consulta)
    WHERE status = 'PENDENTE' OR (estorno_solicitado AND estorno_id IS NULL);

CREATE TABLE financeiro_outbox (
    event_id UUID PRIMARY KEY,
    pagamento_id UUID NOT NULL REFERENCES pagamento(id),
    tipo VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    publicado_em TIMESTAMPTZ,
    proxima_tentativa TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tentativas INTEGER NOT NULL DEFAULT 0,
    UNIQUE (pagamento_id, tipo)
);

CREATE INDEX financeiro_outbox_pendente ON financeiro_outbox(proxima_tentativa) WHERE publicado_em IS NULL;
