import { Injectable, signal } from '@angular/core';

export type Tema = 'claro' | 'escuro' | 'auto';
const CHAVE = 'ebd_tema';

/** Tema claro/escuro. 'auto' segue o sistema (prefers-color-scheme). */
@Injectable({ providedIn: 'root' })
export class TemaService {
  readonly tema = signal<Tema>((localStorage.getItem(CHAVE) as Tema) || 'auto');

  constructor() {
    this.aplicar(this.tema());
  }

  definir(t: Tema): void {
    this.tema.set(t);
    localStorage.setItem(CHAVE, t);
    this.aplicar(t);
  }

  /** Alterna claro ↔ escuro (resolve o 'auto' pelo sistema antes de inverter). */
  alternar(): void {
    this.definir(this.escuroEfetivo() ? 'claro' : 'escuro');
  }

  escuroEfetivo(): boolean {
    const t = this.tema();
    if (t === 'auto') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches;
    }
    return t === 'escuro';
  }

  private aplicar(t: Tema): void {
    const root = document.documentElement;
    if (t === 'auto') {
      root.removeAttribute('data-tema');
    } else {
      root.setAttribute('data-tema', t);
    }
  }
}
