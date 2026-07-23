package br.com.ice.ebd.service;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.UsuarioRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.text.Normalizer;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * Garante o acesso automático de todo aluno cadastrado ao sistema.
 *
 * <p>Regra: ao cadastrar um aluno, cria-se um usuário {@link Role#ALUNO} vinculado a ele,
 * com <b>senha padrão</b> e obrigado a trocá-la no 1º acesso ({@code precisaTrocarSenha}).
 * O login é derivado do nome (<i>nome.sobrenome</i>, sem acento, com sufixo numérico em colisão).
 * O usuário espelha o estado do aluno (ativo/e-mail); a senha nunca é redefinida aqui.</p>
 */
@ApplicationScoped
public class AcessoAlunoService {

    private static final Logger LOG = Logger.getLogger(AcessoAlunoService.class);

    /** Senha padrão do 1º acesso do aluno (trocada obrigatoriamente no login). */
    public static final String SENHA_PADRAO = "12345678";

    @Inject UsuarioRepository usuarioRepository;
    @Inject LoginService loginService;
    @Inject AlunoRepository alunoRepository;

    /**
     * Garante que o aluno tenha um login: cria o usuário se faltar e, de qualquer forma,
     * mantém {@code ativo}/e-mail em sincronia com o cadastro do aluno.
     */
    @Transactional
    public void sincronizarAcesso(Aluno a) {
        Usuario u = usuarioRepository.find("aluno.id", a.getId()).firstResult();
        if (u == null) {
            u = new Usuario();
            u.setUsername(gerarUsername(a.getNome()));
            u.setSenhaHash(BcryptUtil.bcryptHash(SENHA_PADRAO));
            u.setRole(Role.ALUNO);
            u.setAluno(a);
            u.setPrecisaTrocarSenha(true);
            usuarioRepository.persist(u);
            usuarioRepository.flush(); // torna o username visível para a próxima geração
            LOG.infof("Acesso criado para o aluno '%s' (login '%s').", a.getNome(), u.getUsername());
        }
        u.setAtivo(a.isAtivo());
        u.setEmail(a.getEmail());
    }

    /** Remove o login vinculado a um aluno (usado ao excluir o aluno). */
    @Transactional
    public void removerAcesso(Long alunoId) {
        usuarioRepository.delete("aluno.id", alunoId);
    }

    /**
     * Edita o login do usuário vinculado a um aluno. Idempotente: se o novo login for igual ao
     * atual, não faz nada. Valida formato e unicidade pelo {@link LoginService}.
     */
    @Transactional
    public void definirLogin(Long alunoId, String novoLoginRaw) {
        Usuario u = usuarioRepository.find("aluno.id", alunoId).firstResult();
        if (u == null) {
            return; // aluno sem login (não deveria ocorrer); nada a renomear
        }
        String novo = loginService.normalizar(novoLoginRaw);
        if (novo.equals(u.getUsername())) {
            return; // sem mudança
        }
        loginService.validarFormato(novo);
        loginService.validarUnico(novo, u.getId());
        u.setUsername(novo);
    }

    /** Backfill idempotente: cria login para todo aluno que ainda não tem. Retorna quantos criou. */
    @Transactional
    public int garantirAcessoParaTodos() {
        Set<Long> comUsuario = usuarioRepository.alunoIdsComUsuario();
        int criados = 0;
        for (Aluno a : alunoRepository.listAll()) {
            if (!comUsuario.contains(a.getId())) {
                sincronizarAcesso(a);
                criados++;
            }
        }
        if (criados > 0) {
            LOG.infof("Acesso de aluno criado para %d aluno(s) sem login.", criados);
        }
        return criados;
    }

    /** Gera um login único no formato nome.sobrenome (sem acento), com sufixo numérico em colisão. */
    String gerarUsername(String nome) {
        String base = slug(nome);
        String candidato = base;
        int n = 2;
        while (usuarioRepository.findByUsername(candidato).isPresent()) {
            candidato = base + n++;
        }
        return candidato;
    }

    private String slug(String nome) {
        String limpo = Normalizer.normalize(nome == null ? "" : nome.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")        // remove acentos
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")   // só letras/números/espaço
                .trim();
        String[] partes = limpo.isBlank() ? new String[0] : limpo.split("\\s+");
        String base;
        if (partes.length == 0) {
            base = "aluno";
        } else if (partes.length == 1) {
            base = partes[0];
        } else {
            base = partes[0] + "." + partes[partes.length - 1];
        }
        if (base.length() > 55) {  // deixa folga para o sufixo (username <= 60)
            base = base.substring(0, 55);
        }
        return base;
    }
}
