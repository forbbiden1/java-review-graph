create table if not exists project (
  id text primary key,
  name text not null,
  root_path text not null,
  build_tool text not null,
  created_at text not null,
  updated_at text not null
);

create unique index if not exists idx_project_root_path
  on project (root_path);

create table if not exists snapshot (
  id text primary key,
  project_id text not null,
  base_snapshot_id text,
  trigger_type text not null,
  git_commit text,
  git_commit_message text,
  display_name text not null,
  status text not null,
  requested_mode text not null default 'full',
  effective_mode text not null default 'full',
  change_source text,
  includes_workspace_changes integer not null default 0,
  diagnostics_note text,
  fallback_reason text,
  changed_files_json text not null default '[]',
  renamed_paths_json text not null default '[]',
  rebuild_paths_json text not null default '[]',
  removed_paths_json text not null default '[]',
  created_at text not null
);

create index if not exists idx_snapshot_project_id
  on snapshot (project_id);

create index if not exists idx_snapshot_project_created_at
  on snapshot (project_id, created_at desc);

create index if not exists idx_snapshot_project_base_snapshot
  on snapshot (project_id, base_snapshot_id);

create table if not exists source_file (
  id text primary key,
  project_id text not null,
  snapshot_id text not null,
  path text not null,
  module_name text,
  package_name text,
  content_hash text not null,
  scope text not null,
  updated_at text not null
);

create index if not exists idx_source_file_project_snapshot
  on source_file (project_id, snapshot_id);

create unique index if not exists idx_source_file_project_snapshot_path
  on source_file (project_id, snapshot_id, path);

create index if not exists idx_source_file_path
  on source_file (path);

create table if not exists symbol (
  id text primary key,
  project_id text not null,
  snapshot_id text not null,
  file_id text not null,
  symbol_key text not null,
  symbol_type text not null,
  parent_symbol_key text,
  name text not null,
  display_name text not null,
  package_name text,
  qualified_name text not null,
  signature text,
  kind text not null,
  visibility text,
  is_abstract integer not null default 0,
  is_static integer not null default 0,
  start_line integer not null,
  end_line integer not null,
  api_hash text not null,
  impl_hash text,
  change_status text not null,
  metadata_json text
);

create unique index if not exists idx_symbol_snapshot_key
  on symbol (snapshot_id, symbol_key);

create unique index if not exists idx_symbol_project_snapshot_key
  on symbol (project_id, snapshot_id, symbol_key);

create index if not exists idx_symbol_project_snapshot_type
  on symbol (project_id, snapshot_id, symbol_type);

create index if not exists idx_symbol_project_snapshot_parent
  on symbol (project_id, snapshot_id, parent_symbol_key);

create table if not exists relation (
  id text primary key,
  project_id text not null,
  snapshot_id text not null,
  source_symbol_key text not null,
  target_symbol_key text not null,
  relation_type text not null,
  confidence text not null,
  source_file_id text,
  source_line integer,
  metadata_json text
);

create index if not exists idx_relation_snapshot_source
  on relation (snapshot_id, source_symbol_key);

create index if not exists idx_relation_snapshot_target
  on relation (snapshot_id, target_symbol_key);

create index if not exists idx_relation_project_snapshot_type
  on relation (project_id, snapshot_id, relation_type);

create index if not exists idx_relation_project_snapshot_type_source_target
  on relation (project_id, snapshot_id, relation_type, source_symbol_key, target_symbol_key);

create table if not exists symbol_change (
  id text primary key,
  project_id text not null,
  snapshot_id text not null,
  symbol_key text not null,
  before_symbol_id text,
  after_symbol_id text,
  change_type text not null,
  reason text
);

create index if not exists idx_symbol_change_project_snapshot
  on symbol_change (project_id, snapshot_id);
