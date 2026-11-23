import test from 'node:test';
import assert from 'node:assert/strict';
import { copyConfigCommands, uninstallCommandPayload, unpairButtonLabel } from './device-actions.js';

test('uses the explicit Portuguese label for the device-card action', () => {
  assert.equal(unpairButtonLabel(), 'desparear');
});

test('creates app and time-rule commands from a source device configuration', () => {
  assert.deepEqual(copyConfigCommands(
    [{ package: 'com.example.allowed', allowed: true }, { package: 'com.example.blocked', allowed: false }],
    { time_enabled: true, start_minute: 480, end_minute: 1200, daily_limit_minutes: 60 },
  ), [
    { type: 'set_allowed_apps', payload: { packages: ['com.example.allowed'] } },
    { type: 'set_time_rules', payload: { enabled: true, start: 480, end: 1200, limit: 60 } },
  ]);
});

test('creates an uninstall command for a package', () => {
  assert.deepEqual(uninstallCommandPayload('com.example.game'), {
    type: 'uninstall_app',
    payload: { package: 'com.example.game' },
  });
});
