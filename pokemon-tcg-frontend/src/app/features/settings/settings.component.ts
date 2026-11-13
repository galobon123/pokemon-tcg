import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent {
  private readonly router = inject(Router);

  soundVolume: number;
  darkMode: boolean;

  constructor() {
    const savedVolume = localStorage.getItem('sound-volume');
    this.soundVolume = savedVolume !== null ? Number(savedVolume) : 100;
    const savedTheme = localStorage.getItem('app-dark-mode');
    this.darkMode = savedTheme === 'true';
  }

  setVolume(value: number): void {
    this.soundVolume = value;
    localStorage.setItem('sound-volume', String(value));
    this.playPreview();
  }

  onVolumeChange(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.setVolume(value);
  }

  toggleTheme(): void {
    this.darkMode = !this.darkMode;
    localStorage.setItem('app-dark-mode', String(this.darkMode));
  }

  playPreview(): void {
    const audio = new Audio('sounds/click.mp3');
    audio.volume = this.soundVolume / 100;
    audio.play().catch(() => {});
  }

  goBack(): void {
    this.router.navigate(['/lobby']);
  }
}
