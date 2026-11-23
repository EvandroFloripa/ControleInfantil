-- Migração 0008: resumo de notificações de mensagens, sem conteúdo.
--
-- O aparelho envia apenas: nome do pacote do app, horário e contagem. O TEXTO das
-- notificações nunca é enviado nem gravado. O responsável vê quais apps de
-- mensagem foram usados e quando.
--
-- Escrita: só pela RPC do aparelho (com o token). Leitura: só pelo painel do
-- responsável do aparelho (mesmas regras de device_controllers).

create table if not exists public.notification_events (
    id         bigint generated always as identity primary key,
    device_id  uuid not null references public.devices(id) on delete cascade,
    package    text not null,
    count      integer not null default 1 check (count > 0),
    at         timestamptz not null default now()
);

create index if not exists notification_events_device_time_idx
    on public.notification_events (device_id, at desc);

alter table public.notification_events enable row level security;
revoke all on public.notification_events from anon, authenticated;
grant select on public.notification_events to authenticated;

drop policy if exists notification_events_select on public.notification_events;
create policy notification_events_select on public.notification_events
    for select to authenticated
    using (exists (
        select 1 from public.device_controllers dc
        where dc.device_id = notification_events.device_id
          and dc.controller_id = auth.uid()));

create or replace function public.device_post_notification_summary(
    p_device uuid, p_token text, p_package text, p_count integer, p_at timestamptz)
returns void language plpgsql volatile security definer set search_path = '' as $$
begin
    perform private.assert_device(p_device, p_token);
    if p_count is null or p_count <= 0 or p_count > 1000 then
        raise exception 'contagem inválida';
    end if;
    insert into public.notification_events (device_id, package, count, at)
    values (p_device, left(p_package, 200), p_count, coalesce(p_at, now()));
end $$;

grant execute on function public.device_post_notification_summary(uuid, text, text, integer, timestamptz)
    to anon, authenticated;

insert into public.schema_migrations (version, name)
values ('0008', 'notification_summary')
on conflict (version) do nothing;
