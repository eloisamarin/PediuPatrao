package com.umc.pediupatrao.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pedido de redefinição de senha ("Esqueci a senha").
 *
 * O token que vai no link do e-mail NUNCA é gravado: só o seu hash SHA-256. Assim, mesmo quem tiver
 * acesso ao banco não consegue usar os links. Cada token vale uma única vez e expira em 30 minutos.
 * Um índice TTL no MongoDB (criado no MongoInitConfig) apaga os registros vencidos automaticamente.
 */
@Document(collection = "tokensRedefinicaoSenha")
public class TokenRedefinicaoSenha {

    @Id
    private String id;
    private String usuarioId;
    private String tokenHash;   // SHA-256 (hex) do token enviado por e-mail
    private Instant criadoEm;
    private Instant expiraEm;
    private Instant usadoEm;    // preenchido quando a senha é redefinida (uso único)

    public boolean isValido(Instant agora) {
        return usadoEm == null && expiraEm != null && agora.isBefore(expiraEm);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }

    public Instant getExpiraEm() { return expiraEm; }
    public void setExpiraEm(Instant expiraEm) { this.expiraEm = expiraEm; }

    public Instant getUsadoEm() { return usadoEm; }
    public void setUsadoEm(Instant usadoEm) { this.usadoEm = usadoEm; }
}
