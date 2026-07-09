-- role helpers (security definer to read profiles without recursion)
create or replace function app_role_of() returns app_role language sql stable security definer set search_path = public as $$
  select role from profiles where id = auth.uid()
$$;
create or replace function app_is_manager() returns boolean language sql stable security definer set search_path = public as $$
  select coalesce((select role = 'manager' from profiles where id = auth.uid()), false)
$$;
create or replace function app_is_lead_of(dev uuid) returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from team_leads tl
    join team_members tm on tm.team_id = tl.team_id
    where tl.lead_id = auth.uid() and tm.developer_id = dev
  )
$$;

-- provisioning trigger: create profile from allowlist on new auth user
create or replace function handle_new_user() returns trigger language plpgsql security definer set search_path = public as $$
declare ae allowed_emails%rowtype;
begin
  select * into ae from allowed_emails where email = new.email;
  if ae.email is null then
    insert into profiles (id, email, role, status) values (new.id, new.email, 'developer', 'pending');
    return new;
  end if;
  insert into profiles (id, email, full_name, avatar_url, role, status)
    values (new.id, new.email, new.raw_user_meta_data->>'full_name', new.raw_user_meta_data->>'avatar_url', ae.role, 'active');
  if ae.team_id is not null then
    insert into team_members (team_id, developer_id) values (ae.team_id, new.id) on conflict do nothing;
  end if;
  update allowed_emails set consumed = true where email = ae.email;
  return new;
end $$;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function handle_new_user();

-- keep updated_at fresh + reopen locked days on edit
create or replace function touch_updated_at() returns trigger language plpgsql set search_path = public as $$
begin new.updated_at = now(); return new; end $$;
create trigger daily_logs_touch before update on daily_logs for each row execute function touch_updated_at();

alter table profiles enable row level security;
alter table teams enable row level security;
alter table team_members enable row level security;
alter table team_leads enable row level security;
alter table work_types enable row level security;
alter table technologies enable row level security;
alter table log_item_technologies enable row level security;
alter table daily_logs enable row level security;
alter table log_items enable row level security;
alter table dev_notes enable row level security;
alter table allowed_emails enable row level security;

-- profiles
create policy profiles_self_read on profiles for select using (id = auth.uid());
create policy profiles_lead_read on profiles for select using (app_is_lead_of(id));
create policy profiles_mgr_all on profiles for all using (app_is_manager()) with check (app_is_manager());

-- reference data readable by all authenticated
create policy wt_read on work_types for select using (auth.role() = 'authenticated');
create policy wt_mgr on work_types for all using (app_is_manager()) with check (app_is_manager());

-- technologies: everyone reads; any authenticated user may ADD a new one; only manager renames/deactivates/deletes
create policy tech_read on technologies for select using (auth.role() = 'authenticated');
create policy tech_insert on technologies for insert with check (auth.role() = 'authenticated' and created_by = auth.uid());
create policy tech_mgr on technologies for update using (app_is_manager()) with check (app_is_manager());
create policy tech_mgr_del on technologies for delete using (app_is_manager());
create policy teams_read on teams for select using (auth.role() = 'authenticated');
create policy teams_mgr on teams for all using (app_is_manager()) with check (app_is_manager());
create policy tm_read on team_members for select using (auth.role() = 'authenticated');
create policy tm_mgr on team_members for all using (app_is_manager()) with check (app_is_manager());
create policy tl_read on team_leads for select using (auth.role() = 'authenticated');
create policy tl_mgr on team_leads for all using (app_is_manager()) with check (app_is_manager());

-- daily_logs
create policy dl_dev_read on daily_logs for select using (developer_id = auth.uid());
create policy dl_dev_write on daily_logs for insert with check (developer_id = auth.uid());
create policy dl_dev_update on daily_logs for update
  using (developer_id = auth.uid() and status <> 'approved')
  with check (developer_id = auth.uid());
create policy dl_dev_delete on daily_logs for delete using (developer_id = auth.uid() and status = 'draft');
create policy dl_lead_read on daily_logs for select using (app_is_lead_of(developer_id));
create policy dl_lead_update on daily_logs for update using (app_is_lead_of(developer_id)) with check (app_is_lead_of(developer_id));
create policy dl_mgr_all on daily_logs for all using (app_is_manager()) with check (app_is_manager());

-- log_items follow their parent day's access
create policy li_dev_all on log_items for all
  using (exists (select 1 from daily_logs d where d.id = daily_log_id and d.developer_id = auth.uid() and d.status <> 'approved'))
  with check (exists (select 1 from daily_logs d where d.id = daily_log_id and d.developer_id = auth.uid() and d.status <> 'approved'));
create policy li_dev_read on log_items for select
  using (exists (select 1 from daily_logs d where d.id = daily_log_id and d.developer_id = auth.uid()));
create policy li_lead_read on log_items for select
  using (exists (select 1 from daily_logs d where d.id = daily_log_id and app_is_lead_of(d.developer_id)));
create policy li_mgr_all on log_items for all using (app_is_manager()) with check (app_is_manager());

-- log_item_technologies inherit the parent task's access
create policy lit_dev_all on log_item_technologies for all
  using (exists (select 1 from log_items i join daily_logs d on d.id = i.daily_log_id
                 where i.id = log_item_id and d.developer_id = auth.uid() and d.status <> 'approved'))
  with check (exists (select 1 from log_items i join daily_logs d on d.id = i.daily_log_id
                 where i.id = log_item_id and d.developer_id = auth.uid() and d.status <> 'approved'));
create policy lit_dev_read on log_item_technologies for select
  using (exists (select 1 from log_items i join daily_logs d on d.id = i.daily_log_id
                 where i.id = log_item_id and d.developer_id = auth.uid()));
create policy lit_lead_read on log_item_technologies for select
  using (exists (select 1 from log_items i join daily_logs d on d.id = i.daily_log_id
                 where i.id = log_item_id and app_is_lead_of(d.developer_id)));
create policy lit_mgr_all on log_item_technologies for all using (app_is_manager()) with check (app_is_manager());

-- dev_notes: lead-of or manager only; NEVER the developer themselves
create policy notes_access on dev_notes for all
  using (app_is_manager() or app_is_lead_of(developer_id))
  with check ((app_is_manager() or app_is_lead_of(developer_id)) and author_id = auth.uid());

-- allowed_emails: manager only
create policy ae_mgr on allowed_emails for all using (app_is_manager()) with check (app_is_manager());
