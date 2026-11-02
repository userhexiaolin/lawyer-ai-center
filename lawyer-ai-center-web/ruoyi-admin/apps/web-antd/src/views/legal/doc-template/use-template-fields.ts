import type { Ref } from 'vue';

import type { DocTemplate, TemplateField } from '#/api/legal/doc-template';

import { computed, ref, watch } from 'vue';

type Blocks = NonNullable<DocTemplate['blocks']>;
interface BlankRange {
  blockId: string;
  end: number;
  key: string;
  start: number;
}

export function useTemplateFields(
  fields: Ref<TemplateField[]>,
  blocks: () => Blocks,
  disabled: () => boolean,
) {
  const blockId = ref('');
  const start = ref(0);
  const end = ref(0);
  const error = ref('');
  const status = ref('');
  const additions = ref<string[]>([]);
  const selected = computed(() => blocks().find((b) => b.id === blockId.value));
  function findAvailable(configured: TemplateField[]) {
    const result: BlankRange[] = [];
    for (const block of blocks()) {
      const occupied = configured
        .filter((f) => f.blockId === block.id)
        .toSorted((a, b) => a.start - b.start);
      const push = (from: number, to: number) => {
        result.push({
          key: `${block.id}:${from}:${to}`,
          blockId: block.id,
          start: from,
          end: to,
        });
      };
      for (const match of block.text.matchAll(/[\s_＿]+/gu)) {
        let from = match.index;
        const to = from + match[0].length;
        for (const field of occupied) {
          if (field.end <= from || field.start >= to) continue;
          if (field.start > from) push(from, field.start);
          from = Math.max(from, field.end);
        }
        if (from < to) push(from, to);
      }
      if (
        block.text === '' &&
        !occupied.some((f) => f.end === block.text.length)
      ) {
        push(block.text.length, block.text.length);
      }
    }
    return result;
  }
  const available = computed(() => findAvailable(fields.value));
  const rangeKey = computed({
    get: () =>
      available.value.find(
        (r) =>
          r.blockId === blockId.value &&
          r.start === start.value &&
          r.end === end.value,
      )?.key,
    set: (key: string | undefined) => {
      const range = available.value.find((r) => r.key === key);
      if (range) choose(range);
    },
  });
  const addedCount = computed(
    () =>
      additions.value.filter((id) => fields.value.some((f) => f.id === id))
        .length,
  );
  function choose(range: BlankRange) {
    blockId.value = range.blockId;
    start.value = range.start;
    end.value = range.end;
  }
  watch(
    blockId,
    () => {
      const next = available.value.find((r) => r.blockId === blockId.value);
      start.value = next?.start ?? 0;
      end.value = next?.end ?? 0;
    },
    { flush: 'sync' },
  );
  watch(
    available,
    (ranges) => {
      if (!blockId.value && ranges[0]) choose(ranges[0]);
    },
    { immediate: true, flush: 'sync' },
  );
  watch(
    [blockId, start, end],
    () => {
      error.value = '';
    },
    { flush: 'sync' },
  );

  function add() {
    if (disabled()) return;
    error.value = '';
    status.value = '';
    if (fields.value.length >= 500) {
      error.value = '单个模板最多配置500个字段，请先核对现有字段';
      return;
    }
    const block = selected.value;
    if (
      !block ||
      !Number.isInteger(start.value) ||
      !Number.isInteger(end.value) ||
      start.value < 0 ||
      end.value < start.value ||
      end.value > block.text.length
    ) {
      error.value = '请选择段落及有效的起止位置';
      return;
    }
    const original = block.text.slice(start.value, end.value);
    if (!/^[\s_＿]*$/.test(original) || (!original && block.text !== '')) {
      error.value = '只能补标原有空白，不能覆盖固定文字、单位或条款';
      return;
    }
    if (
      fields.value.some(
        (f) =>
          f.blockId === block.id &&
          ((f.start === start.value && f.end === end.value) ||
            (f.start < end.value && f.end > start.value)),
      )
    ) {
      error.value =
        '该位置已配置或与其他字段重叠，请选择下一处空白；已有字段可在上方修改或启用';
      return;
    }
    const unit = block.text.slice(end.value).match(/^([元年月日%])/)?.[1] || '';
    const added: TemplateField = {
      id: `${block.id}-${start.value}-${end.value}`,
      blockId: block.id,
      start: start.value,
      end: end.value,
      original,
      label: '填写项',
      kind: unit === '元' ? 'money' : 'text',
      unit,
      presentation: /[_＿]/.test(original) ? 'underline' : 'text',
      enabled: true,
    };
    const updated = [...fields.value, added].sort(
      (a, b) =>
        blocks().findIndex((p) => p.id === a.blockId) -
          blocks().findIndex((p) => p.id === b.blockId) || a.start - b.start,
    );
    fields.value = updated;
    additions.value.push(added.id);
    // A component v-model may not reflect the emitted array until the next render.
    const remaining = findAvailable(updated);
    const next =
      remaining.find((r) => r.blockId === block.id && r.start >= added.end) ??
      remaining.find((r) => r.blockId === block.id) ??
      remaining[0];
    if (next) choose(next);
    else {
      start.value = 0;
      end.value = 0;
    }
    status.value = `已添加 ${block.id} 的填写字段（${added.start}—${added.end}）。${next ? '已选中下一处空白，可继续添加。' : '当前没有剩余空白候选，可切换段落并手动核对原文。'}`;
  }

  function undo() {
    if (disabled()) return;
    const id = additions.value.findLast((key) =>
      fields.value.some((f) => f.id === key),
    );
    const field = fields.value.find((f) => f.id === id);
    if (!field) return;
    fields.value = fields.value.filter((f) => f.id !== id);
    additions.value = additions.value.filter((key) => key !== id);
    choose({
      key: '',
      blockId: field.blockId,
      start: field.start,
      end: field.end,
    });
    status.value = '已撤销最近一次补标，可重新选择范围后添加；原文件保持不变。';
  }

  return {
    add,
    addedCount,
    available,
    blockId,
    end,
    error,
    rangeKey,
    selected,
    start,
    status,
    undo,
  };
}
