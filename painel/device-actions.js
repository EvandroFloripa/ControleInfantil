export function unpairButtonLabel() {
  return 'desparear';
}

export function copyConfigCommands(apps, status) {
  const packages = (apps || [])
    .filter(app => app.allowed && app.package)
    .map(app => app.package);
  const commands = [
    { type: 'set_allowed_apps', payload: { packages } },
  ];
  if (status) {
    commands.push({
      type: 'set_time_rules',
      payload: {
        enabled: Boolean(status.time_enabled),
        start: Number(status.start_minute ?? 480),
        end: Number(status.end_minute ?? 1200),
        limit: Number(status.daily_limit_minutes ?? 0),
      },
    });
  }
  return commands;
}

export function uninstallCommandPayload(packageName) {
  return { type: 'uninstall_app', payload: { package: packageName } };
}
