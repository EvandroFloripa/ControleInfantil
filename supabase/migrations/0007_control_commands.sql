-- Migração 0007: novos tipos de comando.
--
-- wake_screen    acorda a tela de um aparelho ligado (não liga aparelho desligado)
-- resume_control reativa o controle do responsável remotamente (volta a valer
--                horário, limite e quiosque)
--
-- A lista completa de tipos aceitos é recriada aqui, incluindo todos os anteriores.

alter table public.commands drop constraint if exists commands_type_check;
alter table public.commands add constraint commands_type_check
    check (type in (
        'lock_screen', 'reboot', 'enable_location', 'request_location',
        'start_screen_view', 'start_checkin',
        'stop_screen_view', 'stop_checkin',
        'set_allowed_apps', 'install_app', 'uninstall_app',
        'set_time_rules', 'grant_time',
        'wake_screen', 'resume_control'));

insert into public.schema_migrations (version, name)
values ('0007', 'control_commands')
on conflict (version) do nothing;
