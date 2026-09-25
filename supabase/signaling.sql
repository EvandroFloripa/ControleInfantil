-- ============================================================================
--  Controle Infantil — sinalização WebRTC
-- ============================================================================
--  Rode DEPOIS de supabase/schema.sql. Cria o canal por onde o aparelho e o
--  controlador trocam oferta/resposta SDP e candidatos ICE para abrir a conexão
--  de vídeo/áudio (check-in) ou de tela.
--
--  Cada chamada tem um `session_id` (texto, gerado pelo controlador) que vai no
--  payload do comando start_checkin / start_screen_view. Os dois lados escrevem e
--  leem os sinais dessa sessão:
--    - o APARELHO, pelas funções device_* (autenticado pelo token);
--    - o CONTROLADOR, direto na tabela, protegido por RLS (precisa ser guardião).
-- ============================================================================

-- Garante que os comandos de parar sejam aceitos, mesmo em bancos que já rodaram
-- uma versão anterior do schema (o create table if not exists não altera a checagem).
alter table public.commands drop constraint if exists commands_type_check;
alter table public.commands add constraint commands_type_check
    check (type in (
        'lock_screen', 'reboot', 'enable_location', 'request_location',
        'start_screen_view', 'start_checkin',
        'stop_screen_view', 'stop_checkin',
        'set_allowed_apps', 'install_app',
        'set_time_rules'));

create table if not exists public.signals (
    id          bigint generated always as identity primary key,
    session_id  text not null,
    device_id   uuid not null references public.devices(id) on delete cascade,
    from_role   text not null check (from_role in ('device', 'controller')),
    kind        text not null check (kind in ('offer', 'answer', 'candidate', 'bye')),
    payload     jsonb not null,
    created_at  timestamptz not null default now()
);
create index if not exists signals_session_idx
    on public.signals (session_id, from_role, id);

-- Um SDP de vídeo+áudio tem poucos KB; 32 KB sobra e impede encher a tabela com
-- payloads enormes.
alter table public.signals drop constraint if exists signals_payload_size;
alter table public.signals add constraint signals_payload_size
    check (pg_column_size(payload) <= 32768);

-- Antes de gravar: a data é sempre a do servidor (senão um controlador poderia gravar
-- datas no futuro, que a limpeza de 1 hora nunca apagaria) e há um teto de sinais
-- por aparelho e por lado, contra quem tente inundar a tabela com um token válido.
create or replace function private.guard_signal()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    new.created_at := now();
    if (select count(*) from public.signals s
        where s.device_id = new.device_id
          and s.from_role = new.from_role
          and s.created_at > now() - interval '1 minute') >= 300 then
        raise exception 'rate_limited' using errcode = '54000';
    end if;
    return new;
end $$;

drop trigger if exists guard_signal_trg on public.signals;
create trigger guard_signal_trg
    before insert on public.signals
    for each row execute function private.guard_signal();

create index if not exists signals_device_recent_idx
    on public.signals (device_id, from_role, created_at);

-- Limpa sinais antigos ao inserir um novo (evita a tabela crescer sem fim).
create or replace function private.prune_signals()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    delete from public.signals where created_at < now() - interval '1 hour';
    return new;
end $$;

drop trigger if exists prune_signals_trg on public.signals;
create trigger prune_signals_trg
    after insert on public.signals
    for each row execute function private.prune_signals();

-- ---------------------------------------------------------------------------
--  Lado do APARELHO (funções device_*, autenticadas pelo token)
-- ---------------------------------------------------------------------------

create or replace function public.device_post_signal(
    p_device uuid, p_token text, p_session text, p_kind text, p_payload jsonb)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    if p_kind not in ('offer', 'answer', 'candidate', 'bye') then
        raise exception 'invalid_kind' using errcode = '22023';
    end if;
    insert into public.signals (session_id, device_id, from_role, kind, payload)
    values (left(p_session, 64), p_device, 'device', p_kind, p_payload);
end $$;

-- Lê os sinais que o CONTROLADOR mandou nesta sessão depois de um dado id.
-- O app guarda o maior id recebido e passa de volta em `p_after` (long polling).
create or replace function public.device_fetch_signals(
    p_device uuid, p_token text, p_session text, p_after bigint default 0)
returns table (id bigint, kind text, payload jsonb)
language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    return query
        select s.id, s.kind, s.payload
        from public.signals s
        where s.device_id = p_device
          and s.session_id = left(p_session, 64)
          and s.from_role = 'controller'
          and s.id > p_after
        order by s.id;
end $$;

revoke execute on function
    public.device_post_signal(uuid, text, text, text, jsonb),
    public.device_fetch_signals(uuid, text, text, bigint)
    from anon, authenticated, public;
grant execute on function
    public.device_post_signal(uuid, text, text, text, jsonb),
    public.device_fetch_signals(uuid, text, text, bigint)
    to anon, authenticated;

-- ---------------------------------------------------------------------------
--  Lado do CONTROLADOR (RLS: precisa ser guardião do aparelho)
-- ---------------------------------------------------------------------------

alter table public.signals enable row level security;

grant select, insert on public.signals to authenticated;

drop policy if exists signals_select on public.signals;
create policy signals_select on public.signals
    for select to authenticated using (private.is_guardian(device_id));

drop policy if exists signals_insert on public.signals;
create policy signals_insert on public.signals
    for insert to authenticated
    with check (private.is_guardian(device_id) and from_role = 'controller');
