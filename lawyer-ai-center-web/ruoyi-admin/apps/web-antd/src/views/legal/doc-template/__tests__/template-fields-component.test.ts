// @vitest-environment happy-dom
import type { TemplateField } from '#/api/legal/doc-template';

import { mount } from '@vue/test-utils';
import { defineComponent, h, ref } from 'vue';

import { describe, expect, it, vi } from 'vitest';

import TemplateFields from '../template-fields.vue';
import propertyParagraphs from './fixtures/property-paragraphs.json';

vi.mock('ant-design-vue', async () => {
  const { defineComponent, h } = await import('vue');
  const passthrough = defineComponent({
    setup:
      (_, { slots }) =>
      () =>
        h('div', slots.default?.()),
  });
  return {
    Alert: passthrough,
    Button: defineComponent({
      props: { disabled: Boolean },
      setup:
        (props, { slots }) =>
        () =>
          h('button', { disabled: props.disabled }, slots.default?.()),
    }),
    Checkbox: passthrough,
    Input: passthrough,
    InputNumber: passthrough,
    Select: passthrough,
  };
});

describe('template fields component v-model', () => {
  it('shows the exact source span and distinguishes real fields in one paragraph', () => {
    const text = '本金____元，利息___元';
    const fields: TemplateField[] = [
      {
        id: 'p0-2-6',
        blockId: 'p0',
        start: 2,
        end: 6,
        original: '____',
        label: '本金',
        kind: 'money',
        unit: '元',
        presentation: 'underline',
        enabled: true,
      },
      {
        id: 'p0-10-13',
        blockId: 'p0',
        start: 10,
        end: 13,
        original: '___',
        label: '利息',
        kind: 'money',
        unit: '元',
        presentation: 'underline',
        enabled: true,
      },
    ];
    const wrapper = mount(TemplateFields, {
      props: {
        modelValue: fields,
        blocks: [{ id: 'p0', text, editable: true }],
      },
    });
    try {
      const marks = wrapper.findAll('.field-source mark');
      expect(marks.map((mark) => mark.text())).toEqual(['____', '___']);
      expect(marks[0]?.attributes('aria-label')).toBe('p0-2-6原文填写位置');
      expect(wrapper.findAll('.field-source')).toHaveLength(1);
      expect(
        wrapper
          .findAll('.field-source > span, .field-source mark')
          .map((node) => node.text())
          .join(''),
      ).toBe(`原文：${text}`);
      expect(wrapper.text()).toContain('本段 2 个填写位置');
      expect(wrapper.text()).not.toContain('固定单位：');
      expect(wrapper.text()).not.toContain('原空白：');
      expect(wrapper.text()).not.toContain('位置 2—6');
    } finally {
      wrapper.unmount();
    }
  });
  it('renders the actual property paragraphs once and maps all 2 and 14 fields to the original ranges', () => {
    const blocks = propertyParagraphs as Array<{
      editable: boolean;
      fields: TemplateField[];
      id: string;
      text: string;
    }>;
    const fields = blocks.flatMap((block) => block.fields);
    const wrapper = mount(TemplateFields, {
      props: { modelValue: fields, blocks },
    });
    try {
      expect(wrapper.findAll('.field-group')).toHaveLength(3);
      expect(wrapper.findAll('.field-source')).toHaveLength(3);
      for (const [id, count] of [
        ['p3', 2],
        ['p4', 14],
      ] as const) {
        const group = wrapper.find(`[data-block-id="${id}"]`);
        const block = blocks.find((b) => b.id === id);
        if (!block) throw new Error('Missing original fixture');
        expect(group.findAll('.field-row')).toHaveLength(count);
        expect(group.findAll('.field-source mark')).toHaveLength(count);
        expect(group.findAll('.field-association')).toHaveLength(count);
        expect(
          group
            .findAll('.field-source > span, .field-source mark')
            .map((node) => node.text())
            .join(''),
        ).toBe(`原文：${block.text}`);
        for (const field of block.fields) {
          expect(group.findAll(`[data-field-id="${field.id}"]`)).toHaveLength(
            1,
          );
          expect(
            group.find(`mark[aria-label="${field.id}原文填写位置"]`).text(),
          ).toBe(block.text.slice(field.start, field.end));
        }
        if (id === 'p4') {
          expect(
            group
              .findAll('.field-association strong')
              .map((node) => node.text()),
          ).toEqual([
            '年',
            '月',
            '日',
            '市',
            '路',
            '弄',
            '号',
            '室',
            '元',
            '',
            '年',
            '月',
            '年',
            '月',
          ]);
          expect(group.findAll('.field-association')[0]?.text()).toContain(
            '截至____年',
          );
          expect(group.findAll('.field-association')[12]?.text()).toContain(
            '欠费期间',
          );
        }
      }
    } finally {
      wrapper.unmount();
    }
  });
  it('continues adding fifteen missing fields across paragraph boundaries using the rendered button', async () => {
    const fields = ref<TemplateField[]>([]);
    const blocks = Array.from({ length: 3 }, (_, i) => ({
      id: `p${i}`,
      text: '姓名___本金___元利息___元起日___截止日___',
      editable: true,
    }));
    const wrapper = mount(
      defineComponent({
        setup: () => () =>
          h(TemplateFields, {
            blocks,
            modelValue: fields.value,
            'onUpdate:modelValue': (value: TemplateField[]) => {
              fields.value = value;
            },
          }),
      }),
    );
    try {
      const addButton = wrapper
        .findAll('button')
        .find((b) => b.text() === '添加并继续');
      if (!addButton) throw new Error('Missing add button');
      for (let i = 0; i < 15; i++) {
        await addButton.trigger('click');
        expect(fields.value).toHaveLength(i + 1);
        expect(wrapper.text()).toContain(`本次已补标 ${i + 1} 项`);
      }
      expect(new Set(fields.value.map((f) => f.id)).size).toBe(15);
      const undoButton = wrapper
        .findAll('button')
        .find((b) => b.text() === '撤销最近一次补标');
      if (!undoButton) throw new Error('Missing undo button');
      await undoButton.trigger('click');
      expect(fields.value).toHaveLength(14);
      await addButton.trigger('click');
      expect(fields.value).toHaveLength(15);
    } finally {
      wrapper.unmount();
    }
  });
});
