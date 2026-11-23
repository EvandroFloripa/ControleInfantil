-- Aviso em tempo real de comando novo para o aparelho.
--
-- A cada comando inserido, envia um sinal "wake" no canal público device:<id>.
-- O sinal leva SOMENTE o identificador do aparelho: nenhum conteúdo de comando.
-- O aparelho, ao receber o sinal, busca o comando pela RPC autenticada com o seu
-- token (device_fetch_pending_commands), como já faz na consulta periódica.
--
-- Canal público: qualquer cliente com a chave anon que conheça o UUID do aparelho
-- pode receber o sinal. O risco é apenas um "há comando" a mais, sem o comando.
-- Se isso for inaceitável, troque o último argumento de realtime.send para true e
-- crie políticas de realtime.messages para o papel de quem recebe.

create or replace function public.notify_device_wake()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform realtime.send(
        jsonb_build_object('device_id', new.device_id),
        'wake',
        'device:' || new.device_id::text,
        false);
    return new;
end $$;

drop trigger if exists commands_wake_device on public.commands;
create trigger commands_wake_device
    after insert on public.commands
    for each row execute function public.notify_device_wake();

insert into public.schema_migrations (version, name)
values ('0006', 'realtime_wake')
on conflict (version) do nothing;
