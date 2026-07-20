package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.MinhaFrequenciaResponse;
import br.com.ice.ebd.dto.MinhaFrequenciaResponse.Item;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Presenca;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.PresencaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Frequência do próprio aluno logado. O ALUNO só enxerga o seu cadastro/presenças —
 * nunca os de outros alunos (o id do aluno vem do vínculo do usuário, não do request).
 */
@ApplicationScoped
public class MinhaFrequenciaService {

    @Inject EscopoService escopo;
    @Inject AlunoRepository alunoRepository;
    @Inject PresencaRepository presencaRepository;

    @Transactional
    public MinhaFrequenciaResponse minha() {
        Long alunoId = escopo.alunoIdLogado();
        if (alunoId == null) {
            throw new ForbiddenException("Seu usuário não está vinculado a um aluno.");
        }
        Aluno aluno = alunoRepository.findById(alunoId);
        if (aluno == null) {
            throw new NotFoundException("Aluno não encontrado.");
        }

        List<Presenca> presencas = new ArrayList<>(presencaRepository.listarPorAluno(alunoId));
        presencas.sort(Comparator.comparing((Presenca p) -> p.getAula().getData()).reversed());

        List<Item> itens = new ArrayList<>();
        int presentes = 0;
        for (Presenca p : presencas) {
            itens.add(new Item(
                    p.getAula().getData(), p.getAula().getTema(), p.isPresente(),
                    p.isTrouxeBiblia(), p.isTrouxeRevista(), p.isEstudouLicao()));
            if (p.isPresente()) {
                presentes++;
            }
        }
        int total = presencas.size();
        int faltas = total - presentes;
        int percentual = total > 0 ? Math.round(presentes * 100f / total) : 0;
        return new MinhaFrequenciaResponse(aluno.getNome(), total, presentes, faltas, percentual, itens);
    }
}
