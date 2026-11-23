-- Migração 0009: estado do controle do responsável no painel.
--
-- 1. device_status ganha control_disabled: true quando o controle está desativado
--    no aparelho (tudo liberado até reativar).
-- 2. Novo comando disable_control, para desativar o controle pelo painel.
-- 3. device_report_status passa a gravar control_disabled a partir do status enviado
--    pelo aparelho.

alter table public.device_status
    add column if not exists control_disabled boolean not null default false;

alter table public.commands drop constraint if exists commands_type_check;
alter table public.commands add constraint commands_type_check
    check (type in (
        'lock_screen', 'reboot', 'enable_location', 'request_location',
        'start_screen_view', 'start_checkin',
        'stop_screen_view', 'stop_checkin',
        'set_allowed_apps', 'install_app', 'uninstall_app',
        'set_time_rules', 'grant_time',
        'wake_screen', 'resume_control', 'disable_control'));

create or replace function public.device_report_status(
    p_device uuid, p_token text, p_status jsonb)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    insert into public.device_status (
        device_id, time_enabled, start_minute, end_minute,
        daily_limit_minutes, used_minutes_today, override_until,
        control_disabled, updated_at)
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
        now())
    on conflict (device_id) do update set
        time_enabled = excluded.time_enabled,
        start_minute = excluded.start_minute,
        end_minute = excluded.end_minute,
        daily_limit_minutes = excluded.daily_limit_minutes,
        used_minutes_today = excluded.used_minutes_today,
        override_until = excluded.override_until,
        control_disabled = excluded.control_disabled,
        updated_at = now();
end $$;

revoke execute on function public.device_report_status(uuid, text, jsonb)
    from anon, authenticated, public;
grant execute on function public.device_report_status(uuid, text, jsonb)
    to anon, authenticated;

insert into public.schema_migrations (version, name)
values ('0009', 'control_state')
on conflict (version) do nothing;
