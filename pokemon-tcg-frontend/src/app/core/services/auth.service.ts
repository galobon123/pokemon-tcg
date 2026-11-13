import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface User {
  playerId: string;
  username: string;
  token: string;
  avatar?: string;
}

interface AuthResponse {
  token: string;
  id: number;
  username: string;
  email: string;
  playerId: string | null;
  avatar?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly STORAGE_KEY = 'pokemon_auth';
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.gameServiceUrl}/api/auth`;
  private userSubject = new BehaviorSubject<User | null>(this.loadUser());

  public user$: Observable<User | null> = this.userSubject.asObservable();

  constructor() {}

  login(email: string, password: string, force = false): Observable<unknown> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/login`, { email, password, force })
      .pipe(tap((response) => this.persistAuth(response)));
  }

  register(username: string, email: string, password: string): Observable<unknown> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/register`, { username, email, password })
      .pipe(tap((response) => this.persistAuth(response)));
  }

  validateSession(): Observable<AuthResponse> {
    return this.http
      .get<AuthResponse>(`${this.baseUrl}/me`)
      .pipe(tap((response) => this.persistAuth(response)));
  }

  clearSession(): void {
    localStorage.removeItem(this.STORAGE_KEY);
    this.userSubject.next(null);
  }

  logout(): void {
    this.http.post(`${this.baseUrl}/logout`, {}).subscribe({ error: () => {} });
    localStorage.removeItem(this.STORAGE_KEY);
    this.userSubject.next(null);
  }

  isLoggedIn(): boolean {
    return this.userSubject.value !== null;
  }

  getCurrentUser(): User | null {
    return this.userSubject.value;
  }

  getToken(): string | null {
    return this.userSubject.value?.token ?? null;
  }

  updateAvatar(avatarUrl: string): void {
    const current = this.userSubject.value;
    if (current) {
      // Optimizacion: actualizar local primero para respuesta instantanea
      const updated = { ...current, avatar: avatarUrl };
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(updated));
      this.userSubject.next(updated);

      // Persistir en backend de fondo
      this.http.patch(`${this.baseUrl}/avatar`, { avatar: avatarUrl }).subscribe({
        error: (err) => console.error('Error persisting avatar to backend:', err)
      });
    }
  }

  private loadUser(): User | null {
    const stored = localStorage.getItem(this.STORAGE_KEY);
    return stored ? JSON.parse(stored) : null;
  }

  private persistAuth(response: AuthResponse): User {
    const user: User = {
      playerId: response.playerId ?? '',
      username: response.username,
      token: response.token,
      avatar: response.avatar
    };

    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(user));
    this.userSubject.next(user);
    return user;
  }
}
