import { Component, inject, DestroyRef, ChangeDetectorRef, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);
  private fb = inject(FormBuilder);
  private destroyRef = inject(DestroyRef);
  private cdr = inject(ChangeDetectorRef);

  private loginTimer: ReturnType<typeof setTimeout> | null = null;

  loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  isLoading = false;
  errorMessage = '';
  darkMode = false;
  activeUser: string | null = null;
  showForceButton = false;

  ngOnInit(): void {
    const saved = localStorage.getItem('app-dark-mode');
    this.darkMode = saved === 'true';

    const token = this.authService.getToken();
    if (token) {
      this.isLoading = true;
      this.authService.validateSession()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (response) => {
            this.isLoading = false;
            this.activeUser = response.username;
            this.cdr.detectChanges();
          },
          error: () => {
            this.isLoading = false;
            this.authService.clearSession();
            this.cdr.detectChanges();
          }
        });
    }
  }

  toggleDarkMode(): void {
    this.darkMode = !this.darkMode;
    localStorage.setItem('app-dark-mode', String(this.darkMode));
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.errorMessage = 'Por favor ingresa un email y contraseña válidos.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';
    this.showForceButton = false;
    this.cdr.detectChanges();

    const email = this.loginForm.get('email')?.value ?? '';
    const password = this.loginForm.get('password')?.value ?? '';

    if (this.loginTimer) clearTimeout(this.loginTimer);
    this.loginTimer = setTimeout(() => {
      this.loginTimer = null;
      this.isLoading = false;
      this.errorMessage = 'El servidor no responde. Intenta de nuevo.';
      this.cdr.detectChanges();
    }, 8000);

    this.authService.login(email, password)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.clearLoginTimer();
          this.isLoading = false;
          this.cdr.detectChanges();
          this.router.navigate(['/lobby']);
        },
        error: (err: unknown) => {
          this.clearLoginTimer();
          this.isLoading = false;
          const httpErr = err as HttpErrorResponse;
          const status = httpErr?.status ?? 0;
          if (status === 401) {
            this.errorMessage = 'Credenciales inválidas.';
          } else if (status === 409) {
            const body = httpErr?.error ?? {};
            const username = body.username ?? '';
            this.errorMessage = username
              ? `Ya tienes una sesión activa como ${username}.`
              : 'Ya tienes una sesión activa de esta cuenta.';
            this.showForceButton = true;
          } else if (status === 0) {
            this.errorMessage = 'El servidor no responde. Intenta de nuevo.';
          } else {
            this.errorMessage = `No se pudo iniciar sesión (HTTP ${status}).`;
          }
          this.cdr.detectChanges();
        },
      });
  }

  forceLogin(): void {
    const email = this.loginForm.get('email')?.value ?? '';
    const password = this.loginForm.get('password')?.value ?? '';

    this.isLoading = true;
    this.errorMessage = '';
    this.showForceButton = false;
    this.cdr.detectChanges();

    this.authService.login(email, password, true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.isLoading = false;
          this.cdr.detectChanges();
          this.router.navigate(['/lobby']);
        },
        error: (err: unknown) => {
          this.isLoading = false;
          const status = (err as HttpErrorResponse)?.status ?? 0;
          if (status === 401) {
            this.errorMessage = 'Credenciales inválidas.';
          } else {
            this.errorMessage = `No se pudo iniciar sesión (HTTP ${status}).`;
          }
          this.cdr.detectChanges();
        }
      });
  }

  goToLobby(): void {
    this.router.navigate(['/lobby']);
  }

  private clearLoginTimer(): void {
    if (this.loginTimer) {
      clearTimeout(this.loginTimer);
      this.loginTimer = null;
    }
  }

  get email() {
    return this.loginForm.get('email');
  }

  get password() {
    return this.loginForm.get('password');
  }
}
