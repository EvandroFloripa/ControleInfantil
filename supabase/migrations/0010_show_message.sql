-- Migração 0010: mensagem do responsável na tela da criança (comando show_message).
--
-- 1. Libera o tipo show_message em commands.type.
-- 2. controller_send_command valida o texto: obrigatório, entre 1 e 120 caracteres.
--    A função é a de schema.sql com essa validação adicionada.

alter table public.commands drop constraint if exists commands_type_check;
alter table public.commands add constraint commands_type_check
    check (type in (
        'lock_screen', 'reboot', 'enable_location', 'request_location',
        'start_screen_view', 'start_checkin',
        'stop_screen_view', 'stop_checkin',
        'set_allowed_apps', 'install_app', 'uninstall_app',
        'set_time_rules', 'grant_time',
        'wake_screen', 'resume_control', 'disable_control',
        'show_message'));

create or replace function public.controller_send_command(
    p_device uuid, p_type text, p_payload jsonb default '{}'::jsonb)
returns uuid language plpgsql volatile security definer set search_path = '' as $$
declare
    v_id uuid;
    v_text text;
begin
    if not private.is_guardian(p_device) then
        raise exception 'not_guardian' using errcode = '42501';
    end if;
    if pg_column_size(coalesce(p_payload, '{}'::jsonb)) > 4096 then
        raise exception 'payload_too_large' using errcode = '22023';
    end if;
    if p_type = 'show_message' then
        v_text := btrim(coalesce(p_payload ->> 'text', ''));
        if char_length(v_text) < 1 or char_length(v_text) > 120 then
            raise exception 'invalid_message' using errcode = '22023';
        end if;
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

insert into public.schema_migrations (version, name)
values ('0010', 'show_message')
on conflict (version) do nothing;
