import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export type ToastType = 'info' | 'success' | 'warning' | 'error';

export interface Toast {
  id: string;
  message: string;
  type: ToastType;
  duration: number;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  private toasts$ = new BehaviorSubject<Toast[]>([]);
  private timers = new Map<string, ReturnType<typeof setTimeout>>();
  private idCounter = 0;

  /** Read-only stream for ToastContainerComponent */
  getToasts(): Observable<Toast[]> {
    return this.toasts$.asObservable();
  }

  /** Show a toast. Returns the generated id. */
  show(message: string, type: ToastType = 'info', duration = 3000): string {
    const id = `t-${++this.idCounter}`;
    const toast: Toast = { id, message, type, duration };
    this.toasts$.next([...this.toasts$.getValue(), toast]);

    if (duration > 0) {
      const timer = setTimeout(() => this.dismiss(id), duration);
      this.timers.set(id, timer);
    }
    return id;
  }

  /** Remove a toast by id. Clears its timer if pending. */
  dismiss(id: string): void {
    this.clearTimer(id);
    this.toasts$.next(this.toasts$.getValue().filter((t) => t.id !== id));
  }

  /** Remove all toasts and clear all timers. */
  clearAll(): void {
    this.timers.forEach((_, id) => this.clearTimer(id));
    this.toasts$.next([]);
  }

  /** Lifecycle hook — call from app or service teardown. */
  destroy(): void {
    this.clearAll();
  }

  private clearTimer(id: string): void {
    const timer = this.timers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
  }
}
