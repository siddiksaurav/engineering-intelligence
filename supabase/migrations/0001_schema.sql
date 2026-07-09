create type app_role as enum ('developer','lead','manager');
create type log_status as enum ('draft','submitted','approved');
create type profile_status as enum ('active','pending');
create type item_status as enum ('todo','in_progress','done','blocked');

create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text unique not null,
  full_name text,
  avatar_url text,
  role app_role not null default 'developer',
  status profile_status not null default 'active',
  created_at timestamptz not null default now()
);

create table teams (
  id uuid primary key default gen_random_uuid(),
  name text unique not null,
  created_at timestamptz not null default now()
);

create table team_members (
  team_id uuid references teams(id) on delete cascade,
  developer_id uuid references profiles(id) on delete cascade,
  primary key (team_id, developer_id)
);

create table team_leads (
  team_id uuid references teams(id) on delete cascade,
  lead_id uuid references profiles(id) on delete cascade,
  primary key (team_id, lead_id)
);

create table work_types (
  id uuid primary key default gen_random_uuid(),
  name text unique not null,
  color text not null default '#888888',
  sort_order int not null default 0,
  active boolean not null default true
);

-- shared, extensible technology/skill list (Spring Boot, Apache Kafka, ...)
create table technologies (
  id uuid primary key default gen_random_uuid(),
  name text unique not null,
  color text not null default '#64748b',
  active boolean not null default true,
  created_by uuid references profiles(id),
  created_at timestamptz not null default now()
);

create table daily_logs (
  id uuid primary key default gen_random_uuid(),
  developer_id uuid not null references profiles(id) on delete cascade,
  log_date date not null,
  status log_status not null default 'draft',
  approved_by uuid references profiles(id),
  approved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (developer_id, log_date)
);

create table log_items (
  id uuid primary key default gen_random_uuid(),
  daily_log_id uuid not null references daily_logs(id) on delete cascade,
  work_type_id uuid not null references work_types(id),   -- category
  status item_status not null default 'todo',
  description text not null,
  hours numeric(4,1),                                     -- optional time spent
  blocker_note text,                                      -- optional, shown when status='blocked'
  sort_order int not null default 0
);

-- a task may use many technologies
create table log_item_technologies (
  log_item_id uuid references log_items(id) on delete cascade,
  technology_id uuid references technologies(id) on delete cascade,
  primary key (log_item_id, technology_id)
);

create table dev_notes (
  id uuid primary key default gen_random_uuid(),
  developer_id uuid not null references profiles(id) on delete cascade,
  author_id uuid not null references profiles(id),
  body text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table allowed_emails (
  email text primary key,
  role app_role not null default 'developer',
  team_id uuid references teams(id),
  consumed boolean not null default false
);

create index on daily_logs (developer_id, log_date);
create index on daily_logs (status);
create index on log_items (daily_log_id);
create index on log_items (status);
create index on log_item_technologies (technology_id);
create index on team_members (developer_id);
create index on team_leads (lead_id);
create index on dev_notes (developer_id);

-- Table privileges for the Supabase API roles. Supabase's authorization model
-- is table-level GRANT (checked BEFORE row security) + Row Level Security for
-- per-row control, so the API roles need broad table privileges here; Task 2's
-- RLS is what actually restricts which rows each role sees. The local stack
-- does not auto-grant these to migration-created tables (verified: without
-- this, anon/authenticated/service_role have no SELECT/DML, so every RLS policy
-- denies and the service-role admin client cannot query). Granting explicitly
-- also makes local and cloud behave identically.
grant usage on schema public to anon, authenticated, service_role;
grant all on all tables in schema public to anon, authenticated, service_role;
grant all on all sequences in schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;
