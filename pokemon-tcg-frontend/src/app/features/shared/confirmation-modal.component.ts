import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModalService } from '../../core/services/modal.service';

@Component({
  selector: 'app-confirmation-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <ng-container *ngIf="(modalService.getModalConfig() | async) as config">
      <div class="modal-backdrop"></div>
      <div class="modal-container">
        <div class="modal-box">
          <div class="modal-header">
            <h2>{{ config.title }}</h2>
          </div>
          <div class="modal-body">
            <p>{{ config.message }}</p>
          </div>
          <div class="modal-footer">
            <button 
              type="button"
              class="btn btn-confirm" 
              (click)="onConfirm()">
              {{ config.confirmText || 'Sí' }}
            </button>
            <button 
              type="button"
              class="btn btn-cancel" 
              (click)="onCancel()">
              {{ config.cancelText || 'No' }}
            </button>
          </div>
        </div>
      </div>
    </ng-container>
  `,
  styles: [`
    :host {
      display: contents;
    }

    .modal-backdrop {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.7);
      z-index: 9999;
    }

    .modal-container {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      display: flex;
      justify-content: center;
      align-items: center;
      z-index: 10000;
      font-family: 'Press Start 2P', cursive;
      pointer-events: none;
    }

    .modal-box {
      background: #ffffff;
      border: 6px solid #222222;
      box-shadow: 0 0 0 2px #222222, 8px 8px 20px rgba(0, 0, 0, 0.5);
      padding: 2rem;
      max-width: 500px;
      width: 90%;
      pointer-events: all;
      animation: popIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
    }

    @keyframes popIn {
      0% {
        transform: scale(0.5) rotate(-2deg);
        opacity: 0;
      }
      100% {
        transform: scale(1) rotate(0deg);
        opacity: 1;
      }
    }

    .modal-header {
      margin-bottom: 1.5rem;
    }

    .modal-header h2 {
      font-size: 0.9rem;
      color: #3949AB;
      margin: 0;
      text-transform: uppercase;
      letter-spacing: 2px;
    }

    .modal-body {
      margin-bottom: 2rem;
    }

    .modal-body p {
      font-size: 0.65rem;
      color: #222;
      margin: 0;
      line-height: 1.8;
      text-align: center;
    }

    .modal-footer {
      display: flex;
      gap: 1rem;
      justify-content: center;
    }

    .btn {
      padding: 0.8rem 1.5rem;
      font-family: 'Press Start 2P', cursive;
      font-size: 0.55rem;
      border: 4px solid #222;
      cursor: pointer;
      text-transform: uppercase;
      font-weight: bold;
      transition: all 0.15s ease;
      box-shadow: 4px 4px 0px rgba(0, 0, 0, 0.3);
      flex: 1;
      min-height: 40px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .btn:hover {
      transform: translate(-3px, -3px);
      box-shadow: 7px 7px 0px rgba(0, 0, 0, 0.3);
    }

    .btn:active {
      transform: translate(-1px, -1px);
      box-shadow: 5px 5px 0px rgba(0, 0, 0, 0.3);
    }

    .btn-confirm {
      background: #4CAF50;
      color: white;
    }

    .btn-cancel {
      background: #F44336;
      color: white;
    }
  `]
})
export class ConfirmationModalComponent {
  modalService = inject(ModalService);

  onConfirm() {
    this.modalService.confirm();
  }

  onCancel() {
    this.modalService.cancel();
  }
}
