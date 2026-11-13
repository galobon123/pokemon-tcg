import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { Toast, ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ToastService);
  });

  afterEach(() => {
    vi.useRealTimers();
    service.destroy();
  });

  it('show appends a toast and emits via the stream', () => {
    const emitted: Toast[][] = [];
    service.getToasts().subscribe((toasts) => emitted.push(toasts));

    const id = service.show('Hi', 'info');

    expect(emitted.length).toBe(2);
    expect(emitted[1]).toEqual([{ id, message: 'Hi', type: 'info', duration: 3000 }]);
  });

  it('show uses default info type and 3000ms duration', () => {
    let last: Toast[] = [];
    service.getToasts().subscribe((toasts) => (last = toasts));

    const id = service.show('Default');

    expect(last).toEqual([{ id, message: 'Default', type: 'info', duration: 3000 }]);
  });

  it('show returns unique ids across calls', () => {
    const ids = new Set<string>();
    for (let i = 0; i < 100; i++) {
      ids.add(service.show(`msg-${i}`));
    }
    expect(ids.size).toBe(100);
  });

  it('dismiss removes only the targeted toast', () => {
    let last: Toast[] = [];
    service.getToasts().subscribe((toasts) => (last = toasts));
    const idA = service.show('A');
    const idB = service.show('B');

    service.dismiss(idA);

    expect(last).toEqual([{ id: idB, message: 'B', type: 'info', duration: 3000 }]);
  });

  it('auto-dismiss removes the toast after the duration', () => {
    vi.useFakeTimers();
    let last: Toast[] = [];
    service.getToasts().subscribe((toasts) => (last = toasts));
    service.show('Auto', 'info', 1000);

    expect(last.length).toBe(1);
    vi.advanceTimersByTime(1000);
    expect(last.length).toBe(0);
  });

  it('dismiss cancels the pending auto-dismiss timer', () => {
    vi.useFakeTimers();
    let last: Toast[] = [];
    service.getToasts().subscribe((toasts) => (last = toasts));
    const id = service.show('Stay', 'info', 5000);

    service.dismiss(id);
    expect(last.length).toBe(0);

    vi.advanceTimersByTime(5000);
    expect(last.length).toBe(0);
  });

  it('clearAll empties toasts and clears every pending timer', () => {
    vi.useFakeTimers();
    let last: Toast[] = [];
    service.getToasts().subscribe((toasts) => (last = toasts));
    service.show('A', 'info', 5000);
    service.show('B', 'info', 5000);

    service.clearAll();
    expect(last.length).toBe(0);

    vi.advanceTimersByTime(5000);
    expect(last.length).toBe(0);
  });

  it('destroy clears every toast and timer', () => {
    vi.useFakeTimers();
    let last: Toast[] = [];
    service.getToasts().subscribe((toasts) => (last = toasts));
    service.show('A', 'info', 5000);
    service.show('B', 'info', 5000);

    service.destroy();
    expect(last.length).toBe(0);

    vi.advanceTimersByTime(5000);
    expect(last.length).toBe(0);
  });
});
