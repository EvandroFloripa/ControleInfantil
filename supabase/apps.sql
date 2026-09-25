-- ============================================================================
--  Controle Infantil — gestão remota de apps (pelo painel)
-- ============================================================================
--  Rode DEPOIS de supabase/schema.sql. Permite ao responsável, do painel:
--    - ver os apps instalados no celular da criança e marcar quais ela pode abrir
--      (comando set_allowed_apps);
--    - mandar instalar um app pelo Google Play (comando install_app).
--
--  O aparelho reporta a própria lista de apps (device_report_apps); o painel lê
--  essa lista (RLS: só guardião) e envia os comandos por controller_send_command.
-- ============================================================================

-- Aceita os comandos novos, mesmo em bancos que já rodaram uma versão anterior.
alter table public.commands drop constraint if exists commands_type_check;
alter table public.commands add constraint commands_type_check
    check (type in (
        'lock_screen', 'reboot', 'enable_location', 'request_location',
        'start_screen_view', 'start_checkin',
        'stop_screen_view', 'stop_checkin',
        'set_allowed_apps', 'install_app',
        'set_time_rules', 'grant_time'));

create table if not exists public.device_apps (
    device_id  uuid not null references public.devices(id) on delete cascade,
    package    text not null,
    label      text not null,
    allowed    boolean not null default false,
    category   text not null default 'other',
    updated_at timestamptz not null default now(),
    primary key (device_id, package)
);

-- Coluna acrescentada depois: garante em bancos que já rodaram uma versão anterior.
alter table public.device_apps add column if not exists category text not null default 'other';

-- ---------------------------------------------------------------------------
--  Lado do APARELHO: reporta os apps abríveis e se cada um está liberado.
-- ---------------------------------------------------------------------------
create or replace function public.device_report_apps(
    p_device uuid, p_token text, p_apps jsonb)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    if jsonb_typeof(p_apps) <> 'array' then
        raise exception 'invalid_payload' using errcode = '22023';
    end if;
    if jsonb_array_length(p_apps) > 500 then
        raise exception 'too_many_apps' using errcode = '22023';
    end if;

    -- Substitui a lista por inteiro: some o que foi desinstalado.
    delete from public.device_apps where device_id = p_device;
    insert into public.device_apps (device_id, package, label, allowed, category)
    select p_device,
           left(e ->> 'package', 255),
           left(coalesce(nullif(e ->> 'label', ''), e ->> 'package'), 255),
           coalesce((e ->> 'allowed')::boolean, false),
           left(coalesce(nullif(e ->> 'category', ''), 'other'), 32)
    from jsonb_array_elements(p_apps) e
    where e ->> 'package' is not null
    on conflict (device_id, package) do update
        set label = excluded.label, allowed = excluded.allowed,
            category = excluded.category, updated_at = now();
end $$;

revoke execute on function public.device_report_apps(uuid, text, jsonb)
    from anon, authenticated, public;
grant execute on function public.device_report_apps(uuid, text, jsonb)
    to anon, authenticated;

-- O aparelho informa o próprio nome (modelo). Só troca enquanto o nome ainda é o
-- padrão — nunca sobrescreve um nome que o responsável tenha definido no painel.
create or replace function public.device_set_label(
    p_device uuid, p_token text, p_label text)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    update public.devices
        set label = coalesce(nullif(left(trim(p_label), 60), ''), label)
        where id = p_device and label = 'Celular da criança';
end $$;

revoke execute on function public.device_set_label(uuid, text, text)
    from anon, authenticated, public;
grant execute on function public.device_set_label(uuid, text, text)
    to anon, authenticated;

-- ---------------------------------------------------------------------------
--  Lado do CONTROLADOR: só guardião do aparelho lê a lista (RLS).
-- ---------------------------------------------------------------------------
alter table public.device_apps enable row level security;

grant select on public.device_apps to authenticated;

drop policy if exists device_apps_select on public.device_apps;
create policy device_apps_select on public.device_apps
    for select to authenticated using (private.is_guardian(device_id));
