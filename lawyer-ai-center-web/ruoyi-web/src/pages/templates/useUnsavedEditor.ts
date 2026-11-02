import type { Ref } from 'vue';
import { useEventListener } from '@vueuse/core';
import { ElMessageBox } from 'element-plus';
import { computed, onScopeDispose } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';

export function useUnsavedEditor(open: Ref<boolean>, busy: Ref<boolean>, snapshot: () => string) {
  let baseline = snapshot();
  let alive = true;
  let confirming = false;
  const trigger
    = document.activeElement instanceof HTMLElement ? document.activeElement : undefined;
  const dirty = computed(() => open.value && snapshot() !== baseline);
  async function canLeave() {
    if (busy.value || confirming)
      return false;
    if (!dirty.value)
      return true;
    confirming = true;
    try {
      await ElMessageBox.confirm(
        '本次未保存的修改将丢失，已保存的数据不受影响。',
        '放弃未保存的修改？',
        { confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning' },
      );
      return alive;
    }
    catch {
      return false;
    }
    finally {
      confirming = false;
    }
  }
  async function close() {
    if (await canLeave())
      open.value = false;
  }
  function saved() {
    baseline = snapshot();
    open.value = false;
  }
  // A shared route component can update from the library to categories.
  onBeforeRouteLeave(canLeave);
  onBeforeRouteUpdate(async () => {
    if (!(await canLeave()))
      return false;
    open.value = false;
  });
  useEventListener(window, 'beforeunload', (event) => {
    if (dirty.value || busy.value) {
      event.preventDefault();
      event.returnValue = '';
    }
  });
  onScopeDispose(() => {
    alive = false;
    if (confirming)
      ElMessageBox.close();
    requestAnimationFrame(() => {
      if (trigger?.isConnected)
        trigger.focus();
    });
  });
  return { dirty, close, saved };
}
