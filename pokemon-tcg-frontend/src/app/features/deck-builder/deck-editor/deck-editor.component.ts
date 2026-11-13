import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { DeckEntry } from '../deck-entry.model';

export interface SaveDeckForm {
  name: string;
}

@Component({
  selector: 'app-deck-editor',
  imports: [CommonModule, FormsModule],
  templateUrl: './deck-editor.component.html',
  styleUrl: './deck-editor.component.scss',
})
export class DeckEditorComponent {
  @Input() entries: DeckEntry[] = [];
  @Input() validationErrors: string[] = [];
  @Input() deckName = 'Mazo principal';
  @Input() saving = false;
  @Input() saveMessage: string | null = null;
  @Input() saveError: string | null = null;
  @Input() darkMode = false;

  @Output() readonly increaseQuantity = new EventEmitter<string>();
  @Output() readonly decreaseQuantity = new EventEmitter<string>();
  @Output() readonly saveDeck = new EventEmitter<SaveDeckForm>();


  get totalCards(): number {
    return this.entries.reduce((total, entry) => total + entry.quantity, 0);
  }

  submit(): void {
    this.saveDeck.emit({
      name: this.deckName.trim(),
    });
  }

  trackByCardId(_index: number, entry: DeckEntry): string {
    return entry.card.id;
  }
}
