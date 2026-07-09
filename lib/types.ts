export type AppRole = "developer" | "lead" | "manager";
export type LogStatus = "draft" | "submitted" | "approved";
export type ItemStatus = "todo" | "in_progress" | "done" | "blocked";
export interface Profile { id: string; email: string; full_name: string | null; avatar_url: string | null; role: AppRole; status: "active" | "pending"; }
export interface WorkType { id: string; name: string; color: string; sort_order: number; active: boolean; }
export interface Technology { id: string; name: string; color: string; active: boolean; }
export interface DailyLog { id: string; developer_id: string; log_date: string; status: LogStatus; approved_by: string | null; approved_at: string | null; }
export interface LogItem { id: string; daily_log_id: string; work_type_id: string; status: ItemStatus; description: string; hours: number | null; blocker_note: string | null; sort_order: number; }
// LogItem joined with its technologies for display/editing
export interface LogItemWithTech extends LogItem { technology_ids: string[]; }
export interface DevNote { id: string; developer_id: string; author_id: string; body: string; created_at: string; updated_at: string; }
