package br.com.ice.ebd.bootstrap;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.ClasseRepository;
import br.com.ice.ebd.repository.UsuarioRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Cria os usuários padrão e alguns alunos de exemplo no primeiro boot. */
@ApplicationScoped
public class DataInitializer {

    private static final Logger LOG = Logger.getLogger(DataInitializer.class);

    @Inject UsuarioRepository usuarioRepository;
    @Inject AlunoRepository alunoRepository;
    @Inject ClasseRepository classeRepository;
    @Inject br.com.ice.ebd.service.AcessoAlunoService acessoAluno;

    @ConfigProperty(name = "ebd.seed.admin.username") String adminUser;
    @ConfigProperty(name = "ebd.seed.admin.password") String adminPass;
    @ConfigProperty(name = "ebd.seed.professor.username") String profUser;
    @ConfigProperty(name = "ebd.seed.professor.password") String profPass;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        seedUsuarios();
        seedAlunosExemplo();
        acessoAluno.garantirAcessoParaTodos(); // todo aluno cadastrado tem login (backfill idempotente)
    }

    private void seedUsuarios() {
        if (usuarioRepository.count() > 0) {
            return;
        }
        criarUsuario(adminUser, adminPass, true, false);
        criarUsuario(profUser, profPass, false, true);
        LOG.infof("Usuários padrão criados: admin='%s', professor='%s' (troque as senhas!)",
                adminUser, profUser);
    }

    private void criarUsuario(String username, String senha, boolean admin, boolean professor) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setSenhaHash(BcryptUtil.bcryptHash(senha));
        u.setEhAdmin(admin);
        u.setEhProfessor(professor);
        u.setAtivo(true);
        usuarioRepository.persist(u);
    }

    private void seedAlunosExemplo() {
        if (alunoRepository.count() > 0) {
            return;
        }
        Classe classe = classeRepository.listarAtivas().stream().findFirst().orElseGet(() -> {
            Classe c = new Classe();
            c.setNome("Adultos");
            c.setDescricao("Classe de adultos");
            c.setAtivo(true);
            classeRepository.persist(c);
            return c;
        });
        List<String> nomes = List.of(
                "Ana Beatriz Souza", "Carlos Eduardo Lima", "Débora Martins",
                "Fernando Alves", "Joana Ribeiro", "Marcos Vinícius Silva");
        for (String nome : nomes) {
            Aluno a = new Aluno();
            a.setNome(nome);
            a.setAtivo(true);
            a.setClasse(classe);
            alunoRepository.persist(a);
        }
        LOG.infof("%d alunos de exemplo criados.", nomes.size());
    }
}
