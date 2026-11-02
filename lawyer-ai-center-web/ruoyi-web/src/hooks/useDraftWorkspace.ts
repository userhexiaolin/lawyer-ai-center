import type { InjectionKey } from 'vue';
import type { RouteLocationRaw } from 'vue-router';
import { useEventListener } from '@vueuse/core';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getCurrentInstance, inject, onScopeDispose, provide } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRouter } from 'vue-router';

interface DraftActivity { dirty: () => boolean; pending: () => boolean }

/** One decision for all inputs in a drafting workspace; nothing is persisted in the browser. */
export function createDraftCoordinator(confirm: () => Promise<unknown>, blocked: () => void = () => {}) {
  const activities = new Set<DraftActivity>();
  const internalTargets = new Map<string, number>();
  let alive = true;
  let confirming = false;
  const hasUnsaved = () => [...activities].some(item => item.dirty());
  const hasPending = () => [...activities].some(item => item.pending());
  function register(activity: DraftActivity) {
    if (alive)
      activities.add(activity);
    return () => activities.delete(activity);
  }
  async function confirmDiscard() {
    if (!alive || confirming)
      return false;
    if (hasPending()) {
      blocked();
      return false;
    }
    if (!hasUnsaved())
      return true;
    confirming = true;
    try {
      await confirm();
      return alive && !hasPending();
    }
    catch {
      return false;
    }
    finally {
      confirming = false;
    }
  }
  async function runInternal<T>(target: string, action: () => Promise<T>) {
    if (!alive)
      throw new Error('起草工作区已关闭');
    internalTargets.set(target, (internalTargets.get(target) || 0) + 1);
    try {
      return await action();
    }
    finally {
      const count = (internalTargets.get(target) || 1) - 1;
      if (count)
        internalTargets.set(target, count);
      else
        internalTargets.delete(target);
    }
  }
  const canNavigate = (target: string) => alive && internalTargets.has(target) ? Promise.resolve(true) : confirmDiscard();
  function dispose() {
    alive = false;
    activities.clear();
    internalTargets.clear();
    return confirming;
  }
  return { register, hasUnsaved, hasPending, confirmDiscard, runInternal, canNavigate, dispose };
}

interface DraftWorkspace {
  register: (activity: DraftActivity) => () => unknown;
  hasUnsaved: () => boolean;
  hasPending: () => boolean;
  confirmDiscard: () => Promise<boolean>;
  runInternal: <T>(target: RouteLocationRaw, action: () => Promise<T>) => Promise<T>;
}
const key: InjectionKey<DraftWorkspace> = Symbol('draft-workspace');
const fallback: DraftWorkspace = {
  register: () => () => {},
  hasUnsaved: () => false,
  hasPending: () => false,
  confirmDiscard: async () => true,
  runInternal: (_target, action) => action(),
};
export function useDraftNavigation() {
  return getCurrentInstance() ? inject(key, fallback) : fallback;
}
export function useDraftActivity(dirty: () => boolean, pending: () => boolean = () => false) {
  const workspace = useDraftNavigation();
  const unregister = workspace.register({ dirty, pending });
  onScopeDispose(unregister);
  return workspace;
}
export function provideDraftWorkspace() {
  const router = useRouter();
  const state = createDraftCoordinator(() => ElMessageBox.confirm(
    '本页还有未保存的起草要素、模板填写内容或正文修改。离开或切换后，这些内容可能丢失；已保存的草稿不受影响。',
    '保留未保存的起草内容？',
    { confirmButtonText: '放弃并继续', cancelButtonText: '继续编辑', type: 'warning' },
  ), () => { ElMessage.warning('当前内容正在处理或保存，请完成或停止当前任务后再切换。'); });
  const workspace: DraftWorkspace = {
    ...state,
    runInternal: (target, action) => state.runInternal(router.resolve(target).fullPath, action),
  };
  provide(key, workspace);
  onBeforeRouteLeave(to => state.canNavigate(to.fullPath));
  onBeforeRouteUpdate(to => state.canNavigate(to.fullPath));
  useEventListener(window, 'beforeunload', (event) => {
    if (state.hasUnsaved() || state.hasPending()) {
      event.preventDefault();
      event.returnValue = '';
    }
  });
  onScopeDispose(() => {
    if (state.dispose())
      ElMessageBox.close();
  });
  return workspace;
}
