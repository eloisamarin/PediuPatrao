package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Usuario;

/**
 * Dados aceitos no cadastro/edição de usuário (tela e API): username, e-mail, senha e perfil.
 *
 * Não contém id, hash de senha nem outros campos internos: assim uma requisição não consegue
 * sobrescrever campos que não deveria (mass assignment). Na edição, o username é ignorado.
 */
public class UsuarioForm {

    private String username;
    private String password;
    private String role;
    private String email;

    public static UsuarioForm de(Usuario usuario) {
        UsuarioForm form = new UsuarioForm();
        form.setUsername(usuario.getUsername());
        form.setRole(usuario.getRole());
        form.setEmail(usuario.getEmail());
        return form; // a senha nunca é preenchida de volta
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    // Nunca imprime a senha (ex.: em logs de depuração)
    @Override
    public String toString() {
        return "UsuarioForm[username=" + username + ", email=" + email + ", role=" + role + ", password=***]";
    }
}
