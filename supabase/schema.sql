-- ============================================================================
--  Controle Infantil — esquema do backend (Supabase / PostgreSQL)
-- ============================================================================
--  Rode este arquivo no SQL Editor do seu projeto Supabase.
--
--  Modelo:
--    - Um APARELHO monitorado (o celular da criança) -> tabela `devices`.
--    - Um ou mais CONTROLADORES (responsáveis) -> tabela `controllers`.
--    - O vínculo N:N entre eles -> tabela `device_controllers`.
--    - Pareamento por código de uso único -> tabela `pairing_codes`.
--    - Fila de comandos -> `commands`. Histórico de posição -> `locations`.
-- ============================================================================

create extension if not exists "pgcrypto";

-- Aparelho monitorado -------------------------------------------------------
create table if not exists public.devices (
    id          uuid primary key default gen_random_uuid(),
    label       text not null default 'Celular da criança',
    last_seen   timestamptz,
    created_at  timestamptz not null default now()
);

-- Controlador (responsável) -------------------------------------------------
create table if not exists public.controllers (
    id          uuid primary key default gen_random_uuid(),
    -- Se usar Supabase Auth, aponte para auth.users(id). Para o MVP, e-mail basta.
    email       text unique,
    name        text,
    created_at  timestamptz not null default now()
);

-- Vínculo N:N: quais controladores comandam quais aparelhos -----------------
create table if not exists public.device_controllers (
    device_id       uuid not null references public.devices(id) on delete cascade,
    controller_id   uuid not null references public.controllers(id) on delete cascade,
    role            text not null default 'guardian', -- guardian | viewer
    created_at      timestamptz not null default now(),
    primary key (device_id, controller_id)
);

-- Códigos de pareamento (gerados no app da criança, usados pelo controlador) -
create table if not exists public.pairing_codes (
    code        text primary key,                 -- ex.: "428193"
    device_id   uuid not null references public.devices(id) on delete cascade,
    expires_at  timestamptz not null,
    used        boolean not null default false,
    created_at  timestamptz not null default now()
);

-- Fila de comandos ----------------------------------------------------------
create table if not exists public.commands (
    id              uuid primary key default gen_random_uuid(),
    device_id       uuid not null references public.devices(id) on delete cascade,
    controller_id   uuid references public.controllers(id) on delete set null,
    type            text not null,     -- lock_screen | reboot | enable_location | request_location | ...
    payload         jsonb not null default '{}'::jsonb,
    status          text not null default 'pending', -- pending | done | error
    result_detail   text,
    created_at      timestamptz not null default now(),
    executed_at     timestamptz
);
create index if not exists commands_device_pending_idx
    on public.commands (device_id, status, created_at);

-- Histórico de localização --------------------------------------------------
create table if not exists public.locations (
    id          uuid primary key default gen_random_uuid(),
    device_id   uuid not null references public.devices(id) on delete cascade,
    lat         double precision not null,
    lon         double precision not null,
    accuracy    double precision,
    created_at  timestamptz not null default now()
);
create index if not exists locations_device_time_idx
    on public.locations (device_id, created_at desc);

-- ============================================================================
--  SEGURANÇA (RLS)
-- ============================================================================
--  IMPORTANTE: as políticas abaixo são PERMISSIVAS para facilitar o primeiro
--  teste com a chave `anon`. ANTES de usar de verdade, troque para autenticação
--  real (Supabase Auth para os controladores + um token por aparelho) e
--  restrinja cada tabela a quem é dono do vínculo em `device_controllers`.
--  Veja docs/PROVISIONAMENTO.md e o README.
-- ============================================================================

alter table public.devices             enable row level security;
alter table public.controllers         enable row level security;
alter table public.device_controllers  enable row level security;
alter table public.pairing_codes       enable row level security;
alter table public.commands            enable row level security;
alter table public.locations           enable row level security;

-- MVP: permite leitura/escrita com a chave anon. SUBSTITUA em produção.
do $$
declare t text;
begin
  foreach t in array array[
      'devices','controllers','device_controllers',
      'pairing_codes','commands','locations'] loop
    execute format(
      'create policy "mvp_anon_all" on public.%I for all to anon using (true) with check (true);',
      t);
  end loop;
end $$;
