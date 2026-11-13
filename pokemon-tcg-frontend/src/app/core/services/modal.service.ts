import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { filter, take } from 'rxjs/operators';

export interface ModalConfig {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
}

export interface ModalResult {
  confirmed: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class ModalService {
  private modalConfig$ = new BehaviorSubject<ModalConfig | null>(null);
  private modalResult$ = new Subject<ModalResult>();

  getModalConfig(): Observable<ModalConfig | null> {
    return this.modalConfig$.asObservable();
  }

  openModal(config: ModalConfig): Promise<boolean> {
    this.modalConfig$.next(config);
    
    return new Promise((resolve) => {
      this.modalResult$
        .pipe(take(1))
        .subscribe((result) => {
          this.modalConfig$.next(null);
          resolve(result.confirmed);
        });
    });
  }

  confirm() {
    this.modalResult$.next({ confirmed: true });
  }

  cancel() {
    this.modalResult$.next({ confirmed: false });
  }

  closeModal() {
    this.cancel();
  }
}
