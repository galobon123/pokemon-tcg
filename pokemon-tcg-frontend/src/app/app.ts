import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmationModalComponent } from './features/shared/confirmation-modal.component';
import { ToastContainerComponent } from './features/shared/toast-container.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ConfirmationModalComponent, ToastContainerComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('pokemon-tcg-frontend');
}
