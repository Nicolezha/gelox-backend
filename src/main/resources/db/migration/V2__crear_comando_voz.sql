CREATE TABLE IF NOT EXISTS comando_voz (
    id               UUID PRIMARY KEY,
    usuario_id       UUID NOT NULL REFERENCES usuario(id),
    texto_transcrito TEXT NOT NULL,
    confianza        NUMERIC(4,3) NOT NULL,
    intencion        VARCHAR(30),
    estado           VARCHAR(20) NOT NULL,
    respuesta        TEXT,
    created_at       TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_comando_voz_usuario_fecha
    ON comando_voz (usuario_id, created_at DESC);
