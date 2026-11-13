import { Injectable, inject } from '@angular/core';
import { Client, IFrame, IMessage, StompSubscription } from '@stomp/stompjs';
import { Observable, Subject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { GameEventEnvelope } from '../models/websocket.models';
import { ToastService } from './toast.service';

export interface ChatMessage {
  playerId: string;
  playerName: string;
  text: string;
  timestamp: string;
  id: string;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private readonly toastService = inject(ToastService);
  private readonly eventSubject = new Subject<GameEventEnvelope>();
  private readonly chatSubject = new Subject<ChatMessage>();
  private client: Client | null = null;
  private subscription: StompSubscription | null = null;
  private chatSubscription: StompSubscription | null = null;

  readonly events$: Observable<GameEventEnvelope> = this.eventSubject.asObservable();
  readonly chatEvents$: Observable<ChatMessage> = this.chatSubject.asObservable();

  connect(gameId: string, token?: string): void {
    if (this.client?.active) return;

    let brokerURL = environment.realtimeServiceUrl.replace(/^http/, 'ws') + '/ws';
    const connectHeaders: Record<string, string> = {};
    
    if (token) {
      brokerURL += `?token=${encodeURIComponent(token)}`;
      connectHeaders['Authorization'] = 'Bearer ' + token;
    }

    this.client = new Client({
      brokerURL,
      reconnectDelay: 5000,
      connectHeaders,
    });

    this.client.onConnect = () => {
      this.subscription = this.client!.subscribe(`/topic/games/${gameId}/events`, (message: IMessage) => {
        const event = JSON.parse(message.body) as GameEventEnvelope;
        this.eventSubject.next(event);
      });
      this.chatSubscription = this.client!.subscribe(`/topic/games/${gameId}/chat`, (message: IMessage) => {
        const chat = JSON.parse(message.body) as ChatMessage;
        this.chatSubject.next(chat);
      });
    };

    this.client.onStompError = (frame) => this.handleStompError(frame);

    this.client.onWebSocketClose = () => {
      console.warn('WebSocket connection closed');
    };

    this.client.activate();
  }

  handleStompError(frame: IFrame): void {
    this.toastService.show(frame.headers['message'] || 'WebSocket error', 'error');
  }

  send(destination: string, body: string): void {
    if (this.client?.active) {
      this.client.publish({ destination, body });
    }
  }

  disconnect(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
    this.chatSubscription?.unsubscribe();
    this.chatSubscription = null;
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
  }
}
