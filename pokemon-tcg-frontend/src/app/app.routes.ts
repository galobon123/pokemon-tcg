import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/login/login.component').then(
        (component) => component.LoginComponent,
      ),
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/login/login.component').then(
        (component) => component.LoginComponent,
      ),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/register/register.component').then(
        (component) => component.RegisterComponent,
      ),
  },
  {
    path: 'lobby',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/lobby/lobby.component').then(
        (component) => component.LobbyComponent,
      ),
  },
  {
    path: 'deck-manager',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/deck-manager/deck-manager.component').then(
        (component) => component.DeckManagerComponent,
      ),
  },
  {
    path: 'deck-builder',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/deck-builder/deck-builder.component').then(
        (component) => component.DeckBuilderComponent,
      ),
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/settings/settings.component').then(
        (component) => component.SettingsComponent,
      ),
  },
  {
    path: 'battle-arena/game/:gameId',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/battle-arena/battle-arena.component').then(
        (component) => component.BattleArenaComponent,
      ),
  },
  {
    path: 'battle-arena/:deckId',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/battle-arena/battle-arena.component').then(
        (component) => component.BattleArenaComponent,
      ),
  },
];
