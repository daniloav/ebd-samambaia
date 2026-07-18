import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastService } from './core/toast.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <router-outlet />
    @if (toast.atual(); as t) {
      <div class="toast" [class.toast-ok]="t.tipo === 'ok'" [class.toast-erro]="t.tipo === 'erro'">
        {{ t.texto }}
      </div>
    }
  `,
})
export class AppComponent {
  toast = inject(ToastService);
}
