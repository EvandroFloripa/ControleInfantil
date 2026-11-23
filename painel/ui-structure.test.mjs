import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const html = await readFile(new URL('./index.html', import.meta.url), 'utf8');
const commandTypes = [
  'lock_screen', 'reboot', 'enable_location', 'request_location',
  'start_checkin', 'start_screen_view', 'wake_screen',
];

function inlineClassicScript() {
  const scripts = [...html.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)];
  assert.equal(scripts.length, 1, 'o painel deve ter um único script inline');
  assert.doesNotMatch(scripts[0][1], /\btype\s*=\s*["']module["']/);
  assert.doesNotMatch(scripts[0][2], /device-actions\.js/);
  return scripts[0][2];
}

function fakeElement(attributes = {}) {
  const classes = new Set(['hidden']);
  const attrs = new Map(Object.entries(attributes));
  return {
    children: [],
    classList: {
      add: (...names) => names.forEach(name => classes.add(name)),
      remove: (...names) => names.forEach(name => classes.delete(name)),
      contains: name => classes.has(name),
      toggle: (name, force) => {
        const enabled = force === undefined ? !classes.has(name) : force;
        if (enabled) classes.add(name); else classes.delete(name);
        return enabled;
      },
    },
    style: {},
    textContent: '',
    innerHTML: '',
    value: '',
    checked: false,
    disabled: false,
    appendChild(child) { this.children.push(child); },
    addEventListener() {},
    querySelector() { return null; },
    querySelectorAll() { return []; },
    getAttribute(name) { return attrs.get(name) ?? null; },
    setAttribute(name, value) { attrs.set(name, value); },
  };
}

function bootPanel() {
  const elements = new Map();
  const buttons = commandTypes.map(type => fakeElement({ 'data-cmd': type }));
  const fetchCalls = [];
  const storage = { getItem: () => null, setItem() {}, removeItem() {}, clear() {} };
  const document = {
    getElementById(id) {
      if (!elements.has(id)) elements.set(id, fakeElement());
      return elements.get(id);
    },
    createElement: () => fakeElement(),
    querySelectorAll: selector => selector === '[data-cmd]' ? buttons : [],
  };
  const context = {
    document,
    window: { addEventListener() {} },
    localStorage: storage,
    sessionStorage: storage,
    location: { hash: '', origin: 'https://example.test', pathname: '/painel/', search: '', reload() {} },
    history: { replaceState() {} },
    URLSearchParams,
    crypto: { randomUUID: () => '00000000-0000-4000-8000-000000000000' },
    console,
    confirm: () => true,
    prompt: () => null,
    setTimeout: () => 0,
    setInterval: () => 0,
    clearInterval() {},
    fetch: async (url, options = {}) => {
      fetchCalls.push({ url, options });
      return { ok: true, status: 200, text: async () => '', json: async () => [] };
    },
  };
  const source = inlineClassicScript() +
    '\n;globalThis.__panelTest = { state, renderDevices, selectDevice, renderApps,' +
    ' setDeviceStatus: s => { deviceStatus = s; renderStatus(); },' +
    ' setDeviceApps: list => {' +
    ' deviceApps = list; allowedSet = new Set(list.filter(a => a.allowed).map(a => a.package));' +
    ' savedSet = new Set(allowedSet); } };';
  new vm.Script(source, { filename: 'painel/index.html:inline' }).runInNewContext(context);
  return { buttons, elements, fetchCalls, panel: context.__panelTest };
}

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
  const actualCommands = [...html.matchAll(/\bdata-cmd="([^"]+)"/g)].map(match => match[1]);
  assert.deepEqual(actualCommands, commandTypes);
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

test('initializes without external modules and wires the existing command handler', async () => {
  const harness = bootPanel();
  harness.buttons.forEach(button => assert.equal(typeof button.onclick, 'function'));

  harness.panel.state.selected = 'device-1';
  harness.panel.state.url = 'https://example.test';
  harness.panel.state.key = 'anon-key';
  harness.buttons[0].onclick();
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(harness.fetchCalls.length, 1);
  assert.match(harness.fetchCalls[0].url, /\/rpc\/controller_send_command$/);
  assert.deepEqual(JSON.parse(harness.fetchCalls[0].options.body), {
    p_device: 'device-1',
    p_type: 'lock_screen',
  });
});

test('announces toasts and secondary-tab content to assistive technology', () => {
  assert.match(html, /id="toast"[^>]*role="status"[^>]*aria-live="polite"/);
  for (const id of ['appsList', 'timeUsed', 'locationInfo', 'peopleList', 'usageList', 'historyList']) {
    assert.match(html, new RegExp(`id="${id}"[^>]*aria-live="polite"`));
  }
});

test('defines the loading, empty, error and denied states and honours reduced motion', () => {
  for (const kind of ['loading', 'empty', 'error', 'denied']) {
    assert.match(html, new RegExp(`\\[data-state="${kind}"\\]`));
  }
  assert.match(html, /@media \(prefers-reduced-motion: reduce\)/);
});

test('shows a read-only notice to viewers in the apps and time tabs only', () => {
  for (const id of ['appsPermission', 'timePermission']) {
    assert.match(html, new RegExp(`id="${id}"`));
  }
  const harness = bootPanel();
  harness.panel.state.userId = 'viewer-1';
  harness.panel.state.roles = { 'device-1': 'viewer' };
  harness.panel.selectDevice({ id: 'device-1', label: 'Celular', last_seen: null });
  assert.equal(harness.elements.get('appsPermission').classList.contains('hidden'), false);
  assert.equal(harness.elements.get('timePermission').classList.contains('hidden'), false);

  const guardian = bootPanel();
  guardian.panel.state.userId = 'guardian-1';
  guardian.panel.state.roles = { 'device-1': 'guardian' };
  guardian.panel.selectDevice({ id: 'device-1', label: 'Celular', last_seen: null });
  assert.equal(guardian.elements.get('appsPermission').classList.contains('hidden'), true);
  assert.equal(guardian.elements.get('timePermission').classList.contains('hidden'), true);
});

test('marks the apps list as loading before the device replies', () => {
  const harness = bootPanel();
  harness.panel.state.userId = 'guardian-1';
  harness.panel.state.roles = { 'device-1': 'guardian' };
  harness.panel.selectDevice({ id: 'device-1', label: 'Celular', last_seen: null });
  assert.equal(harness.elements.get('appsList').getAttribute('data-state'), 'loading');
});

test('lets the device list and the overview be collapsed and reopened', () => {
  for (const id of ['devicesCard', 'overviewCard']) {
    assert.match(html, new RegExp(`<details[^>]*id="${id}"[^>]*open`));
    assert.match(html, new RegExp(`id="${id}"[\\s\\S]*?<summary`));
  }
  assert.match(html, /id="devicesSummary"/);

  const harness = bootPanel();
  harness.panel.state.userId = 'guardian-1';
  harness.panel.state.roles = { 'device-1': 'guardian' };
  harness.panel.selectDevice({ id: 'device-1', label: 'Celular', last_seen: null });
  assert.equal(harness.elements.get('devicesCard').open, false);
  assert.match(harness.elements.get('devicesSummary').textContent, /Celular/);
});

test('gives each navigation tab an icon and keeps its label for assistive technology', () => {
  const tabsBar = html.match(/<div id="tabsBar"[\s\S]*?<\/div>/)[0];
  const buttons = tabsBar.match(/<button [^>]*data-tab=[\s\S]*?<\/button>/g);
  assert.equal(buttons.length, 6);
  for (const button of buttons) {
    assert.match(button, /class="tab-icon"[^>]*aria-hidden="true"/);
    assert.match(button, /class="tab-label"/);
    assert.match(button, /title="[^"]+"/);
  }
  const inicio = html.indexOf('@media (max-width: 860px)');
  const tablet = html.slice(inicio, html.indexOf('@media', inicio + 10));
  assert.match(tablet, /\.tab-label\s*\{/);
  assert.match(tablet, /\.tab-icon\s*\{/);
});

test('organizes the apps section as the approved prototype', () => {
  for (const id of ['appsSavebar', 'appsPending', 'btnDiscardApps', 'btnSaveApps', 'appsInstall']) {
    assert.match(html, new RegExp(`id="${id}"`));
  }
  assert.match(html, /<details[^>]*id="appsInstall"/);
  assert.match(html, /role="switch"/);
  assert.match(html, /class="cat-node"/);
});

test('renders app switches read-only for viewers and editable for guardians', () => {
  const apps = [{ package: 'com.a', label: 'App A', category: 'game', allowed: true }];

  const viewer = bootPanel();
  viewer.panel.state.selected = 'device-1';
  viewer.panel.state.roles = { 'device-1': 'viewer' };
  viewer.panel.setDeviceApps(apps);
  viewer.panel.renderApps();
  assert.match(viewer.elements.get('appsList').innerHTML, /role="switch"[\s\S]*?aria-checked="true"[\s\S]*?disabled/);

  const guardian = bootPanel();
  guardian.panel.state.selected = 'device-1';
  guardian.panel.state.roles = { 'device-1': 'guardian' };
  guardian.panel.setDeviceApps(apps);
  guardian.panel.renderApps();
  const guardianHtml = guardian.elements.get('appsList').innerHTML;
  assert.match(guardianHtml, /role="switch"[\s\S]*?aria-checked="true"/);
  assert.doesNotMatch(guardianHtml, /disabled/);
});

test('offers uninstall to guardians only, and never for the panel app itself', () => {
  const apps = [
    { package: 'com.a', label: 'App A', category: 'game', allowed: true },
    { package: 'com.controleinfantil.kids', label: 'Controle Infantil', category: 'productivity', allowed: true },
  ];

  const viewer = bootPanel();
  viewer.panel.state.selected = 'device-1';
  viewer.panel.state.roles = { 'device-1': 'viewer' };
  viewer.panel.setDeviceApps(apps);
  viewer.panel.renderApps();
  assert.doesNotMatch(viewer.elements.get('appsList').innerHTML, /data-uninstall/);

  const guardian = bootPanel();
  guardian.panel.state.selected = 'device-1';
  guardian.panel.state.roles = { 'device-1': 'guardian' };
  guardian.panel.setDeviceApps(apps);
  guardian.panel.renderApps();
  const html2 = guardian.elements.get('appsList').innerHTML;
  assert.match(html2, /data-uninstall="com\.a"/);
  assert.doesNotMatch(html2, /data-uninstall="com\.controleinfantil\.kids"/);
});

test('never injects device-controlled category text into the apps markup', () => {
  const harness = bootPanel();
  harness.panel.state.selected = 'device-1';
  harness.panel.state.roles = { 'device-1': 'guardian' };
  harness.panel.setDeviceApps([{
    package: 'com.a', label: 'App A', allowed: true,
    category: '"><img src=x onerror=alert(1)>',
  }]);
  harness.panel.renderApps();
  const markup = harness.elements.get('appsList').innerHTML;
  assert.doesNotMatch(markup, /<img/);
  assert.match(markup, /data-cat-all="other"/);
});

test('organizes time, location and history as the approved prototype', () => {
  for (const id of ['timeMeter', 'timeMeterValue', 'timeMeterBar', 'timeSavebar', 'timePending',
    'btnDiscardTime', 'btnSaveTime', 'locationMapArea', 'usageFilter']) {
    assert.match(html, new RegExp(`id="${id}"`));
  }
  for (const f of ['all', 'blocked']) assert.match(html, new RegExp(`data-usage-filter="${f}"`));
  assert.match(html, /class="event"/);
});

test('shows the daily usage meter against the daily limit', () => {
  const harness = bootPanel();
  harness.panel.state.selected = 'device-1';
  harness.panel.setDeviceStatus({
    time_enabled: true, start_minute: 480, end_minute: 1200,
    daily_limit_minutes: 60, used_minutes_today: 50, override_until: null,
  });
  assert.equal(harness.elements.get('timeMeterValue').textContent, '50 min de 60 min');
  assert.equal(harness.elements.get('timeMeterBar').getAttribute('data-warn'), 'true');
});

test('keeps the copy-configuration action in the commands header', () => {
  assert.match(html, /id="btnCopyConfig"/);
  assert.match(html, /class="cmd-head"[\s\S]*?id="btnCopyConfig"/);
});

test('renders viewer device cards without an unlink control', () => {
  const harness = bootPanel();
  harness.panel.state.userId = 'viewer-1';
  harness.panel.state.devices = [{ id: 'device-1', label: 'Celular', last_seen: null }];
  harness.panel.state.roles = { 'device-1': 'viewer' };

  harness.panel.renderDevices();

  const cards = harness.elements.get('devicesList').children;
  assert.equal(cards.length, 1);
  assert.doesNotMatch(cards[0].innerHTML, /data-unpair|desparear/);
});
