import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { App } from './app';
import { ToastService } from './core/services/toast.service';

@Component({
  selector: 'app-page-a',
  template: '<p>Page A</p>',
  standalone: true,
})
class PageAComponent {}

@Component({
  selector: 'app-page-b',
  template: '<p>Page B</p>',
  standalone: true,
})
class PageBComponent {}

describe('App toast navigation survival', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App, PageAComponent, PageBComponent],
      providers: [
        provideRouter([
          { path: '', component: PageAComponent },
          { path: 'other', component: PageBComponent },
        ]),
      ],
    }).compileComponents();
  });

  afterEach(() => {
    TestBed.inject(ToastService).destroy();
  });

  it('keeps toasts visible after navigating to a new route', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();

    const toastService = TestBed.inject(ToastService);
    toastService.show('Saved!', 'success', 10_000);
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    await router.navigate(['/other']);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Saved!');
    expect(compiled.textContent).toContain('Page B');
  });
});
