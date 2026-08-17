package br.com.ice.ebd.service;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.AlunoInativacao;
import br.com.ice.ebd.model.MotivoInativacao;
import br.com.ice.ebd.repository.AlunoInativacaoRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;

/**
 * Histórico de inativação de alunos. Os dois pontos que mudam {@code aluno.ativo} chamam
 * este serviço na <b>mesma transação</b> da operação: a inativação automática por faltas
 * seguidas ({@link ChamadaService}) e a edição do cadastro ({@link AlunoService}).
 * Registrar é idempotente: com um episódio já aberto, nada é criado.
 */
@ApplicationScoped
public class InativacaoService {

    @Inject SecurityIdentity identity;
    @Inject AlunoInativacaoRepository repository;

    /** Abre o episódio: o aluno acabou de ficar inativo. */
    public void registrarInativacao(Aluno aluno, MotivoInativacao motivo, Integer faltasSeguidas) {
        if (repository.abertoDoAluno(aluno.getId()).isPresent()) {
            return; // já está inativo no histórico — não duplica
        }
        AlunoInativacao i = new AlunoInativacao();
        i.setAluno(aluno);
        i.setInativadoEm(LocalDateTime.now());
        i.setMotivo(motivo);
        i.setFaltasSeguidas(faltasSeguidas);
        i.setInativadoPor(usuarioAtual());
        repository.persist(i);
    }

    /** Fecha o episódio aberto: o aluno voltou. Sem episódio aberto, não faz nada. */
    public void registrarReativacao(Aluno aluno) {
        repository.abertoDoAluno(aluno.getId()).ifPresent(i -> {
            i.setReativadoEm(LocalDateTime.now());
            i.setReativadoPor(usuarioAtual());
        });
    }

    private String usuarioAtual() {
        try {
            String u = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
            return (u == null || u.isBlank()) ? "sistema" : u;
        } catch (Exception e) {
            return "sistema";
        }
    }
}
