package br.com.ice.ebd;

import br.com.ice.ebd.dto.AlunoRequest;
import br.com.ice.ebd.dto.AlunoResponse;
import br.com.ice.ebd.model.AcaoAuditoria;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Auditoria;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.EntidadeAuditoria;
import br.com.ice.ebd.repository.AuditoriaRepository;
import br.com.ice.ebd.service.AlunoService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class AuditoriaServiceTest {

    @Inject AlunoService alunoService;
    @Inject AuditoriaRepository auditoriaRepository;
    @Inject Fixtures fx;

    private List<Auditoria> daAluno(Long id) {
        return auditoriaRepository.list("entidade = ?1 and entidadeId = ?2", EntidadeAuditoria.ALUNO, id);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void criarAlunoRegistraAuditoriaComQuemERotulo() {
        Classe c = fx.classe("Turma Auditoria");
        AlunoRequest req = new AlunoRequest("Novo Aluno", c.getId(), null, null, null, false, true, null);

        AlunoResponse resp = alunoService.criar(req);

        List<Auditoria> regs = daAluno(resp.id());
        assertEquals(1, regs.size());
        assertEquals(AcaoAuditoria.CRIAR, regs.get(0).getAcao());
        assertEquals("admin", regs.get(0).getUsuario());
        assertEquals("Novo Aluno", regs.get(0).getDescricao());
    }

    @Test
    @TestSecurity(user = "profa", roles = {"PROFESSOR", "ADMIN"})
    @TestTransaction
    void excluirAlunoRegistraAuditoriaExcluir() {
        Classe c = fx.classe("Turma Auditoria 2");
        Aluno a = fx.aluno("Aluno A Excluir", c, null, false); // sem usuário vinculado gerenciado

        alunoService.deletar(a.getId());

        List<Auditoria> regs = daAluno(a.getId());
        assertEquals(1, regs.size());
        assertEquals(AcaoAuditoria.EXCLUIR, regs.get(0).getAcao());
        assertEquals("profa", regs.get(0).getUsuario());
        assertEquals("Aluno A Excluir", regs.get(0).getDescricao());
    }
}
