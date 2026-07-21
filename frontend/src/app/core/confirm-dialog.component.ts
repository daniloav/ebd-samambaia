import { Component, HostListener, inject } from '@angular/core';
import { ConfirmService } from './confirm.service';

/** Diálogo único de confirmação — montado uma vez no shell. */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  template: `
    @if (svc.atual(); as c) {
      <div class="modal-backdrop" (click)="svc.responder(false)">
        <div class="modal" style="max-width:420px" (click)="$event.stopPropagation()">
          <div class="modal-header"><h3 style="margin:0">{{ c.titulo || 'Confirmar' }}</h3></div>
          <div class="modal-body">{{ c.mensagem }}</div>
          <div class="modal-footer">
            <button class="btn btn-outline" (click)="svc.responder(false)">{{ c.cancelar || 'Cancelar' }}</button>
            <button class="btn" [class.btn-perigo]="c.perigo" [class.btn-verde]="!c.perigo"
                    (click)="svc.responder(true)" autofocus>{{ c.confirmar || 'Confirmar' }}</button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ConfirmDialogComponent {
  svc = inject(ConfirmService);

  @HostListener('document:keydown.escape')
  aoEsc(): void { if (this.svc.atual()) { this.svc.responder(false); } }
}
