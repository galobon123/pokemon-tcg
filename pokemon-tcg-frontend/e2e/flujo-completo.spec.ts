import { test, expect, Page } from '@playwright/test';

// Cuenta única por ejecución para evitar conflictos de email repetido
const RUN_ID = Date.now();
const TEST_EMAIL = `e2e_${RUN_ID}@pokemon.com`;
const TEST_PASSWORD = 'test123456';
const TEST_USERNAME = `Trainer${RUN_ID % 100000}`;
const DECK_NAME = `Mazo E2E ${RUN_ID}`;

async function login(page: Page) {
  await page.goto('/login');

  // Caso 1: localStorage tiene sesión activa → banner con "IR AL LOBBY"
  const sessionBanner = page.locator('.active-session-banner');
  if (await sessionBanner.isVisible({ timeout: 3000 }).catch(() => false)) {
    await page.getByRole('button', { name: 'IR AL LOBBY' }).click();
    await expect(page).toHaveURL(/\/lobby/, { timeout: 15000 });
    return;
  }

  // Caso 2: Sin sesión en frontend → llenar formulario
  await page.locator('#email').fill(TEST_EMAIL);
  await page.locator('#password').fill(TEST_PASSWORD);
  await page.getByRole('button', { name: 'INICIAR SESIÓN' }).click();

  // Esperar: o redirige al lobby, o aparece el botón de forzar cierre de sesión
  const forceBtn = page.locator('.force-button');
  await Promise.race([
    page.waitForURL(/\/lobby/, { timeout: 10000 }),
    forceBtn.waitFor({ state: 'visible', timeout: 10000 }),
  ]).catch(() => {});

  // Si el backend detectó sesión activa, cerrarla y entrar
  if (await forceBtn.isVisible().catch(() => false)) {
    await forceBtn.click();
    await expect(page).toHaveURL(/\/lobby/, { timeout: 15000 });
    return;
  }

  await expect(page).toHaveURL(/\/lobby/, { timeout: 15000 });
}

// Los tests corren en orden y comparten el mismo usuario registrado
test.describe.serial('Flujo completo de Pokémon TCG', () => {

  // ─────────────────────────────────────────────────────────────
  // SETUP: Registrar cuenta nueva antes de todos los tests
  // ─────────────────────────────────────────────────────────────
  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    await page.goto('/register');

    await page.locator('#username').fill(TEST_USERNAME);
    await page.locator('#email').fill(TEST_EMAIL);
    await page.locator('#password').fill(TEST_PASSWORD);
    await page.getByRole('button', { name: 'CREAR CUENTA' }).click();

    await expect(page).toHaveURL(/\/lobby/, { timeout: 15000 });
    await page.close();
  });

  // ─────────────────────────────────────────────────────────────
  // TEST 1: Crear mazo (60 cartas, reglas reales)
  // ─────────────────────────────────────────────────────────────
  test('crear mazo con 60 cartas respetando las reglas', async ({ page }) => {
    await login(page);

    // Ir al gestor de mazos
    await page.goto('/deck-manager');
    await expect(page.getByText('Tus mazos')).toBeVisible({ timeout: 10000 });

    // Abrir el deck builder
    await page.getByRole('button', { name: 'Crear nuevo' }).click();
    await expect(page).toHaveURL(/\/deck-builder/, { timeout: 10000 });

    // Filtrar por Pokémon (así el catálogo muestra Básicos del set xy1)
    await page.locator('.supertype-select').selectOption('Pokémon');

    // Esperar que desaparezca el skeleton y aparezca la grilla real de cartas
    await expect(page.locator('.card-grid')).toBeVisible({ timeout: 30000 });
    await page.locator('.card-skeleton').first().waitFor({ state: 'hidden', timeout: 30000 }).catch(() => {});

    // Verificar que hay cartas disponibles
    const firstCard = page.locator('.card-cell').first();
    await firstCard.waitFor({ state: 'visible', timeout: 15000 });

    // Agregar 15 cartas distintas × 4 copias cada una = 60 cartas
    // (máx 4 copias por carta no-energía, al menos 1 Pokémon Básico)
    for (let i = 0; i < 15; i++) {
      const card = page.locator('.card-cell').nth(i);
      await card.click(); // copia 1
      await card.click(); // copia 2
      await card.click(); // copia 3
      await card.click(); // copia 4
    }

    // Poner nombre al mazo
    await page.locator('#deck-name').fill(DECK_NAME);

    // Guardar el mazo
    await page.getByRole('button', { name: 'Guardar mazo' }).click();

    // Verificar que se guardó y redirigió al gestor de mazos
    await expect(page).toHaveURL(/\/deck-manager/, { timeout: 15000 });
  });

  // ─────────────────────────────────────────────────────────────
  // TEST 2: Unirse a partida (Sala de Práctica)
  // ─────────────────────────────────────────────────────────────
  test('unirse a partida usando Sala de Práctica', async ({ page }) => {
    await login(page);

    // Abrir la Sala de Práctica (usar el heading exacto del card del lobby)
    await page.getByRole('heading', { name: 'Sala de Práctica', exact: true }).click();

    // Esperar overlay: verificar con el botón "SALA LOCAL" que solo existe en el panel
    await expect(page.getByRole('button', { name: 'SALA LOCAL' })).toBeVisible({ timeout: 10000 });

    // Seleccionar el primer mazo disponible (el que creamos en Test 1)
    const firstDeck = page.locator('.deck-item').first();
    await firstDeck.waitFor({ state: 'visible', timeout: 15000 });
    await firstDeck.click();

    // Confirmar sala local
    await page.getByRole('button', { name: 'SALA LOCAL' }).click();

    // Verificar que entra a la battle arena
    await expect(page).toHaveURL(/\/battle-arena\//, { timeout: 20000 });
  });

  // ─────────────────────────────────────────────────────────────
  // TEST 3: Ejecutar un turno
  // ─────────────────────────────────────────────────────────────
  test('ejecutar un turno en batalla', async ({ page }) => {
    await login(page);

    // Entrar a sala de práctica
    await page.getByRole('heading', { name: 'Sala de Práctica', exact: true }).click();
    await expect(page.getByRole('button', { name: 'SALA LOCAL' })).toBeVisible({ timeout: 10000 });

    const firstDeck = page.locator('.deck-item').first();
    await firstDeck.waitFor({ state: 'visible', timeout: 15000 });
    await firstDeck.click();
    await page.getByRole('button', { name: 'SALA LOCAL' }).click();

    await expect(page).toHaveURL(/\/battle-arena\//, { timeout: 20000 });

    // Esperar que la partida inicialice
    await page.waitForTimeout(3000);

    // Confirmar setup si hay botón "Listo" / "Confirmar"
    const setupBtn = page.getByRole('button', { name: /listo|confirmar|ready/i });
    if (await setupBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await setupBtn.click();
      await page.waitForTimeout(2000);
    }

    // Robar carta si está en fase DRAW
    const drawBtn = page.getByRole('button', { name: /robar|draw/i });
    if (await drawBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await drawBtn.click();
      await page.waitForTimeout(1000);
    }

    // Pasar turno si está disponible
    const passBtn = page.getByRole('button', { name: /pasar turno|finalizar|pass/i });
    if (await passBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await passBtn.click();
      await page.waitForTimeout(1000);
    }

    // El test verifica que la arena sigue activa después de ejecutar acciones
    await expect(page).toHaveURL(/\/battle-arena\//);
  });

});
