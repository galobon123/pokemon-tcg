import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type EnergyType =
  | 'Fire'
  | 'Water'
  | 'Grass'
  | 'Lightning'
  | 'Psychic'
  | 'Fighting'
  | 'Darkness'
  | 'Metal'
  | 'Fairy'
  | 'Dragon'
  | 'Colorless'
  | 'Double'
  | 'Rainbow'
  | 'Unknown';

@Component({
  selector: 'app-energy-type-icon',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="energy-type-icon" [class]="energyType.toLowerCase()"
          [title]="energyType + (count > 1 ? ' x' + count : '')">
      <svg viewBox="0 0 32 32" width="28" height="28" aria-hidden="true">
        <ng-container [ngSwitch]="energyType">
          <ng-container *ngSwitchCase="'Fire'">
            <polygon points="16,2 28,30 4,30" fill="#FF4433" stroke="#CC2200" stroke-width="1.5"/>
            <polygon points="16,10 22,26 10,26" fill="#FFB74D"/>
            <polygon points="16,14 19,24 13,24" fill="#FFF176"/>
          </ng-container>
          <ng-container *ngSwitchCase="'Water'">
            <circle cx="16" cy="16" r="13" fill="#3399FF" stroke="#1565C0" stroke-width="1.5"/>
            <path d="M16 6 Q22 14 16 24 Q10 14 16 6Z" fill="#90CAF9" opacity="0.8"/>
            <circle cx="12" cy="12" r="2" fill="#E3F2FD" opacity="0.7"/>
          </ng-container>
          <ng-container *ngSwitchCase="'Grass'">
            <circle cx="16" cy="16" r="13" fill="#4CAF50" stroke="#1B5E20" stroke-width="1.5"/>
            <path d="M16 28 L16 10" stroke="#C8E6C9" stroke-width="3" stroke-linecap="round"/>
            <path d="M16 14 Q10 10 8 14" fill="none" stroke="#A5D6A7" stroke-width="2" stroke-linecap="round"/>
            <path d="M16 14 Q22 10 24 14" fill="none" stroke="#A5D6A7" stroke-width="2" stroke-linecap="round"/>
            <circle cx="16" cy="11" r="2.5" fill="#E8F5E9" opacity="0.6"/>
          </ng-container>
          <ng-container *ngSwitchCase="'Lightning'">
            <polygon points="18,2 8,18 14,18 12,30 24,12 17,12" fill="#FFD600" stroke="#FF8F00" stroke-width="1.5"/>
            <polygon points="16,8 10,20 14,20 13,28 22,14 18,14" fill="#FFF9C4" opacity="0.6"/>
          </ng-container>
          <ng-container *ngSwitchCase="'Psychic'">
            <circle cx="16" cy="16" r="13" fill="#BA68C8" stroke="#6A1B9A" stroke-width="1.5"/>
            <circle cx="16" cy="17" r="6" fill="#E1BEE7" opacity="0.7"/>
            <circle cx="16" cy="15" r="3" fill="#F3E5F5" opacity="0.9"/>
            <circle cx="14" cy="13" r="1" fill="#FFF" opacity="0.8"/>
          </ng-container>
          <ng-container *ngSwitchCase="'Fighting'">
            <circle cx="16" cy="16" r="13" fill="#E53935" stroke="#8E0000" stroke-width="1.5"/>
            <path d="M10 12 L22 12 L22 22 L10 22Z" fill="none" stroke="#FFCDD2" stroke-width="2.5" stroke-linejoin="round"/>
            <path d="M13 12 L13 22" stroke="#FFCDD2" stroke-width="2"/>
            <path d="M19 12 L19 22" stroke="#FFCDD2" stroke-width="2"/>
            <path d="M10 17 L22 17" stroke="#FFCDD2" stroke-width="2"/>
          </ng-container>
          <ng-container *ngSwitchCase="'Darkness'">
            <circle cx="16" cy="16" r="13" fill="#555555" stroke="#222222" stroke-width="1.5"/>
            <path d="M16 6 L22 20 L10 20Z" fill="#AAAAAA" opacity="0.9"/>
            <circle cx="16" cy="22" r="2.5" fill="#CCCCCC"/>
            <circle cx="13" cy="9" r="1.5" fill="#888" opacity="0.6"/>
          </ng-container>
          <ng-container *ngSwitchCase="'Metal'">
            <circle cx="16" cy="16" r="13" fill="#CFD8DC" stroke="#90A4AE" stroke-width="1.5"/>
            <circle cx="16" cy="16" r="7" fill="none" stroke="#ECEFF1" stroke-width="2.5"/>
            <circle cx="16" cy="16" r="2.5" fill="#ECEFF1"/>
            <circle cx="10" cy="10" r="1.5" fill="#FFF" opacity="0.4"/>
          </ng-container>
          <ng-container *ngSwitchCase="'Fairy'">
            <circle cx="16" cy="16" r="13" fill="#F06292" stroke="#C2185B" stroke-width="1.5"/>
            <path d="M16 8 Q20 6 21 10 Q22 14 16 16" fill="#F8BBD0" opacity="0.8"/>
            <path d="M16 8 Q12 6 11 10 Q10 14 16 16" fill="#F8BBD0" opacity="0.8"/>
            <path d="M16 16 Q22 14 23 18 Q22 26 16 24 Q10 26 9 18 Q10 14 16 16" fill="#FCE4EC" opacity="0.6"/>
          </ng-container>
          <ng-container *ngSwitchCase="'Dragon'">
            <circle cx="16" cy="16" r="13" fill="#9C27B0" stroke="#4A148C" stroke-width="1.5"/>
            <path d="M16 5 L13 10 L10 8 L9 14 L6 12 L8 18 L4 20 L10 20 L16 28 L22 20 L28 20 L24 18 L26 12 L23 14 L22 8 L19 10Z" fill="#E1BEE7" opacity="0.8"/>
            <path d="M16 8 L14 12 L12 11 L11 14" fill="none" stroke="#F3E5F5" stroke-width="0.8" opacity="0.6"/>
          </ng-container>
          <ng-container *ngSwitchCase="'Colorless'">
            <polygon points="16,3 19,12 29,12 21,19 24,29 16,23 8,29 11,19 3,12 13,12" fill="#D4D4A0" stroke="#999966" stroke-width="1.5"/>
            <polygon points="16,7 18,13 25,13 20,18 22,26 16,22 10,26 12,18 7,13 14,13" fill="#EEEEC0" opacity="0.5"/>
          </ng-container>
          <ng-container *ngSwitchCase="'Double'">
            <circle cx="11" cy="16" r="10" fill="#C0A080" stroke="#8B7355" stroke-width="1.5"/>
            <circle cx="11" cy="16" r="6" fill="#D4C5B0" opacity="0.8"/>
            <circle cx="21" cy="16" r="10" fill="#C0A080" stroke="#8B7355" stroke-width="1.5"/>
            <circle cx="21" cy="16" r="6" fill="#D4C5B0" opacity="0.8"/>
            <text x="11" y="20" text-anchor="middle" font-size="10" fill="#654321" font-weight="bold">2</text>
            <text x="21" y="20" text-anchor="middle" font-size="10" fill="#654321" font-weight="bold">2</text>
          </ng-container>
          <ng-container *ngSwitchCase="'Rainbow'">
            <circle cx="16" cy="16" r="13" fill="white" stroke="#888" stroke-width="1.5"/>
            <path d="M 5 16 Q 5 10 11 6 Q 16 3 21 6 Q 27 10 27 16" fill="#FF4444" opacity="0.7"/>
            <path d="M 5 16 Q 5 10 11 6 Q 16 3 21 6 Q 27 10 27 16 L 27 19 Q 27 23 21 26 Q 16 29 11 26 Q 5 23 5 19 Z" fill="#FFD700" opacity="0.5"/>
            <path d="M 7 16 Q 10 12 16 10 Q 22 12 25 16" fill="#44FF44" opacity="0.5"/>
            <path d="M 7 16 Q 10 20 16 22 Q 22 20 25 16" fill="#4444FF" opacity="0.5"/>
            <circle cx="16" cy="16" r="2" fill="#333" opacity="0.6"/>
          </ng-container>
          <ng-container *ngSwitchDefault>
            <circle cx="16" cy="16" r="13" fill="#999" stroke="#666" stroke-width="1.5"/>
            <text x="16" y="21" text-anchor="middle" font-size="14" fill="#fff" font-weight="bold">?</text>
          </ng-container>
        </ng-container>
      </svg>
      <span *ngIf="count > 1" class="energy-multiplier">x{{ count }}</span>
    </span>
  `,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      position: relative;
      line-height: 1;
    }
    .energy-type-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      position: relative;
      line-height: 1;
    }
    .energy-type-icon svg {
      display: block;
      filter: drop-shadow(0 1px 3px rgba(0,0,0,0.5));
    }
    .energy-multiplier {
      position: absolute;
      top: -4px;
      right: -6px;
      font-family: 'Press Start 2P', cursive;
      font-size: 0.55rem;
      line-height: 1;
      color: #fff;
      background: rgba(0, 0, 0, 0.8);
      border: 1px solid rgba(255, 255, 255, 0.6);
      border-radius: 3px;
      padding: 1px 3px;
      text-shadow: 1px 1px 0 #000;
      z-index: 2;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnergyTypeIconComponent {
  @Input({ required: true }) energyType!: EnergyType;
  @Input() count = 1;
 }