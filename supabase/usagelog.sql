-- ============================================================================
--  Controle Infantil — histórico de apps abertos
-- ============================================================================
--  Rode DEPOIS de supabase/schema.sql. Guarda os apps que a criança abriu (e se
--  foram bloqueados), reportados pelo aparelho; o painel lê (RLS: só guardião).
-- ============================================================================

create table if not exists public.app_events (
    id         bigint generated always as identity primary key,
    device_id  uuid not null references public.devices(id) on delete cascade,
    package    text not null,
    label      text not null,
    blocked    boolean not null default false,
    at         timestamptz not null default now()
);
create index if not exists app_events_device_time_idx
    on public.app_events (device_id, at desc);

-- ---------------------------------------------------------------------------
--  Lado do APARELHO: envia os eventos em lote e limpa os antigos.
-- ---------------------------------------------------------------------------
create or replace function public.device_report_app_events(
    p_device uuid, p_token text, p_events jsonb)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    if jsonb_typeof(p_events) <> 'array' then
        raise exception 'invalid_payload' using errcode = '22023';
    end if;
    if jsonb_array_length(p_events) > 500 then
        raise exception 'too_many_events' using errcode = '22023';
    end if;

    insert into public.app_events (device_id, package, label, blocked, at)
    select p_device,
           left(e ->> 'package', 255),
           left(coalesce(nullif(e ->> 'label', ''), e ->> 'package'), 255),
           coalesce((e ->> 'blocked')::boolean, false),
           coalesce(to_timestamp((e ->> 'at')::bigint / 1000.0), now())
    from jsonb_array_elements(p_events) e
    where e ->> 'package' is not null;

    -- Guarda no máximo os últimos 14 dias por aparelho.
    delete from public.app_events
        where device_id = p_device and at < now() - interval '14 days';
end $$;

revoke execute on function public.device_report_app_events(uuid, text, jsonb)
    from anon, authenticated, public;
grant execute on function public.device_report_app_events(uuid, text, jsonb)
    to anon, authenticated;

-- ---------------------------------------------------------------------------
--  Lado do CONTROLADOR: só guardião do aparelho lê o histórico (RLS).
-- ---------------------------------------------------------------------------
alter table public.app_events enable row level security;

grant select on public.app_events to authenticated;

drop policy if exists app_events_select on public.app_events;
create policy app_events_select on public.app_events
    for select to authenticated using (private.is_guardian(device_id));
