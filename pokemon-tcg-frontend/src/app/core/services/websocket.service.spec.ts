import { TestBed } from '@angular/core/testing';
import { IFrame } from '@stomp/stompjs';
import { vi } from 'vitest';

import { WebSocketService } from './websocket.service';
import { ToastService } from './toast.service';

describe('WebSocketService error handling', () => {
  let service: WebSocketService;
  let toastService: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(WebSocketService);
    toastService = TestBed.inject(ToastService);
  });

  afterEach(() => {
    toastService.destroy();
  });

  it('shows an error toast on STOMP error frames', () => {
    const showSpy = vi.spyOn(toastService, 'show');

    service.handleStompError({ headers: { message: 'Auth failed' } } as unknown as IFrame);

    expect(showSpy).toHaveBeenCalledWith('Auth failed', 'error');
  });

  it('falls back to a default message when the error frame has no message', () => {
    const showSpy = vi.spyOn(toastService, 'show');

    service.handleStompError({ headers: {} } as unknown as IFrame);

    expect(showSpy).toHaveBeenCalledWith('WebSocket error', 'error');
  });
});
