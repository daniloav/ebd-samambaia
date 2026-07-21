import { Injectable, signal } from '@angular/core';

export interface ConfirmOpts {
  titulo?: string;
  mensagem: string;
  confirmar?: string;
  cancelar?: string;
  perigo?: boolean;
}

type Pendente = ConfirmOpts & { resolver: (v: boolean) => void };

/** Confirmação no padrão visual do app (substitui o confirm() nativo). */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly atual = signal<Pendente | null>(null);

  pedir(opts: ConfirmOpts): Promise<boolean> {
    return new Promise<boolean>((resolver) => this.atual.set({ ...opts, resolver }));
  }

  responder(valor: boolean): void {
    const a = this.atual();
    if (a) {
      this.atual.set(null);
      a.resolver(valor);
    }
  }
}
