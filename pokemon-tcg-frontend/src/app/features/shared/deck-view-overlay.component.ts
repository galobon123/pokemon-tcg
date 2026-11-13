import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { DeckCardInput, DeckResponse } from '../../core/models/deck.models';

interface DeckCardView {
  id: string;
  name: string;
  type: string;
  quantity: number;
  imageUrl: string;
}

@Component({
  selector: 'app-deck-view-overlay',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './deck-view-overlay.component.html',
  styleUrl: './deck-view-overlay.component.scss',
})
export class DeckViewOverlayComponent implements OnChanges, AfterViewInit {
  @Input() deck: DeckResponse | null = null;
  @Input() error: string | null = null;
  @Output() readonly close = new EventEmitter<void>();

  @ViewChild('deckViewPanel') deckViewPanel?: ElementRef<HTMLElement>;

  deckTitle = 'Mazo del jugador';
  deckTotalCards = 0;
  deckCardsView: DeckCardView[] = [];
  readonly cardBackUrl = '/images/cardBack.png';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['deck']) {
      this.refreshDeckView();
    }
  }

  ngAfterViewInit(): void {
    this.focusPanel();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.requestClose();
  }

  requestClose(): void {
    this.close.emit();
  }

  onOverlayClick(): void {
    this.requestClose();
  }

  trackByDeckCardId(_index: number, card: DeckCardView): string {
    return card.id;
  }

  private refreshDeckView(): void {
    if (!this.deck) {
      this.deckTitle = 'Mazo del jugador';
      this.deckCardsView = [];
      this.deckTotalCards = 0;
      return;
    }

    this.deckTitle = this.deck.name?.trim() || 'Mazo del jugador';
    this.deckCardsView = this.buildDeckCardsView(this.deck);
    this.deckTotalCards = this.deckCardsView.reduce(
      (total, card) => total + card.quantity,
      0,
    );
  }

  private focusPanel(): void {
    setTimeout(() => this.deckViewPanel?.nativeElement.focus(), 0);
  }

  private buildDeckCardsView(deck: DeckResponse): DeckCardView[] {
    return deck.cards.map((card) => ({
      id: card.cardId,
      name: card.name,
      type: card.type || '-',
      quantity: card.quantity,
      imageUrl: this.resolveCardImage(card),
    }));
  }

  private resolveCardImage(card: DeckCardInput): string {
    if (!card.setId || !card.cardId) {
      return this.cardBackUrl;
    }

    const parts = card.cardId.split('-');
    const number = parts.length > 1 ? parts[1] : '';
    if (!number) {
      return this.cardBackUrl;
    }

    return `https://images.pokemontcg.io/${card.setId}/${number}.png`;
  }
}
