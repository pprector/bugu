import { request } from '../request';

// ---- 运营概览(E9-S1) ----

export interface AdminStats {
  userTotal: number;
  userElder: number;
  bondActive: number;
  reminderPending: number;
  fireToday: number;
  aiGenToday: number;
}

export function fetchAdminStats() {
  return request<AdminStats>({ url: '/api/v1/admin/stats' });
}

// ---- 后台账号 ----

export interface AdminAccount {
  id: number;
  username: string;
  nickname: string;
  role: string;
  enabled: boolean;
  createdAt: string;
}

export function fetchAdminAccounts() {
  return request<AdminAccount[]>({ url: '/api/v1/admin/admin-users' });
}

export function fetchAdminCreateAccount(data: { username: string; password: string; nickname: string; role: string }) {
  return request<null>({ url: '/api/v1/admin/admin-users', method: 'post', data });
}

export function fetchAdminSetAccountEnabled(id: number, enabled: boolean) {
  return request<null>({
    url: `/api/v1/admin/admin-users/${id}/enabled`,
    method: 'patch',
    data: { enabled }
  });
}

// ---- 运营查询(E9-S2) ----

export interface AdminAppUser {
  id: number;
  nickname: string;
  phone: string | null;
  accountType: string;
  elderMode: boolean;
}

export function fetchAdminUsers(query?: string) {
  return request<AdminAppUser[]>({ url: '/api/v1/admin/users', params: { query } });
}

export function fetchSetElderMode(userId: number, enabled: boolean) {
  return request<AdminAppUser>({
    url: `/api/v1/admin/users/${userId}/elder-mode`,
    method: 'patch',
    data: { enabled }
  });
}

export interface AdminBond {
  id: number;
  status: string;
  requesterId: number;
  requesterName: string;
  receiverId: number | null;
  receiverName: string | null;
  createdAt: string;
}

export function fetchAdminBonds(query?: string) {
  return request<AdminBond[]>({ url: '/api/v1/admin/bonds', params: { query } });
}

export function fetchAdminUnbind(bondId: number) {
  return request<{ unbound: boolean }>({ url: `/api/v1/admin/bonds/${bondId}/unbind`, method: 'post' });
}

export interface AdminReminder {
  id: number;
  title: string;
  kind: string;
  status: string;
  fireAt: string;
  creatorId: number;
  creatorName: string;
  receiverId: number;
  receiverName: string;
  createdAt: string;
}

export function fetchAdminReminders(params: { status?: string; kind?: string; query?: string }) {
  return request<AdminReminder[]>({ url: '/api/v1/admin/reminders', params });
}

export interface AdminEscalationAction {
  stage: string;
  action: string;
  createdAt: string;
}

export interface AdminEscalation {
  fireId: number;
  fireAt: string;
  state: string;
  actions: AdminEscalationAction[];
}

export function fetchAdminEscalations(reminderId: number) {
  return request<AdminEscalation[]>({ url: `/api/v1/admin/reminders/${reminderId}/escalations` });
}

export interface AdminPushStatus {
  channel: string;
  description: string;
}

export function fetchAdminPushStatus() {
  return request<AdminPushStatus>({ url: '/api/v1/admin/push/status' });
}

// ---- 用户反馈 ----

export type AdminFeedbackStatus = 'OPEN' | 'PROCESSING' | 'RESOLVED';

export interface AdminFeedback {
  id: number;
  category: string;
  content: string;
  appVersion: string | null;
  status: AdminFeedbackStatus;
  reply: string | null;
  createdAt: string;
  updatedAt: string;
  userId: number;
}

export interface AdminFeedbackPage {
  items: AdminFeedback[];
  total: number;
  page: number;
  size: number;
}

export function fetchAdminFeedback(params: { page: number; size: number; status?: AdminFeedbackStatus }) {
  return request<AdminFeedbackPage>({ url: '/api/v1/admin/feedback', params });
}

export function fetchAdminUpdateFeedback(id: number, data: { status: AdminFeedbackStatus; reply: string | null }) {
  return request<AdminFeedback>({ url: `/api/v1/admin/feedback/${id}`, method: 'patch', data });
}

// ---- AI 管理(E9-S3) ----

export interface AdminTemplate {
  id: number;
  tone: string;
  scene: string;
  content: string;
  enabled: boolean;
  createdAt: string;
}

export function fetchAdminTemplates(params?: { tone?: string; scene?: string }) {
  return request<AdminTemplate[]>({ url: '/api/v1/admin/ai/copy-templates', params });
}

export function fetchAdminCreateTemplate(data: { tone: string; scene: string; content: string }) {
  return request<AdminTemplate>({ url: '/api/v1/admin/ai/copy-templates', method: 'post', data });
}

export function fetchAdminUpdateTemplate(id: number, data: { content?: string; enabled?: boolean }) {
  return request<AdminTemplate>({ url: `/api/v1/admin/ai/copy-templates/${id}`, method: 'put', data });
}

export interface AdminGenLog {
  id: number;
  scene: string;
  tone: string | null;
  degraded: boolean;
  latencyMs: number | null;
  createdAt: string;
}

export interface AdminGenLogSummary {
  scene: string;
  total: number;
  degraded: number;
  degradeRate: number;
}

export function fetchAdminGenLogs(params: { scene?: string; page?: number; size?: number }) {
  return request<{ summary: AdminGenLogSummary[]; logs: AdminGenLog[] }>({
    url: '/api/v1/admin/ai/gen-logs',
    params
  });
}

export function fetchAdminAiStatus() {
  return request<{ llmProvider: string; gatewayRegistered: boolean; note: string }>({
    url: '/api/v1/admin/ai/status'
  });
}

// ---- 审计(E9) ----

export interface AdminAuditLog {
  id: number;
  adminUserId: number;
  action: string;
  targetType: string | null;
  targetId: string | null;
  detail: string | null;
  createdAt: string;
}

export function fetchAdminAuditLogs(adminUserId?: number) {
  // P0: 按操作人过滤;全量经 /admin/audit-logs 端点(S2 查询服务),此处先按 repo 现有查询
  return request<AdminAuditLog[]>({ url: '/api/v1/admin/audit-logs', params: { adminUserId } });
}
