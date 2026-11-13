import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LoginComponent } from './login.component';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authServiceMock: {
    login: ReturnType<typeof vi.fn>;
    register: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    authServiceMock = {
      login: vi.fn(),
      register: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        ToastService,
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({}),
              queryParamMap: convertToParamMap({}),
            },
          },
        },
        { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } as unknown as Router },
        { provide: AuthService, useValue: authServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('shows credentials error and stops loading on 401', () => {
    component.loginForm.setValue({ email: 'bad@test.com', password: 'wrong123' });
    authServiceMock.login.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 401 })),
    );

    component.onSubmit();
    fixture.detectChanges();

    expect(component.isLoading).toBe(false);
    expect(component.errorMessage).toBe('Credenciales inválidas.');
  });

  it('shows active session error and stops loading on 409', () => {
    component.loginForm.setValue({ email: 'test@test.com', password: 'secret123' });
    authServiceMock.login.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );

    component.onSubmit();
    fixture.detectChanges();

    expect(component.isLoading).toBe(false);
    expect(component.errorMessage).toBe('Ya tienes una sesión activa de esta cuenta.');
  });

  it('shows server error and stops loading on timeout / status 0', () => {
    component.loginForm.setValue({ email: 'test@test.com', password: 'secret123' });
    authServiceMock.login.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0 })),
    );

    component.onSubmit();
    fixture.detectChanges();

    expect(component.isLoading).toBe(false);
    expect(component.errorMessage).toBe('El servidor no responde. Intenta de nuevo.');
  });

  it('navigates to lobby on successful login', () => {
    const router = TestBed.inject(Router);
    component.loginForm.setValue({ email: 'good@test.com', password: 'secret123' });
    authServiceMock.login.mockReturnValue(of({}));

    component.onSubmit();

    expect(component.isLoading).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/lobby']);
  });
});
