-- Migração 0011: botão de bloquear/desbloquear a tela pelo painel.
--
-- 1. device_status ganha screen_locked: true quando a tela da criança está bloqueada.
-- 2. Novo comando unlock_screen, para tirar o bloqueio pelo painel.
-- 3. device_report_status passa a gravar screen_locked a partir do status enviado
--    pelo aparelho (campo "locked"). Demais campos: igual à migração 0009.

alter table public.device_status
    add column if not exists screen_locked boolean not null default false;

alter table public.commands drop constraint if exists commands_type_check;
alter table public.commands add constraint commands_type_check
    check (type in (
        'lock_screen', 'reboot', 'enable_location', 'request_location',
        'start_screen_view', 'start_checkin',
        'stop_screen_view', 'stop_checkin',
        'set_allowed_apps', 'install_app', 'uninstall_app',
        'set_time_rules', 'grant_time',
        'wake_screen', 'resume_control', 'disable_control',
        'show_message', 'unlock_screen'));

create or replace function public.device_report_status(
    p_device uuid, p_token text, p_status jsonb)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    insert into public.device_status (
        device_id, time_enabled, start_minute, end_minute,
        daily_limit_minutes, used_minutes_today, override_until,
        control_disabled, screen_locked, updated_at)
    values (
        p_device,
        coalesce((p_status ->> 'enabled')::boolean, false),
        coalesce((p_status ->> 'start')::int, 480),
        coalesce((p_status ->> 'end')::int, 1200),
        coalesce((p_status ->> 'limit')::int, 0),
        greatest(coalesce((p_status ->> 'used')::int, 0), 0),
        case when coalesce((p_status ->> 'override_until')::bigint, 0) > 0
             then to_timestamp((p_status ->> 'override_until')::bigint / 1000.0) end,
        coalesce((p_status ->> 'control_disabled')::boolean, false),
        coalesce((p_status ->> 'locked')::boolean, false),
        now())
    on conflict (device_id) do update set
        time_enabled = excluded.time_enabled,
        start_minute = excluded.start_minute,
        end_minute = excluded.end_minute,
        daily_limit_minutes = excluded.daily_limit_minutes,
        used_minutes_today = excluded.used_minutes_today,
        override_until = excluded.override_until,
        control_disabled = excluded.control_disabled,
        screen_locked = excluded.screen_locked,
        updated_at = now();
end $$;

revoke execute on function public.device_report_status(uuid, text, jsonb)
    from anon, authenticated, public;
grant execute on function public.device_report_status(uuid, text, jsonb)
    to anon, authenticated;

insert into public.schema_migrations (version, name)
values ('0011', 'lock_toggle')
on conflict (version) do nothing;
