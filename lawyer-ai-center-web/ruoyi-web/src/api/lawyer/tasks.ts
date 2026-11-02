import { lawyerRequest } from './index';
export interface CaseCheck { id: string; taskId: string; content: string; checked: boolean; checkedAt?: string | null }
export interface CaseTask {
  id: string; caseId: string; userId: string; title: string; status: 'todo' | 'doing' | 'done';
  dueAt?: string | null; sortOrder: number; workMinutes: number; checklist: CaseCheck[];
}
export interface TaskSummary { tasks: CaseTask[]; doneTasks: number; totalTasks: number; checkedItems: number; totalItems: number; workMinutes: number }
const root = '/lawyer/case';
async function data<T>(path: string, method = 'GET', body?: unknown) { return (await lawyerRequest<{ data: T }>(root + path, method, body)).data; }
export const caseTaskApi = {
  list: (id: string) => data<TaskSummary>(`/${encodeURIComponent(id)}/tasks`),
  create: (id: string, title: string, dueAt?: string) => data<CaseTask>(`/${encodeURIComponent(id)}/tasks`, 'POST', { title, dueAt: dueAt || undefined }),
  status: (id: string, status: CaseTask['status']) => data<CaseTask>(`/task/${encodeURIComponent(id)}`, 'PATCH', { status }),
  addCheck: (id: string, content: string) => data<CaseCheck>(`/task/${encodeURIComponent(id)}/checklist`, 'POST', { content }),
  toggle: (taskId: string, itemId: string, checked: boolean) => data<CaseCheck>(`/task/${encodeURIComponent(taskId)}/checklist/${encodeURIComponent(itemId)}/toggle`, 'POST', { checked }),
  time: (id: string, minutes: number, note: string, requestId: string) => data<CaseTask>(`/task/${encodeURIComponent(id)}/time`, 'POST', { minutes, note, requestId }),
};
