import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';

/**
 * Instrumentação de uso de funcionalidade (item D do painel /uso). Envia, sem bloquear a UI,
 * um evento de "abrir tela" (page view) ou "clique notável" (export, WhatsApp...). Erros são
 * engolidos — telemetria nunca deve atrapalhar a navegação. Page views repetidos seguidos do
 * mesmo recurso são deduplicados (evita contar 2× a mesma tela numa navegação só).
 */
@Injectable({ providedIn: 'root' })
export class TelemetriaService {
  private api = inject(ApiService);
  private ultimaPagina = '';

  /** Registra a abertura de uma tela. Ignora recurso vazio e repetição imediata. */
  pagina(recurso: string): void {
    if (!recurso || recurso === this.ultimaPagina) {
      return;
    }
    this.ultimaPagina = recurso;
    this.enviar(recurso, 'ABRIR');
  }

  /** Registra um clique notável (ex.: export-pdf, whatsapp-parabenizar). */
  clique(recurso: string): void {
    if (!recurso) {
      return;
    }
    this.enviar(recurso, 'CLICAR');
  }

  private enviar(recurso: string, acao: 'ABRIR' | 'CLICAR'): void {
    this.api.evento(recurso, acao).subscribe({ error: () => {} });
  }
}
