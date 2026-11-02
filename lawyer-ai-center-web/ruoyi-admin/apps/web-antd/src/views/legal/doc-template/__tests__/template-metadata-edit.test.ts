// @vitest-environment happy-dom
/* eslint-disable vue/one-component-per-file -- Isolated UI controls for editor behavior. */
import type { PropType } from 'vue';

import type { DocTemplate } from '#/api/legal/doc-template';

import { flushPromises, mount } from '@vue/test-utils';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  classifyExistingTemplateType,
  saveTemplate,
  saveTemplateMetadata,
} from '#/api/legal/doc-template';

import TemplateEditor from '../template-editor-drawer.vue';

vi.mock('#/api/legal/doc-template', () => ({
  classifyExistingTemplateType: vi.fn(),
  saveTemplate: vi.fn(),
  saveTemplateMetadata: vi.fn(),
}));
vi.mock('../template-ai-icon.vue', () => ({ default: { template: '<i />' } }));
vi.mock('ant-design-vue', async () => {
  const { defineComponent, h } = await import('vue');
  const plain = defineComponent({
    setup:
      (_, { slots }) =>
      () =>
        h('div', slots.default?.()),
  });
  const input = defineComponent({
    props: { value: { type: [String, Number], default: undefined } },
    emits: ['update:value'],
    setup:
      (props, { emit }) =>
      () =>
        h('input', {
          value: props.value,
          onInput: (event: Event) =>
            emit('update:value', (event.target as HTMLInputElement).value),
        }),
  });
  return {
    Alert: defineComponent({
      props: { message: { type: String, default: '' } },
      setup: (props) => () => h('div', props.message),
    }),
    Button: defineComponent({
      props: { disabled: Boolean, loading: Boolean },
      setup:
        (props, { slots }) =>
        () =>
          h('button', { disabled: props.disabled }, slots.default?.()),
    }),
    Form: defineComponent({
      setup: (_, { slots, expose }) => {
        expose({ validate: () => Promise.resolve(), clearValidate: () => {} });
        return () => h('form', slots.default?.());
      },
    }),
    FormItem: plain,
    Input: Object.assign({ ...input }, { TextArea: input }),
    InputNumber: input,
    Modal: { confirm: vi.fn(() => ({ destroy: vi.fn() })) },
    Drawer: defineComponent({
      name: 'EditorDrawer',
      props: { open: Boolean },
      emits: ['close', 'afterOpenChange'],
      setup:
        (props, { slots }) =>
        () =>
          h('section', props.open ? [slots.default?.(), slots.footer?.()] : []),
    }),
    Select: defineComponent({
      props: {
        value: { type: String, default: undefined },
        options: {
          type: Array as PropType<{ label: string; value: string }[]>,
          default: () => [],
        },
      },
      emits: ['update:value'],
      setup:
        (props, { emit }) =>
        () =>
          h(
            'select',
            {
              value: props.value,
              onChange: (event: Event) =>
                emit('update:value', (event.target as HTMLSelectElement).value),
            },
            props.options.map((option) =>
              h('option', { value: option.value }, option.label),
            ),
          ),
    }),
    Space: plain,
    Tag: plain,
  };
});
const template: DocTemplate = {
  id: '19',
  name: '模板',
  documentType: '律师函',
  content: '本金____元',
  sortOrder: 0,
  status: '1',
  remark: '人工备注',
  sourceVersion: 'source',
  fieldsConfirmed: true,
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(saveTemplateMetadata).mockReset().mockResolvedValue(undefined);
  vi.mocked(saveTemplate).mockReset().mockResolvedValue(undefined);
});
function editor(initial = template, canWrite = true) {
  return mount(TemplateEditor, {
    props: {
      open: true,
      initial,
      canWrite,
      types: [
        { value: '律师函', label: '律师函' },
        { value: '申请书', label: '申请书' },
        { value: '台账', label: '台账' },
      ],
    },
  });
}
async function click(wrapper: ReturnType<typeof editor>, text = '保存模板') {
  const button = wrapper.findAll('button').find((item) => item.text() === text);
  if (!button) throw new Error(`Missing action ${text}`);
  await button.trigger('click');
  await flushPromises();
}

describe('template editor persistence and recovery', () => {
  it.each(['律师函', '申请书', '台账'])(
    'saves the manual type %s regardless of the name or body inference',
    async (documentType) => {
      const wrapper = editor({
        ...template,
        name: '物业服务催款函',
        content: '物业服务费催款函\n请履行付款义务。',
      });
      await wrapper.find('select').setValue(documentType);
      await click(wrapper);
      expect(wrapper.emitted('saved')).toHaveLength(1);
      expect(wrapper.emitted('update:open')).toEqual([[false]]);
      expect(saveTemplateMetadata).toHaveBeenLastCalledWith(
        expect.objectContaining({ documentType, name: '物业服务催款函' }),
      );
      expect(classifyExistingTemplateType).not.toHaveBeenCalled();
      wrapper.unmount();
    },
  );
  it('returns keyboard focus to the opener after the drawer closes', async () => {
    const trigger = document.createElement('button');
    document.body.append(trigger);
    const wrapper = mount(TemplateEditor, {
      attachTo: document.body,
      props: { open: false, initial: template, canWrite: true, types: [] },
    });
    trigger.focus();
    await wrapper.setProps({ open: true });
    const drawer = wrapper.findComponent({ name: 'EditorDrawer' });
    drawer.vm.$emit('afterOpenChange', true);
    expect(document.activeElement?.id).toBe('template-name');
    await wrapper.setProps({ open: false });
    drawer.vm.$emit('afterOpenChange', false);
    expect(document.activeElement).toBe(trigger);
    wrapper.unmount();
    trigger.remove();
  });
  it('only saves metadata for a source template and waits for the response', async () => {
    let finish!: () => void;
    vi.mocked(saveTemplateMetadata).mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve;
        }),
    );
    const wrapper = editor();
    await wrapper.find('select').setValue('申请书');
    await Promise.all([click(wrapper), click(wrapper)]);
    expect(saveTemplateMetadata).toHaveBeenCalledTimes(1);
    expect(wrapper.emitted('saved')).toBeUndefined();
    finish();
    await flushPromises();
    expect(wrapper.emitted('saved')).toHaveLength(1);
    expect(wrapper.emitted('update:open')).toEqual([[false]]);
    expect(saveTemplate).not.toHaveBeenCalled();
    expect(saveTemplateMetadata).toHaveBeenCalledWith(
      expect.objectContaining({ documentType: '申请书' }),
    );
    wrapper.unmount();
  });
  it('retains the full save path for editable text templates', async () => {
    const wrapper = editor({ ...template, sourceVersion: undefined });
    await click(wrapper);
    expect(saveTemplate).toHaveBeenCalledWith(
      expect.objectContaining({ content: '本金____元' }),
    );
    expect(saveTemplateMetadata).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it.each(['请求超时', '409：模板已变化'])(
    'retains edits and a visible error after %s, then permits retry',
    async (reason) => {
      vi.mocked(saveTemplateMetadata).mockRejectedValueOnce(new Error(reason));
      const wrapper = editor();
      await wrapper.find('select').setValue('申请书');
      await click(wrapper);
      expect(wrapper.find('select').element.value).toBe('申请书');
      expect(wrapper.text()).toContain('本次修改尚未保存');
      expect(wrapper.emitted('saved')).toBeUndefined();
      expect(wrapper.emitted('update:open')).toBeUndefined();
      await click(wrapper);
      expect(wrapper.emitted('saved')).toHaveLength(1);
      wrapper.unmount();
    },
  );
  it('an explicit AI suggestion remains local and later manual choice wins', async () => {
    vi.mocked(classifyExistingTemplateType).mockResolvedValue({
      id: '19',
      previousType: '律师函',
      documentType: '律师函',
      revision: 'v1',
      warning: '',
    });
    const wrapper = editor();
    await wrapper.find('select').setValue('申请书');
    await click(wrapper, 'AI识别类型');
    expect(wrapper.find('select').element.value).toBe('律师函');
    expect(saveTemplateMetadata).not.toHaveBeenCalled();
    await wrapper.find('select').setValue('申请书');
    await click(wrapper);
    expect(saveTemplateMetadata).toHaveBeenCalledWith(
      expect.objectContaining({ documentType: '申请书' }),
    );
    wrapper.unmount();
  });
  it('provides no save or AI action for a query-only user', () => {
    const wrapper = editor(template, false);
    expect(wrapper.text()).not.toContain('保存模板');
    expect(wrapper.text()).not.toContain('AI识别类型');
    wrapper.unmount();
  });
  it('requires confirmation before discarding local edits', async () => {
    const { Modal } = await import('ant-design-vue');
    const wrapper = editor();
    await wrapper.find('select').setValue('申请书');
    await click(wrapper, '取消');
    expect(Modal.confirm).toHaveBeenCalledWith(
      expect.objectContaining({ title: '放弃本次未保存的修改？' }),
    );
    expect(wrapper.emitted('update:open')).toBeUndefined();
    wrapper.unmount();
  });
});
