import { Component, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';

import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [AsyncPipe],
  template: `
    <div class="toast-stack">
      @for (toast of toastService.getToasts() | async; track toast.id) {
        <div
          class="toast"
          [attr.data-type]="toast.type"
          (click)="toastService.dismiss(toast.id)"
        >
          <span class="toast-msg">{{ toast.message }}</span>
        </div>
      }
    </div>
  `,
  styles: [`
    .toast-stack {
      position: fixed;
      top: 16px;
      right: 16px;
      z-index: 10001;
      display: flex;
      flex-direction: column;
      gap: 8px;
      font-family: 'Press Start 2P', cursive;
      pointer-events: none;
    }

    .toast {
      min-width: 200px;
      max-width: 320px;
      padding: 12px 16px;
      background: #ffffff;
      border: 4px solid #222;
      box-shadow: 4px 4px 0px rgba(0, 0, 0, 0.3);
      color: #fff;
      cursor: pointer;
      pointer-events: all;
      animation: slideIn 0.25s ease-out;
      font-size: 0.55rem;
      line-height: 1.6;
    }

    .toast[data-type="info"] { background: #3949AB; }
    .toast[data-type="success"] { background: #4CAF50; }
    .toast[data-type="warning"] { background: #FFCB05; color: #222; }
    .toast[data-type="error"] { background: #F44336; }

    .toast-msg {
      word-break: break-word;
    }

    @keyframes slideIn {
      from { transform: translateX(120%); opacity: 0; }
      to { transform: translateX(0); opacity: 1; }
    }
  `]
})
export class ToastContainerComponent {
  toastService = inject(ToastService);
}
