import { Injectable, signal } from '@angular/core';

export interface Toast {
  texto: string;
  tipo: 'ok' | 'erro';
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly atual = signal<Toast | null>(null);
  private timer?: ReturnType<typeof setTimeout>;

  sucesso(texto: string): void {
    this.mostrar({ texto, tipo: 'ok' });
  }

  erro(texto: string): void {
    this.mostrar({ texto, tipo: 'erro' });
  }

  private mostrar(t: Toast): void {
    this.atual.set(t);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.atual.set(null), 3500);
  }
}
