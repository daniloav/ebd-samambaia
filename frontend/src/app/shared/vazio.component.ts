import { Component, Input } from '@angular/core';

/** Estado vazio orientado: ícone + título + texto opcional + ação projetada. */
@Component({
  selector: 'app-vazio',
  standalone: true,
  styles: [`
    .vazio { text-align: center; padding: 2.5rem 1rem; }
    .vazio .ico { font-size: 2.4rem; line-height: 1; margin-bottom: .6rem; }
    .vazio h3 { margin: 0 0 .3rem; color: var(--cinza-texto); font-weight: 700; }
    .vazio p { margin: 0 auto 1rem; max-width: 34ch; }
    .vazio ::ng-deep .btn { margin-top: .2rem; }
  `],
  template: `
    <div class="vazio">
      <div class="ico">{{ icone || '📭' }}</div>
      <h3>{{ titulo }}</h3>
      @if (texto) { <p class="muted">{{ texto }}</p> }
      <ng-content></ng-content>
    </div>
  `,
})
export class VazioComponent {
  @Input() icone = '📭';
  @Input({ required: true }) titulo = '';
  @Input() texto = '';
}
