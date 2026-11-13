import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ToastContainerComponent } from './toast-container.component';
import { ToastService } from '../../core/services/toast.service';

describe('ToastContainerComponent', () => {
  let fixture: ComponentFixture<ToastContainerComponent>;
  let service: ToastService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ToastContainerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ToastContainerComponent);
    service = TestBed.inject(ToastService);
    fixture.detectChanges();
  });

  afterEach(() => {
    service.destroy();
  });

  it('renders nothing when the service has no toasts', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.toast').length).toBe(0);
  });

  it('renders current toasts with type-based styling', () => {
    service.show('Hello', 'info');
    service.show('Saved', 'success');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const toasts = compiled.querySelectorAll('.toast');
    expect(toasts.length).toBe(2);
    expect(compiled.textContent).toContain('Hello');
    expect(compiled.textContent).toContain('Saved');
  });

  it('dismisses a toast when clicked', () => {
    service.show('Click me', 'info');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const toast = compiled.querySelector('.toast') as HTMLElement;
    toast.click();
    fixture.detectChanges();

    expect(compiled.querySelectorAll('.toast').length).toBe(0);
  });
});
