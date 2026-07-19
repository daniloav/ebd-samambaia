import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { Classe } from './models';

const CHAVE = 'ebd_classe';

/** Guarda a classe (turma) atualmente selecionada, compartilhada por todas as telas. */
@Injectable({ providedIn: 'root' })
export class ClasseContextService {
  private api = inject(ApiService);

  readonly classes = signal<Classe[]>([]);
  readonly selecionadaId = signal<number | null>(
    localStorage.getItem(CHAVE) ? Number(localStorage.getItem(CHAVE)) : null
  );

  carregar(): void {
    this.api.listarClasses(true).subscribe({
      next: (cs) => {
        this.classes.set(cs);
        const atual = this.selecionadaId();
        if (!atual || !cs.some((c) => c.id === atual)) {
          this.selecionar(cs.length ? cs[0].id : null);
        }
      },
      error: () => {},
    });
  }

  selecionar(id: number | null): void {
    this.selecionadaId.set(id);
    if (id) {
      localStorage.setItem(CHAVE, String(id));
    } else {
      localStorage.removeItem(CHAVE);
    }
  }

  nomeSelecionada(): string {
    const id = this.selecionadaId();
    return this.classes().find((c) => c.id === id)?.nome ?? '';
  }
}
