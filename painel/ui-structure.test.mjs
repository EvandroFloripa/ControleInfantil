import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const html = await readFile(new URL('./index.html', import.meta.url), 'utf8');

test('exposes the professional dashboard shell and design tokens', () => {
  assert.match(html, /--surface:/);
  assert.match(html, /--accent:/);
  assert.match(html, /--success:/);
  assert.match(html, /--warning:/);
  assert.match(html, /--danger:/);
  assert.match(html, /--focus:/);
  assert.match(html, /class="app-shell"/);
  assert.match(html, /class="sidebar"/);
  assert.match(html, /class="dashboard-content"/);
});

test('provides one page heading, a skip link and semantic navigation', () => {
  assert.equal((html.match(/<h1\b/g) || []).length, 1);
  assert.match(html, /<a\b[^>]*class="skip-link"[^>]*href="#mainContent"/);
  assert.match(html, /<nav\b[^>]*aria-label="Navegação principal"/);
  assert.match(html, /<main\b[^>]*id="mainContent"/);
});

test('exposes the overview contract and derives it from the selected device', () => {
  for (const id of [
    'overviewCard', 'overviewConnection', 'overviewLastSeen',
    'overviewProtection', 'overviewUsage', 'overviewLocation',
    'overviewAlerts',
  ]) {
    assert.match(html, new RegExp(`id="${id}"`));
  }
  assert.match(html, /function renderOverview\(device, role\)/);
  assert.match(html, /renderOverview\(selectedDevice, selectedRole\)/);
  assert.match(html, /renderOverview\(d, state\.roles\[d\.id\]\)/);
});

test('groups existing commands without changing their command types', () => {
  for (const group of ['protection', 'device', 'communication']) {
    assert.match(html, new RegExp(`data-command-group="${group}"`));
  }
  for (const command of [
    'lock_screen', 'reboot', 'enable_location', 'request_location',
    'start_checkin', 'start_screen_view',
  ]) {
    assert.equal((html.match(new RegExp(`data-cmd="${command}"`, 'g')) || []).length, 1);
  }
  assert.match(html, /\$\('btnCopyConfig'\)\.disabled = !isGuardian/);
  assert.match(html, /const canManageApps = state\.roles\[state\.selected\] === 'guardian'/);
  assert.match(html, /canManageApps \? '' : 'disabled'/);
  assert.match(html, /canManageApps && a\.package !== 'com\.controleinfantil\.kids'/);
});

test('keeps the dashboard responsive and keyboard-visible', () => {
  assert.match(html, /@media \(max-width: 860px\)/);
  assert.match(html, /:focus-visible/);
  assert.match(html, /overflow-x:\s*hidden/);
});

test('preserves the functional panel contracts', () => {
  for (const id of [
    'authCard', 'devicesCard', 'tabsBar', 'commandsCard', 'appsCard',
    'timeCard', 'locationCard', 'peopleCard', 'usageCard', 'historyCard',
    'viewerModal', 'toast',
  ]) {
    assert.match(html, new RegExp(`id="${id}"`));
  }
});
