// @vitest-environment happy-dom
import type { DocTemplate, TemplateField } from '#/api/legal/doc-template';

import { flushPromises, mount } from '@vue/test-utils';

import { message, Modal } from 'ant-design-vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  analyzeTemplateFields,
  parseTemplateVersion,
  saveTemplate,
  templatePreview,
} from '#/api/legal/doc-template';

import TemplateUploadModal from '../template-upload-modal.vue';
import TemplateFields from '../template-fields.vue';

vi.mock('#/api/legal/doc-template', () => ({
  analyzeTemplateFields: vi.fn(),
  parseTemplateVersion: vi.fn(),
  saveTemplate: vi.fn(),
  uploadTemplate: vi.fn(),
  templatePreview: vi.fn(),
}));
vi.mock('@ant-design/icons-vue', () => ({
  InboxOutlined: { template: '<i />' },
}));
vi.mock('ant-design-vue', async () => {
  const { defineComponent, h } = await import('vue');
  const plain = defineComponent({
    setup:
      (_, { slots }) =>
      () =>
        h('div', slots.default?.()),
  });
  return {
    message: { error: vi.fn() },
    Modal: { confirm: vi.fn(() => ({ destroy: vi.fn() })) },
    Drawer: defineComponent({
      name: 'TestDrawer',
      props: { open: Boolean },
      emits: ['ok', 'cancel', 'update:open'],
      setup:
        (_, { slots }) =>
        () =>
          h('section', [slots.default?.(), slots.footer?.()]),
    }),
    Button: defineComponent({
      props: { disabled: Boolean },
      setup:
        (props, { slots }) =>
        () =>
          h('button', { disabled: props.disabled }, slots.default?.()),
    }),
    Checkbox: defineComponent({
      props: { checked: Boolean, disabled: Boolean },
      emits: ['update:checked'],
      setup:
        (props, { emit, slots }) =>
        () =>
          h('label', [
            h('input', {
              type: 'checkbox',
              checked: props.checked,
              disabled: props.disabled,
              onChange: (event: Event) =>
                emit(
                  'update:checked',
                  (event.target as HTMLInputElement).checked,
                ),
            }),
            slots.default?.(),
          ]),
    }),
    Alert: defineComponent({
      props: { message: { type: String, default: '' } },
      setup: (props) => () => h('p', { role: 'alert' }, props.message),
    }),
    CheckboxGroup: plain,
    Form: plain,
    FormItem: plain,
    InputNumber: plain,
    Select: plain,
    Tag: plain,
    Popconfirm: plain,
    Input: Object.assign({ ...plain }, { TextArea: plain }),
    Upload: Object.assign(
      { ...plain },
      { Dragger: plain, LIST_IGNORE: 'ignore' },
    ),
  };
});

const field: TemplateField = {
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
};
beforeEach(() => {
  vi.mocked(analyzeTemplateFields).mockReset();
  vi.mocked(parseTemplateVersion).mockReset();
  vi.mocked(analyzeTemplateFields).mockResolvedValue({
    fields: [{ ...field }],
    method: 'AI',
    warning: '待核对',
  });
  vi.mocked(saveTemplate).mockReset();
  vi.mocked(saveTemplate).mockResolvedValue('test-id');
  vi.mocked(templatePreview).mockReset();
});
async function openDialog(initial: Partial<DocTemplate> = {}) {
  const wrapper = mount(TemplateUploadModal, {
    props: {
      open: true,
      types: [{ value: '律师函', label: '律师函' }],
      initial: {
        name: '确认测试',
        documentType: '律师函',
        status: '1',
        sourceVersion: 'version',
        sourceFormat: 'docx',
        configureExisting: true,
        content: '本金____元',
        blocks: [
          {
            id: 'p0',
            text: '本金____元',
            editable: true,
            fields: [{ ...field }],
          },
        ],
        ...initial,
      },
    },
  });
  await flushPromises();
  return wrapper;
}
async function confirmAndSave(wrapper: Awaited<ReturnType<typeof openDialog>>) {
  const confirmation = wrapper
    .findAll('label')
    .find((label) => label.text().includes('已核对启用字段'));
  if (!confirmation) throw new Error('Missing review checkbox');
  await confirmation.find('input').setValue(true);
  await wrapper
    .findAll('button')
    .find((button) => button.text() === '保存字段配置')!
    .trigger('click');
  await flushPromises();
  expect(saveTemplate).toHaveBeenCalledWith(
    expect.objectContaining({
      fieldsConfirmed: true,
      sourceVersion: 'version',
    }),
  );
  expect(vi.mocked(saveTemplate).mock.calls[0]?.[0]).not.toHaveProperty(
    'layoutConfirmed',
  );
}
describe('one review confirmation, optional PDF preview', () => {
  it('saves after one field confirmation without requesting a PDF or showing another layout checkbox', async () => {
    const wrapper = await openDialog();
    try {
      expect(wrapper.text()).not.toContain('已核对预览中的字体');
      await confirmAndSave(wrapper);
      expect(templatePreview).not.toHaveBeenCalled();
    } finally {
      wrapper.unmount();
    }
  });
  it('shows a clear unparsed empty state and waits for the explicit parse button', async () => {
    const wrapper = await openDialog({
      id: '19',
      name: '新的模板名称',
      sourceFilename: '02_原文件.docx',
      parseStatus: 'uploaded',
      content: '',
      blocks: [],
      editableBlocks: [],
      fieldsConfirmed: false,
    });
    try {
      expect(wrapper.text()).toContain('原文件已上传，尚未解析填写字段');
      expect(wrapper.text()).toContain('02_原文件.docx');
      expect(wrapper.text()).not.toContain('解析失败');
      expect(
        wrapper
          .findAll('label')
          .some((label) => label.text().includes('已核对启用字段')),
      ).toBe(false);
      expect(analyzeTemplateFields).not.toHaveBeenCalled();
      expect(parseTemplateVersion).not.toHaveBeenCalled();
      vi.mocked(parseTemplateVersion).mockResolvedValue({
        name: '原始名',
        documentType: '律师函',
        sourceVersion: 'working',
        sourceFormat: 'docx',
        content: '本金____元',
        sortOrder: 0,
        status: '1',
        remark: '',
        parseStatus: 'rules',
        blocks: [
          { id: 'p0', text: '本金____元', editable: false, fields: [field] },
        ],
      });
      const parse = wrapper
        .findAll('button')
        .find((button) => button.text() === '解析字段');
      if (!parse) throw new Error('Missing explicit parse action');
      await parse.trigger('click');
      await flushPromises();
      expect(parseTemplateVersion).toHaveBeenCalledWith('version', '律师函');
      expect(analyzeTemplateFields).toHaveBeenCalledWith('working');
      expect(wrapper.text()).not.toContain('原文件已上传，尚未解析填写字段');
    } finally {
      wrapper.unmount();
    }
  });
  it('ignores a preview returned after closing and reopening the same template', async () => {
    let finish: ((value: Blob) => void) | undefined;
    vi.mocked(templatePreview).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const wrapper = await openDialog();
    try {
      const preview = wrapper
        .findAll('button')
        .find((button) => button.text().includes('查看版式 PDF'));
      if (!preview) throw new Error('Missing preview button');
      await preview.trigger('click');
      await wrapper.setProps({ open: false });
      await wrapper.setProps({ open: true });
      if (!finish) throw new Error('Preview did not start');
      finish(new Blob(['%PDF-test']));
      await flushPromises();
      expect(wrapper.find('iframe').exists()).toBe(false);
      expect(wrapper.text()).not.toContain('未收到有效PDF预览');
    } finally {
      wrapper.unmount();
    }
  });
  it('ignores a preview when the template changes while the PDF signature is being read', async () => {
    let finishText: ((value: string) => void) | undefined;
    const signature = new Blob();
    Object.defineProperty(signature, 'text', {
      value: () =>
        new Promise<string>((resolve) => {
          finishText = resolve;
        }),
    });
    const pdf = new Blob(['%PDF-test']);
    vi.spyOn(pdf, 'slice').mockReturnValue(signature);
    vi.mocked(templatePreview).mockResolvedValueOnce(pdf);
    const wrapper = await openDialog();
    try {
      const preview = wrapper
        .findAll('button')
        .find((button) => button.text().includes('查看版式 PDF'));
      if (!preview) throw new Error('Missing preview button');
      await preview.trigger('click');
      await flushPromises();
      await wrapper.setProps({ open: false });
      await wrapper.setProps({
        open: true,
        initial: {
          name: '另一份模板',
          documentType: '律师函',
          sourceVersion: 'another',
          configureExisting: true,
          content: '正文',
          blocks: [],
        },
      });
      if (!finishText) throw new Error('Signature read did not start');
      finishText('%PDF-');
      await flushPromises();
      expect(wrapper.find('iframe').exists()).toBe(false);
      expect(wrapper.text()).not.toContain('createObjectURL');
    } finally {
      wrapper.unmount();
    }
  });
  it('keeps edits and allows confirmed save when optional PDF conversion fails', async () => {
    const wrapper = await openDialog();
    try {
      vi.mocked(templatePreview).mockRejectedValueOnce(
        new Error('转换服务不可用'),
      );
      const preview = wrapper
        .findAll('button')
        .find((button) => button.text().includes('查看版式 PDF'));
      if (!preview) throw new Error('Missing optional preview');
      await preview.trigger('click');
      await flushPromises();
      expect(wrapper.text()).toContain('转换服务不可用');
      await confirmAndSave(wrapper);
    } finally {
      wrapper.unmount();
    }
  });
});

describe('visible review validation', () => {
  it('keeps edited fields after a version conflict and asks before discarding them', async () => {
    const wrapper = await openDialog();
    try {
      wrapper
        .findComponent(TemplateFields)
        .vm.$emit('update:modelValue', [{ ...field, label: '人工修正本金' }]);
      await flushPromises();
      await wrapper.find('.field-confirmation input').setValue(true);
      vi.mocked(saveTemplate).mockRejectedValueOnce(
        new Error('409：模板版本已变化'),
      );
      await wrapper
        .findAll('button')
        .find((button) => button.text() === '保存字段配置')!
        .trigger('click');
      await flushPromises();
      expect(wrapper.text()).toContain('409：模板版本已变化');
      expect(wrapper.findComponent(TemplateFields).props('modelValue')).toEqual(
        [expect.objectContaining({ label: '人工修正本金' })],
      );
      expect(wrapper.emitted('update:open')).toBeUndefined();
      await wrapper
        .findAll('button')
        .find((button) => button.text() === '取消')!
        .trigger('click');
      expect(Modal.confirm).toHaveBeenCalledWith(
        expect.objectContaining({ title: '放弃本次未保存的字段修改？' }),
      );
      expect(wrapper.emitted('update:open')).toBeUndefined();
    } finally {
      wrapper.unmount();
    }
  });
  it('shows a three-second message on every failed save and focuses the checkbox without losing edits', async () => {
    const wrapper = await openDialog();
    try {
      const checkbox = wrapper.find('.field-confirmation input');
      const focus = vi.spyOn(checkbox.element as HTMLElement, 'focus');
      for (let attempt = 0; attempt < 2; attempt++) {
        await wrapper
          .findAll('button')
          .find((button) => button.text() === '保存字段配置')!
          .trigger('click');
        await flushPromises();
      }
      expect(message.error).toHaveBeenLastCalledWith(
        '请核对填写字段或段落，并勾选确认后保存',
        3,
      );
      expect(focus).toHaveBeenCalledTimes(2);
      expect(wrapper.find('#field-confirmation-error').text()).toContain(
        '请先核对字段',
      );
      expect(saveTemplate).not.toHaveBeenCalled();
      expect(wrapper.text()).toContain('本金');
      await confirmAndSave(wrapper);
    } finally {
      wrapper.unmount();
    }
  });
});
