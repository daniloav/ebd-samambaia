package br.com.ice.ebd.service;

import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Regras únicas do <b>login</b> (username) de qualquer usuário — usadas tanto na tela de
 * Usuários quanto ao editar o login pelo cadastro do aluno.
 *
 * <ul>
 *   <li><b>Normaliza</b>: remove espaços das pontas e passa para minúsculas.</li>
 *   <li><b>Formato</b>: só letras minúsculas, números e separadores simples ({@code . - _}),
 *       sem espaço/acento, sem começar/terminar com separador nem repeti-lo (ex.: {@code joao.silva}).</li>
 *   <li><b>Tamanho</b>: entre {@value #MIN} e {@value #MAX} caracteres.</li>
 *   <li><b>Único</b>: não pode colidir com o login de outro usuário.</li>
 * </ul>
 */
@ApplicationScoped
public class LoginService {

    public static final int MIN = 3;
    public static final int MAX = 60;

    /** blocos alfanuméricos separados por um único . - _ (sem pontas/duplos separadores). */
    private static final Pattern FORMATO = Pattern.compile("[a-z0-9]+([._-][a-z0-9]+)*");

    @Inject UsuarioRepository usuarioRepository;

    /** Apenas normaliza (trim + minúsculas), sem validar. */
    public String normalizar(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    /** Valida o formato/tamanho de um login já normalizado (400 se inválido). */
    public void validarFormato(String login) {
        if (login == null || login.isBlank()) {
            throw bad("Informe o login.");
        }
        if (login.length() < MIN || login.length() > MAX) {
            throw bad("O login deve ter entre " + MIN + " e " + MAX + " caracteres.");
        }
        if (!FORMATO.matcher(login).matches()) {
            throw bad("O login deve usar apenas letras minúsculas, números, ponto, hífen ou "
                    + "sublinhado — sem espaços ou acentos (ex.: joao.silva).");
        }
    }

    /** Garante que nenhum outro usuário use este login (409 em caso de colisão). */
    public void validarUnico(String login, Long usuarioIdAtual) {
        Optional<Usuario> existente = usuarioRepository.findByUsername(login);
        if (existente.isPresent() && !existente.get().getId().equals(usuarioIdAtual)) {
            throw new WebApplicationException("Já existe um usuário com o login \"" + login + "\".",
                    Response.Status.CONFLICT);
        }
    }

    /**
     * Normaliza, valida formato e unicidade; devolve o login pronto para gravar.
     * {@code usuarioIdAtual} é o id do próprio usuário (para não colidir consigo mesmo) ou {@code null} na criação.
     */
    public String preparar(String raw, Long usuarioIdAtual) {
        String login = normalizar(raw);
        validarFormato(login);
        validarUnico(login, usuarioIdAtual);
        return login;
    }

    private WebApplicationException bad(String msg) {
        return new WebApplicationException(msg, Response.Status.BAD_REQUEST);
    }
}
