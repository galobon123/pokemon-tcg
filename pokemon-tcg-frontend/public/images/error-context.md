# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: flujo-completo.spec.ts >> Flujo completo de Pokémon TCG >> unirse a partida usando Sala de Práctica
- Location: e2e\flujo-completo.spec.ts:102:7

# Error details

```
Error: expect(page).toHaveURL(expected) failed

Expected pattern: /\/lobby/
Received string:  "http://localhost:4200/login"
Timeout: 15000ms

Call log:
  - Expect "toHaveURL" with timeout 15000ms
    33 × unexpected value "http://localhost:4200/login"

```

```yaml
- text: POK E MON TCG ARENA
- button "🌙"
- img "Trainer"
- heading "¡Entrenador!" [level=2]
- paragraph: Ingresa al mundo Pokémon TCG
- text: EMAIL
- textbox "EMAIL":
  - /placeholder: tu@email.com
  - text: e2e_1782612989702@pokemon.com
- text: CONTRASEÑA
- textbox "CONTRASEÑA":
  - /placeholder: Minimo 6 caracteres
  - text: test123456
- paragraph: Ya tienes una sesión activa como Trainer89702.
- button "Cerrar sesión anterior y entrar"
- button "INICIAR SESIÓN"
- link "Crear cuenta":
  - /url: /register
- paragraph: © 2026 Pokémon Trading Card Game Arena • v1.0
```

# Test source

```ts
  1   | import { test, expect, Page } from '@playwright/test';
  2   | 
  3   | // Cuenta única por ejecución para evitar conflictos de email repetido
  4   | const RUN_ID = Date.now();
  5   | const TEST_EMAIL = `e2e_${RUN_ID}@pokemon.com`;
  6   | const TEST_PASSWORD = 'test123456';
  7   | const TEST_USERNAME = `Trainer${RUN_ID % 100000}`;
  8   | const DECK_NAME = `Mazo E2E ${RUN_ID}`;
  9   | 
  10  | async function login(page: Page) {
  11  |   await page.goto('/login');
  12  | 
  13  |   // Caso 1: localStorage tiene sesión activa → banner con "IR AL LOBBY"
  14  |   const sessionBanner = page.locator('.active-session-banner');
  15  |   if (await sessionBanner.isVisible({ timeout: 3000 }).catch(() => false)) {
  16  |     await page.getByRole('button', { name: 'IR AL LOBBY' }).click();
  17  |     await expect(page).toHaveURL(/\/lobby/, { timeout: 15000 });
  18  |     return;
  19  |   }
  20  | 
  21  |   // Caso 2: Sin sesión en frontend → llenar formulario
  22  |   await page.locator('#email').fill(TEST_EMAIL);
  23  |   await page.locator('#password').fill(TEST_PASSWORD);
  24  |   await page.getByRole('button', { name: 'INICIAR SESIÓN' }).click();
  25  | 
  26  |   // Caso 3: Backend detecta sesión activa del beforeAll → aparece botón forzar login
  27  |   const forceBtn = page.getByRole('button', { name: /cerrar sesión anterior/i });
  28  |   if (await forceBtn.isVisible({ timeout: 4000 }).catch(() => false)) {
  29  |     await forceBtn.click();
  30  |   }
  31  | 
> 32  |   await expect(page).toHaveURL(/\/lobby/, { timeout: 15000 });
      |                      ^ Error: expect(page).toHaveURL(expected) failed
  33  | }
  34  | 
  35  | // Los tests corren en orden y comparten el mismo usuario registrado
  36  | test.describe.serial('Flujo completo de Pokémon TCG', () => {
  37  | 
  38  |   // ─────────────────────────────────────────────────────────────
  39  |   // SETUP: Registrar cuenta nueva antes de todos los tests
  40  |   // ─────────────────────────────────────────────────────────────
  41  |   test.beforeAll(async ({ browser }) => {
  42  |     const page = await browser.newPage();
  43  |     await page.goto('/register');
  44  | 
  45  |     await page.locator('#username').fill(TEST_USERNAME);
  46  |     await page.locator('#email').fill(TEST_EMAIL);
  47  |     await page.locator('#password').fill(TEST_PASSWORD);
  48  |     await page.getByRole('button', { name: 'CREAR CUENTA' }).click();
  49  | 
  50  |     await expect(page).toHaveURL(/\/lobby/, { timeout: 15000 });
  51  |     await page.close();
  52  |   });
  53  | 
  54  |   // ─────────────────────────────────────────────────────────────
  55  |   // TEST 1: Crear mazo (60 cartas, reglas reales)
  56  |   // ─────────────────────────────────────────────────────────────
  57  |   test('crear mazo con 60 cartas respetando las reglas', async ({ page }) => {
  58  |     await login(page);
  59  | 
  60  |     // Ir al gestor de mazos
  61  |     await page.goto('/deck-manager');
  62  |     await expect(page.getByText('Tus mazos')).toBeVisible({ timeout: 10000 });
  63  | 
  64  |     // Abrir el deck builder
  65  |     await page.getByRole('button', { name: 'Crear nuevo' }).click();
  66  |     await expect(page).toHaveURL(/\/deck-builder/, { timeout: 10000 });
  67  | 
  68  |     // Filtrar por Pokémon (así el catálogo muestra Básicos del set xy1)
  69  |     await page.locator('.supertype-select').selectOption('Pokémon');
  70  | 
  71  |     // Esperar que desaparezca el skeleton y aparezca la grilla real de cartas
  72  |     await expect(page.locator('.card-grid')).toBeVisible({ timeout: 30000 });
  73  |     await page.locator('.card-skeleton').first().waitFor({ state: 'hidden', timeout: 30000 }).catch(() => {});
  74  | 
  75  |     // Verificar que hay cartas disponibles
  76  |     const firstCard = page.locator('.card-cell').first();
  77  |     await firstCard.waitFor({ state: 'visible', timeout: 15000 });
  78  | 
  79  |     // Agregar 15 cartas distintas × 4 copias cada una = 60 cartas
  80  |     // (máx 4 copias por carta no-energía, al menos 1 Pokémon Básico)
  81  |     for (let i = 0; i < 15; i++) {
  82  |       const card = page.locator('.card-cell').nth(i);
  83  |       await card.click(); // copia 1
  84  |       await card.click(); // copia 2
  85  |       await card.click(); // copia 3
  86  |       await card.click(); // copia 4
  87  |     }
  88  | 
  89  |     // Poner nombre al mazo
  90  |     await page.locator('#deck-name').fill(DECK_NAME);
  91  | 
  92  |     // Guardar el mazo
  93  |     await page.getByRole('button', { name: 'Guardar mazo' }).click();
  94  | 
  95  |     // Verificar que se guardó y redirigió al gestor de mazos
  96  |     await expect(page).toHaveURL(/\/deck-manager/, { timeout: 15000 });
  97  |   });
  98  | 
  99  |   // ─────────────────────────────────────────────────────────────
  100 |   // TEST 2: Unirse a partida (Sala de Práctica)
  101 |   // ─────────────────────────────────────────────────────────────
  102 |   test('unirse a partida usando Sala de Práctica', async ({ page }) => {
  103 |     await login(page);
  104 | 
  105 |     // Abrir la Sala de Práctica (usar el heading exacto del card del lobby)
  106 |     await page.getByRole('heading', { name: 'Sala de Práctica', exact: true }).click();
  107 | 
  108 |     // Esperar overlay: verificar con el botón "SALA LOCAL" que solo existe en el panel
  109 |     await expect(page.getByRole('button', { name: 'SALA LOCAL' })).toBeVisible({ timeout: 10000 });
  110 | 
  111 |     // Seleccionar el primer mazo disponible (el que creamos en Test 1)
  112 |     const firstDeck = page.locator('.deck-item').first();
  113 |     await firstDeck.waitFor({ state: 'visible', timeout: 15000 });
  114 |     await firstDeck.click();
  115 | 
  116 |     // Confirmar sala local
  117 |     await page.getByRole('button', { name: 'SALA LOCAL' }).click();
  118 | 
  119 |     // Verificar que entra a la battle arena
  120 |     await expect(page).toHaveURL(/\/battle-arena\//, { timeout: 20000 });
  121 |   });
  122 | 
  123 |   // ─────────────────────────────────────────────────────────────
  124 |   // TEST 3: Ejecutar un turno
  125 |   // ─────────────────────────────────────────────────────────────
  126 |   test('ejecutar un turno en batalla', async ({ page }) => {
  127 |     await login(page);
  128 | 
  129 |     // Entrar a sala de práctica
  130 |     await page.getByRole('heading', { name: 'Sala de Práctica', exact: true }).click();
  131 |     await expect(page.getByRole('button', { name: 'SALA LOCAL' })).toBeVisible({ timeout: 10000 });
  132 | 
```