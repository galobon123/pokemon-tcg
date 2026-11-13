import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  EventEmitter,
  inject,
  Input,
  OnDestroy,
  Output,
  signal,
} from '@angular/core';

import { CardService } from '../../../core/services/card.service';
import {
  CardSearchResponse,
  CardSummaryResponse,
} from '../../../core/models/card.models';
import { CardDetailOverlayComponent } from './card-detail-overlay/card-detail-overlay.component';
import { Subscription } from 'rxjs';
import { take, timeout } from 'rxjs/operators';

@Component({
  selector: 'app-card-catalog',
  standalone: true,
  imports: [CommonModule, CardDetailOverlayComponent],
  templateUrl: './card-catalog.component.html',
  styleUrls: ['./card-catalog.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardCatalogComponent implements OnDestroy {
  private readonly cardService = inject(CardService);
  @Output() readonly cardSelected = new EventEmitter<CardSummaryResponse>();
  @Input() darkMode = false;

  private fetchSubscription: Subscription | null = null;

  readonly cards = signal<CardSummaryResponse[]>([]);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly currentPage = signal<number>(1);
  readonly totalCount = signal<number>(0);
  readonly hoveredCard = signal<CardSummaryResponse | null>(null);
  readonly hoveredCardRect = signal<DOMRect | null>(null);

  // Filters
  readonly nameFilter = signal<string>('');
  readonly supertypeFilter = signal<string>('');
  readonly energyTypeFilter = signal<string>('');

  // Internal debounced name value
  private nameDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  private latestRequestId = 0;
  readonly nameFilterDebounced = signal<string>('');

  readonly pageSize = 20;
  readonly skeletons = computed(() => Array.from({ length: this.pageSize }, (_, i) => i));
  readonly pages = computed(() => Array.from({ length: this.totalPages() }, (_, i) => i + 1));

  readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.totalCount() / this.pageSize))
  );

  constructor() {
    // When filters (debounced name, supertype, energy) change, reset to page 1 and fetch.
    effect(() => {
      // Reset to first page on filter change and fetch immediately.
      this.currentPage.set(1);
      this.fetchPage(this.buildQuery(), 1);
    });

    // debounce name filter
    effect(() => {
      const v = this.nameFilter();
      if (this.nameDebounceTimer) clearTimeout(this.nameDebounceTimer);
      this.nameDebounceTimer = setTimeout(() => {
        this.nameFilterDebounced.set(v);
      }, 250);
    });
  }

  private fetchPage(q: string, page: number): void {
    const requestId = ++this.latestRequestId;

    if (this.fetchSubscription) {
      this.fetchSubscription.unsubscribe();
    }

    this.loading.set(true);
    this.error.set(null);

    this.fetchSubscription = this.cardService
      .search(q, this.pageSize, page)
      .pipe(
        timeout(15_000),
        take(1),
      )
      .subscribe({
        next: (res: CardSearchResponse) => {
          if (requestId !== this.latestRequestId) return;
          this.cards.set(res.cards ?? []);
          this.totalCount.set(res.totalCount ?? res.count ?? 0);
          this.loading.set(false);
        },
        error: () => {
          if (requestId !== this.latestRequestId) return;
          this.error.set('Failed to load cards.');
          this.cards.set([]);
          this.totalCount.set(0);
          this.loading.set(false);
        },
      });
  }

  // UI bindings
  onNameInput(value: string): void {
    this.nameFilter.set(value);
    this.currentPage.set(1);
  }

  onSupertypeChange(value: string): void {
    this.supertypeFilter.set(value);
    if (value === 'Trainer') {
      this.energyTypeFilter.set('');
    }
    this.currentPage.set(1);
  }

  onEnergyChange(value: string): void {
    this.energyTypeFilter.set(value);
    this.currentPage.set(1);
  }

  // pagination
  goToPage(page: number): void {
    if (page < 1 || page > this.totalPages()) return;
    this.currentPage.set(page);

    // scroll to top of grid
    const el = document.getElementById('card-grid');
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    // build query from current filters and fetch the requested page
    this.fetchPage(this.buildQuery(), page);
  }

  private buildQuery(): string {
    const qParts: string[] = ['set.id:xy1'];
    const name = this.nameFilterDebounced();
    const supertype = this.supertypeFilter();
    const energy = this.energyTypeFilter();

    if (name.trim()) qParts.push(`name:${name.trim()}*`);
    if (supertype.trim()) qParts.push(`supertype:${supertype.trim()}`);
    if (energy.trim()) qParts.push(`types:${energy.trim()}`);

    return qParts.join(' ');
  }

  prevPage(): void {
    this.goToPage(this.currentPage() - 1);
  }

  nextPage(): void {
    this.goToPage(this.currentPage() + 1);
  }

  // hover
  onCardMouseEnter(card: CardSummaryResponse, ev: MouseEvent): void {
    const target = ev.currentTarget as HTMLElement;
    this.hoveredCard.set(card);
    this.hoveredCardRect.set(target.getBoundingClientRect());
  }

  onCardMouseLeave(): void {
    this.hoveredCard.set(null);
    this.hoveredCardRect.set(null);
  }

  selectCard(card: CardSummaryResponse): void {
    this.cardSelected.emit(card);
  }

  // trackBy
  trackById(_i: number, card: CardSummaryResponse): string {
    return card.id;
  }

  ngOnDestroy(): void {
    this.fetchSubscription?.unsubscribe();
  }
}
