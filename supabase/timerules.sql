-- ============================================================================
--  Controle Infantil — limites de tempo pelo painel
-- ============================================================================
--  Rode DEPOIS de supabase/schema.sql (e é o mais recente da lista de comandos,
--  então rode-o por último). Permite ao responsável definir, do painel, a janela
--  de horário e o teto de tempo por dia; o aparelho aplica (comando set_time_rules)
--  e reporta o estado atual + o tempo já usado hoje (device_report_status).
-- ============================================================================

-- Lista completa e atual de comandos (inclui apps e limites de tempo).
alter table public.commands drop constraint if exists commands_type_check;
alter table public.commands add constraint commands_type_check
    check (type in (
        'lock_screen', 'reboot', 'enable_location', 'request_location',
        'start_screen_view', 'start_checkin',
        'stop_screen_view', 'stop_checkin',
        'set_allowed_apps', 'install_app',
        'set_time_rules', 'grant_time'));

create table if not exists public.device_status (
    device_id           uuid primary key references public.devices(id) on delete cascade,
    time_enabled        boolean not null default false,
    start_minute        int not null default 480,   -- 08:00
    end_minute          int not null default 1200,  -- 20:00
    daily_limit_minutes int not null default 0,     -- 0 = sem teto
    used_minutes_today  int not null default 0,
    override_until      timestamptz,                -- tempo extra liberado até
    updated_at          timestamptz not null default now()
);
alter table public.device_status add column if not exists override_until timestamptz;

-- ---------------------------------------------------------------------------
--  Lado do APARELHO: reporta as regras vigentes e o uso de hoje.
-- ---------------------------------------------------------------------------
create or replace function public.device_report_status(
    p_device uuid, p_token text, p_status jsonb)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    insert into public.device_status (
        device_id, time_enabled, start_minute, end_minute,
        daily_limit_minutes, used_minutes_today, override_until, updated_at)
    values (
        p_device,
        coalesce((p_status ->> 'enabled')::boolean, false),
        coalesce((p_status ->> 'start')::int, 480),
        coalesce((p_status ->> 'end')::int, 1200),
        coalesce((p_status ->> 'limit')::int, 0),
        greatest(coalesce((p_status ->> 'used')::int, 0), 0),
        case when coalesce((p_status ->> 'override_until')::bigint, 0) > 0
             then to_timestamp((p_status ->> 'override_until')::bigint / 1000.0) end,
        now())
    on conflict (device_id) do update set
        time_enabled = excluded.time_enabled,
        start_minute = excluded.start_minute,
        end_minute = excluded.end_minute,
        daily_limit_minutes = excluded.daily_limit_minutes,
        used_minutes_today = excluded.used_minutes_today,
        override_until = excluded.override_until,
        updated_at = now();
end $$;

revoke execute on function public.device_report_status(uuid, text, jsonb)
    from anon, authenticated, public;
grant execute on function public.device_report_status(uuid, text, jsonb)
    to anon, authenticated;

-- ---------------------------------------------------------------------------
--  Lado do CONTROLADOR: só guardião do aparelho lê o status (RLS).
-- ---------------------------------------------------------------------------
alter table public.device_status enable row level security;

grant select on public.device_status to authenticated;

drop policy if exists device_status_select on public.device_status;
create policy device_status_select on public.device_status
    for select to authenticated using (private.is_guardian(device_id));
