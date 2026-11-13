import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  computed,
} from '@angular/core';

import {
  CardSummaryResponse,
  CardAttackResponse,
} from '../../../../core/models/card.models';
import { EnergyTypeIconComponent, EnergyType } from '../../../../shared/components/energy-type-icon/energy-type-icon.component';
import { groupEnergiesByType, GroupedEnergy } from '../../../../shared/components/energy-type-icon/energy-grouping.util';
import { StatusCondition } from '../../../../core/models/game.models';

type CardSummaryWithStatus = CardSummaryResponse & { statusCondition?: StatusCondition | null };

@Component({
  selector: 'app-card-detail-overlay',
  standalone: true,
  imports: [CommonModule, EnergyTypeIconComponent],
  templateUrl: './card-detail-overlay.component.html',
  styleUrls: ['./card-detail-overlay.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardDetailOverlayComponent {
  @Input({ required: true }) card!: CardSummaryResponse | null;
  @Input() rect: DOMRect | null = null;
  @Input() attachedEnergies: { energyType: string }[] = [];
  @Output() close = new EventEmitter<void>();

  readonly groupedEnergies = computed(() =>
    groupEnergiesByType(this.attachedEnergies ?? [])
  );

  readonly overlayStyle = computed(() => {
    const r = this.rect;
    const width = 400;
    const heightEstimate = this.attachedEnergies?.length ? 580 : 480;

    if (!r) {
      return {
        left: `calc(50% - ${width / 2}px)`,
        top: `20%`,
      };
    }

    const viewportW = window.innerWidth;
    const viewportH = window.innerHeight;

    let left = r.right + 12;
    if (left + width + 8 > viewportW) {
      left = r.left - width - 12;
    }

    let top = r.top;
    if (top + heightEstimate + 8 > viewportH) top = Math.max(8, viewportH - heightEstimate - 8);
    if (top < 8) top = 8;

    return {
      left: `${Math.max(8, left)}px`,
      top: `${top}px`,
    };
  });

  imageUrl(card: CardSummaryResponse | null): string {
    if (!card) return '';
    const num = card.id.split('-')[1];
    return `https://images.pokemontcg.io/${card.setId}/${num}.png`;
  }

  costDots(attack: CardAttackResponse): string[] {
    return attack.cost ?? [];
  }

  weaknessesText(): string {
    if (!this.card?.weaknesses?.length) return '';
    return this.card.weaknesses.map((w) => `${w.type} ${w.value}`).join(', ');
  }

  resistancesText(): string {
    if (!this.card?.resistances?.length) return '';
    return this.card.resistances.map((r) => `${r.type} ${r.value}`).join(', ');
  }

  asEnergyType(type: string): EnergyType {
    return type as EnergyType;
  }

  statusCondition(): StatusCondition | null {
    return (this.card as CardSummaryWithStatus | null)?.statusCondition ?? null;
  }

  statusLabel(status: StatusCondition): string {
    switch (status) {
      case 'ASLEEP': return 'Dormido';
      case 'PARALYZED': return 'Paralizado';
      case 'CONFUSED': return 'Confundido';
      case 'POISONED': return 'Envenenado';
      case 'BURNED': return 'Quemado';
      default: return status;
    }
  }
}
