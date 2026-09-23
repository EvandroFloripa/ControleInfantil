-- ============================================================================
--  Controle Infantil — esquema do backend (Supabase / PostgreSQL)
-- ============================================================================
--  Rode este arquivo inteiro no SQL Editor do seu projeto Supabase.
--
--  Quem é quem:
--    - APARELHO (celular da criança): não faz login. Ao se registrar recebe um
--      token secreto; o banco guarda só o hash (SHA-256). O aparelho NÃO acessa
--      nenhuma tabela diretamente — só chama as funções `device_*`, que conferem
--      o token.
--    - CONTROLADOR (responsável): faz login pelo Supabase Auth (e-mail + senha).
--      Enxerga apenas os aparelhos aos quais está vinculado, e age pelas funções
--      `controller_*`.
--
--  Papéis de um controlador em um aparelho:
--    - guardian (guardião): envia comandos, convida e remove outros controladores.
--    - viewer (observador): só vê aparelho, localização e histórico.
--
--  Pareamento:
--    - O PRIMEIRO controlador pareia com um código gerado no próprio celular da
--      criança (só funciona enquanto o aparelho não tem nenhum controlador).
--    - Os seguintes entram por CONVITE gerado por um guardião no painel.
--    - Códigos: 8 caracteres, uso único, valem 15 minutos; no máximo 10
--      tentativas erradas por hora por controlador.
--
--  Se você rodou a versão MVP anterior deste arquivo (políticas "mvp_anon_all"),
--  apague as tabelas antigas antes, rodando:
--    drop table if exists public.locations, public.commands, public.pairing_codes,
--      public.device_controllers, public.controllers, public.devices cascade;
-- ============================================================================

create extension if not exists pgcrypto with schema extensions;

-- Funções auxiliares ficam num schema que a API (PostgREST) não expõe.
create schema if not exists private;

-- ============================================================================
--  Tabelas
-- ============================================================================

create table if not exists public.devices (
    id          uuid primary key default gen_random_uuid(),
    label       text not null default 'Celular da criança'
                check (char_length(label) between 1 and 60),
    token_hash  bytea not null,            -- SHA-256 do token do aparelho
    last_seen   timestamptz,
    created_at  timestamptz not null default now()
);

-- Perfil do controlador; uma linha por usuário do Supabase Auth.
create table if not exists public.controllers (
    id          uuid primary key references auth.users(id) on delete cascade,
    email       text,
    name        text check (char_length(name) <= 60),
    created_at  timestamptz not null default now()
);

create table if not exists public.device_controllers (
    device_id       uuid not null references public.devices(id) on delete cascade,
    controller_id   uuid not null references public.controllers(id) on delete cascade,
    role            text not null default 'viewer'
                    check (role in ('guardian', 'viewer')),
    created_at      timestamptz not null default now(),
    primary key (device_id, controller_id)
);
create index if not exists device_controllers_controller_idx
    on public.device_controllers (controller_id);

create table if not exists public.pairing_codes (
    code        text primary key,
    device_id   uuid not null references public.devices(id) on delete cascade,
    role        text not null check (role in ('guardian', 'viewer')),
    created_by  uuid references public.controllers(id) on delete cascade, -- null = gerado no aparelho
    expires_at  timestamptz not null,
    used_at     timestamptz,
    used_by     uuid references public.controllers(id) on delete set null,
    created_at  timestamptz not null default now()
);

create table if not exists public.pairing_attempts (
    id          bigint generated always as identity primary key,
    user_id     uuid not null,
    success     boolean not null,
    at          timestamptz not null default now()
);
create index if not exists pairing_attempts_user_idx
    on public.pairing_attempts (user_id, at);

create table if not exists public.commands (
    id              uuid primary key default gen_random_uuid(),
    device_id       uuid not null references public.devices(id) on delete cascade,
    controller_id   uuid references public.controllers(id) on delete set null,
    type            text not null check (type in (
                        'lock_screen', 'reboot', 'enable_location', 'request_location',
                        'start_screen_view', 'start_checkin',
                        'stop_screen_view', 'stop_checkin')),
    payload         jsonb not null default '{}'::jsonb,
    status          text not null default 'pending'
                    check (status in ('pending', 'done', 'error', 'expired')),
    result_detail   text,
    created_at      timestamptz not null default now(),
    executed_at     timestamptz
);
create index if not exists commands_device_pending_idx
    on public.commands (device_id, status, created_at);

create table if not exists public.locations (
    id          uuid primary key default gen_random_uuid(),
    device_id   uuid not null references public.devices(id) on delete cascade,
    lat         double precision not null check (lat between -90 and 90),
    lon         double precision not null check (lon between -180 and 180),
    accuracy    double precision check (accuracy >= 0),
    created_at  timestamptz not null default now()
);
create index if not exists locations_device_time_idx
    on public.locations (device_id, created_at desc);

-- ============================================================================
--  Perfil automático para cada novo usuário do Auth
-- ============================================================================

create or replace function private.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    insert into public.controllers (id, email)
    values (new.id, new.email)
    on conflict (id) do nothing;
    return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function private.handle_new_user();

-- ============================================================================
--  Auxiliares (schema private — não expostas pela API)
-- ============================================================================

create or replace function private.is_linked(p_device uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.device_controllers
        where device_id = p_device and controller_id = auth.uid());
$$;

create or replace function private.is_guardian(p_device uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.device_controllers
        where device_id = p_device and controller_id = auth.uid() and role = 'guardian');
$$;

-- Verdadeiro se o controlador informado divide algum aparelho com o usuário atual.
create or replace function private.shares_device(p_controller uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select exists (
        select 1
        from public.device_controllers mine
        join public.device_controllers theirs on theirs.device_id = mine.device_id
        where mine.controller_id = auth.uid() and theirs.controller_id = p_controller);
$$;

-- Confere o token do aparelho; lança erro se não bater.
create or replace function private.assert_device(p_device uuid, p_token text)
returns void language plpgsql stable security definer set search_path = '' as $$
begin
    if p_device is null or p_token is null or not exists (
        select 1 from public.devices
        where id = p_device
          and token_hash = extensions.digest(p_token, 'sha256'))
    then
        raise exception 'device_auth_failed' using errcode = '28000';
    end if;
end $$;

-- Código de 8 caracteres sem letras/dígitos ambíguos (sem 0/O, 1/I).
-- 32 símbolos: cada byte aleatório % 32 é uniforme.
create or replace function private.new_code()
returns text language plpgsql volatile security definer set search_path = '' as $$
declare
    alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    bytes bytea := extensions.gen_random_bytes(8);
    result text := '';
begin
    for i in 0..7 loop
        result := result || substr(alphabet, (get_byte(bytes, i) % 32) + 1, 1);
    end loop;
    return result;
end $$;

-- ============================================================================
--  Funções do APARELHO (chamadas com a chave anon + token do aparelho)
-- ============================================================================

-- Registra um aparelho novo e devolve o id e o token (mostrado só uma vez).
create or replace function public.device_register(p_label text default null)
returns table (device_id uuid, device_token text)
language plpgsql volatile security definer set search_path = '' as $$
declare
    v_token text := encode(extensions.gen_random_bytes(32), 'hex');
    v_id uuid;
begin
    insert into public.devices (label, token_hash)
    values (
        coalesce(nullif(left(trim(p_label), 60), ''), 'Celular da criança'),
        extensions.digest(v_token, 'sha256'))
    returning id into v_id;
    return query select v_id, v_token;
end $$;

create or replace function public.device_heartbeat(p_device uuid, p_token text)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    update public.devices set last_seen = now() where id = p_device;
end $$;

-- Devolve os comandos pendentes. Comandos com mais de 10 minutos expiram em vez
-- de serem executados (um "reiniciar" esquecido não dispara horas depois).
create or replace function public.device_fetch_commands(p_device uuid, p_token text)
returns table (id uuid, type text, payload jsonb)
language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);

    update public.devices set last_seen = now() where public.devices.id = p_device;

    update public.commands c
    set status = 'expired',
        executed_at = now(),
        result_detail = 'Expirou antes de chegar ao aparelho'
    where c.device_id = p_device
      and c.status = 'pending'
      and c.created_at < now() - interval '10 minutes';

    return query
        select c.id, c.type, c.payload
        from public.commands c
        where c.device_id = p_device and c.status = 'pending'
        order by c.created_at;
end $$;

create or replace function public.device_report_result(
    p_device uuid, p_token text, p_command uuid, p_status text, p_detail text default null)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    if p_status not in ('done', 'error') then
        raise exception 'invalid_status' using errcode = '22023';
    end if;
    update public.commands
    set status = p_status,
        result_detail = left(p_detail, 500),
        executed_at = now()
    where id = p_command and device_id = p_device and status = 'pending';
end $$;

create or replace function public.device_post_location(
    p_device uuid, p_token text, p_lat double precision, p_lon double precision,
    p_accuracy double precision default null)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    insert into public.locations (device_id, lat, lon, accuracy)
    values (p_device, p_lat, p_lon, p_accuracy);
end $$;

-- Gera o código do PRIMEIRO pareamento. Recusado se o aparelho já tiver algum
-- controlador: a partir daí, só um guardião convida pelo painel.
create or replace function public.device_create_pairing_code(p_device uuid, p_token text)
returns table (code text, expires_at timestamptz)
language plpgsql volatile security definer set search_path = '' as $$
declare
    v_code text;
    v_expires timestamptz := now() + interval '15 minutes';
begin
    perform private.assert_device(p_device, p_token);
    if exists (select 1 from public.device_controllers dc where dc.device_id = p_device) then
        raise exception 'device_already_paired' using errcode = '42501';
    end if;
    delete from public.pairing_codes pc
    where pc.device_id = p_device and pc.created_by is null;
    v_code := private.new_code();
    insert into public.pairing_codes (code, device_id, role, created_by, expires_at)
    values (v_code, p_device, 'guardian', null, v_expires);
    return query select v_code, v_expires;
end $$;

-- ============================================================================
--  Funções do CONTROLADOR (exigem login no Supabase Auth)
-- ============================================================================

-- Usa um código (do aparelho ou de convite). Devolve JSON em vez de lançar erro
-- para que a tentativa falha fique registrada (um erro desfaria o registro).
create or replace function public.controller_claim_code(p_code text)
returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
    v_uid uuid := auth.uid();
    v_row public.pairing_codes;
    v_fails int;
begin
    if v_uid is null then
        raise exception 'not_authenticated' using errcode = '28000';
    end if;

    select count(*) into v_fails
    from public.pairing_attempts
    where user_id = v_uid and not success and at > now() - interval '1 hour';
    if v_fails >= 10 then
        return jsonb_build_object('ok', false, 'error', 'too_many_attempts');
    end if;

    select * into v_row
    from public.pairing_codes
    where code = upper(trim(p_code)) and used_at is null and expires_at > now()
    for update;

    if not found then
        insert into public.pairing_attempts (user_id, success) values (v_uid, false);
        return jsonb_build_object('ok', false, 'error', 'invalid_code');
    end if;

    update public.pairing_codes
    set used_at = now(), used_by = v_uid
    where code = v_row.code;

    -- Garante o perfil mesmo que o trigger do Auth não tenha rodado.
    insert into public.controllers (id) values (v_uid) on conflict (id) do nothing;

    -- Nunca rebaixa um guardião para observador.
    insert into public.device_controllers (device_id, controller_id, role)
    values (v_row.device_id, v_uid, v_row.role)
    on conflict (device_id, controller_id) do update
        set role = case when public.device_controllers.role = 'guardian'
                        then 'guardian' else excluded.role end;

    insert into public.pairing_attempts (user_id, success) values (v_uid, true);
    return jsonb_build_object('ok', true, 'device_id', v_row.device_id);
end $$;

-- Guardião gera um convite para outro familiar.
create or replace function public.controller_create_invite(p_device uuid, p_role text default 'viewer')
returns table (code text, expires_at timestamptz)
language plpgsql volatile security definer set search_path = '' as $$
declare
    v_code text;
    v_expires timestamptz := now() + interval '15 minutes';
begin
    if not private.is_guardian(p_device) then
        raise exception 'not_guardian' using errcode = '42501';
    end if;
    if p_role not in ('guardian', 'viewer') then
        raise exception 'invalid_role' using errcode = '22023';
    end if;
    v_code := private.new_code();
    insert into public.pairing_codes (code, device_id, role, created_by, expires_at)
    values (v_code, p_device, p_role, auth.uid(), v_expires);
    return query select v_code, v_expires;
end $$;

create or replace function public.controller_send_command(
    p_device uuid, p_type text, p_payload jsonb default '{}'::jsonb)
returns uuid language plpgsql volatile security definer set search_path = '' as $$
declare
    v_id uuid;
begin
    if not private.is_guardian(p_device) then
        raise exception 'not_guardian' using errcode = '42501';
    end if;
    if pg_column_size(coalesce(p_payload, '{}'::jsonb)) > 4096 then
        raise exception 'payload_too_large' using errcode = '22023';
    end if;
    if (select count(*) from public.commands
        where device_id = p_device and created_at > now() - interval '10 minutes') >= 30 then
        raise exception 'rate_limited' using errcode = '53400';
    end if;
    insert into public.commands (device_id, controller_id, type, payload)
    values (p_device, auth.uid(), p_type, coalesce(p_payload, '{}'::jsonb))
    returning id into v_id;
    return v_id;
end $$;

-- Remove um controlador do aparelho. Qualquer um pode sair; só guardião remove
-- outros. Não deixa o aparelho sem guardião enquanto houver outros controladores.
create or replace function public.controller_unlink(p_device uuid, p_controller uuid)
returns void language plpgsql volatile security definer set search_path = '' as $$
declare
    v_target_role text;
begin
    if p_controller <> auth.uid() and not private.is_guardian(p_device) then
        raise exception 'not_guardian' using errcode = '42501';
    end if;

    select role into v_target_role
    from public.device_controllers
    where device_id = p_device and controller_id = p_controller
    for update;
    if not found then
        return;
    end if;

    if v_target_role = 'guardian'
       and (select count(*) from public.device_controllers
            where device_id = p_device and role = 'guardian') = 1
       and (select count(*) from public.device_controllers
            where device_id = p_device) > 1
    then
        raise exception 'last_guardian' using errcode = '42501';
    end if;

    delete from public.device_controllers
    where device_id = p_device and controller_id = p_controller;
end $$;

create or replace function public.controller_rename_device(p_device uuid, p_label text)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    if not private.is_guardian(p_device) then
        raise exception 'not_guardian' using errcode = '42501';
    end if;
    update public.devices
    set label = coalesce(nullif(left(trim(p_label), 60), ''), label)
    where id = p_device;
end $$;

-- ============================================================================
--  Permissões e RLS
-- ============================================================================
--  O Supabase dá, por padrão, acesso total às tabelas e funções novas para
--  `anon` e `authenticated`. Aqui tiramos tudo e liberamos só o necessário.
-- ============================================================================

revoke all on
    public.devices, public.controllers, public.device_controllers,
    public.pairing_codes, public.pairing_attempts, public.commands, public.locations
    from anon, authenticated, public;

revoke all on schema private from anon, authenticated, public;
revoke execute on all functions in schema private from anon, authenticated, public;
-- As políticas de RLS abaixo rodam como o usuário e precisam destas três.
grant usage on schema private to authenticated;
grant execute on function
    private.is_linked(uuid), private.is_guardian(uuid), private.shares_device(uuid)
    to authenticated;

revoke execute on function
    public.device_register(text),
    public.device_heartbeat(uuid, text),
    public.device_fetch_commands(uuid, text),
    public.device_report_result(uuid, text, uuid, text, text),
    public.device_post_location(uuid, text, double precision, double precision, double precision),
    public.device_create_pairing_code(uuid, text),
    public.controller_claim_code(text),
    public.controller_create_invite(uuid, text),
    public.controller_send_command(uuid, text, jsonb),
    public.controller_unlink(uuid, uuid),
    public.controller_rename_device(uuid, text)
    from anon, authenticated, public;

grant execute on function
    public.device_register(text),
    public.device_heartbeat(uuid, text),
    public.device_fetch_commands(uuid, text),
    public.device_report_result(uuid, text, uuid, text, text),
    public.device_post_location(uuid, text, double precision, double precision, double precision),
    public.device_create_pairing_code(uuid, text)
    to anon, authenticated;

grant execute on function
    public.controller_claim_code(text),
    public.controller_create_invite(uuid, text),
    public.controller_send_command(uuid, text, jsonb),
    public.controller_unlink(uuid, uuid),
    public.controller_rename_device(uuid, text)
    to authenticated;

-- Leitura para controladores logados (a coluna token_hash fica de fora).
grant select (id, label, last_seen, created_at) on public.devices to authenticated;
grant select on public.controllers, public.device_controllers,
    public.commands, public.locations to authenticated;
grant update (name) on public.controllers to authenticated;

alter table public.devices            enable row level security;
alter table public.controllers        enable row level security;
alter table public.device_controllers enable row level security;
alter table public.pairing_codes      enable row level security;
alter table public.pairing_attempts   enable row level security;
alter table public.commands           enable row level security;
alter table public.locations          enable row level security;

-- Remove as políticas permissivas da versão MVP, se existirem.
do $$
declare t text;
begin
    foreach t in array array['devices', 'controllers', 'device_controllers',
                             'pairing_codes', 'commands', 'locations'] loop
        execute format('drop policy if exists "mvp_anon_all" on public.%I', t);
    end loop;
end $$;

drop policy if exists devices_select on public.devices;
create policy devices_select on public.devices
    for select to authenticated using (private.is_linked(id));

drop policy if exists controllers_select on public.controllers;
create policy controllers_select on public.controllers
    for select to authenticated
    using (id = auth.uid() or private.shares_device(id));

drop policy if exists controllers_update_self on public.controllers;
create policy controllers_update_self on public.controllers
    for update to authenticated
    using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists device_controllers_select on public.device_controllers;
create policy device_controllers_select on public.device_controllers
    for select to authenticated using (private.is_linked(device_id));

drop policy if exists commands_select on public.commands;
create policy commands_select on public.commands
    for select to authenticated using (private.is_linked(device_id));

drop policy if exists locations_select on public.locations;
create policy locations_select on public.locations
    for select to authenticated using (private.is_linked(device_id));

-- pairing_codes e pairing_attempts: sem políticas = ninguém acessa pela API;
-- só as funções acima (security definer) mexem nelas.
