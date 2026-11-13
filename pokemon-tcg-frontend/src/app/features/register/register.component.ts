import { CommonModule } from '@angular/common';
import { Component, inject, ChangeDetectorRef, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { timeout } from 'rxjs/operators';

import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly cdr = inject(ChangeDetectorRef);

  registerForm = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(20)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  isLoading = false;
  errorMessage = '';
  darkMode = false;

  ngOnInit(): void {
    const saved = localStorage.getItem('app-dark-mode');
    this.darkMode = saved === 'true';
  }

  toggleDarkMode(): void {
    this.darkMode = !this.darkMode;
    localStorage.setItem('app-dark-mode', String(this.darkMode));
  }

  onSubmit(): void {
    if (this.registerForm.invalid) {
      this.errorMessage = 'Revisa los campos e intenta nuevamente.';
      return;
    }

    const username = this.registerForm.get('username')?.value ?? '';
    const email = this.registerForm.get('email')?.value ?? '';
    const password = this.registerForm.get('password')?.value ?? '';

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.register(username, email, password).pipe(
      timeout(15000)
    ).subscribe({
      next: () => {
        this.isLoading = false;
        this.cdr.detectChanges();
        this.router.navigate(['/lobby']);
      },
      error: (error: unknown) => {
        console.error('Register error:', error);
        this.isLoading = false;
        if (typeof error === 'object' && error !== null && 'status' in error) {
          const status = (error as { status?: number }).status;
          if (status === 409) {
            this.errorMessage = 'Ese email ya esta registrado.';
            this.cdr.detectChanges();
            return;
          }
          if (typeof status === 'number') {
            this.errorMessage = `No se pudo crear la cuenta (HTTP ${status}).`;
            this.cdr.detectChanges();
            return;
          }
        }
        this.errorMessage = 'No se pudo crear la cuenta. Verifica que el backend esté corriendo.';
        this.cdr.detectChanges();
      },
    });
  }

  get username() {
    return this.registerForm.get('username');
  }

  get email() {
    return this.registerForm.get('email');
  }

  get password() {
    return this.registerForm.get('password');
  }
}
