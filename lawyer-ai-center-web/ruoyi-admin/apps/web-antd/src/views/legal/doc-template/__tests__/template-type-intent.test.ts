import { effectScope, ref } from 'vue';

import { afterEach, describe, expect, it, vi } from 'vitest';

import { useTemplateTypeChoice } from '../use-template-type-choice';

const scopes: ReturnType<typeof effectScope>[] = [];
afterEach(() => scopes.splice(0).forEach((scope) => scope.stop()));
const options = [
  { value: '原类型', label: '原类型' },
  { value: '人工类型', label: '人工类型' },
  { value: 'AI类型', label: 'AI类型' },
];
function setup() {
  const scope = effectScope();
  scopes.push(scope);
  const type = ref('原类型');
  const classify =
    vi.fn<
      (
        _: AbortSignal,
      ) => Promise<{ documentType: null | string; warning?: string }>
    >();
  const choice = scope.run(() =>
    useTemplateTypeChoice(
      () => options,
      (value) => {
        type.value = value;
      },
      classify,
    ),
  );
  if (!choice) throw new Error('Inactive scope');
  return { ...choice, type, classify };
}
describe('latest explicit type choice', () => {
  it('shows an actionable server failure once and preserves the current manual type', async () => {
    const choice = setup();
    choice.select('人工类型');
    const warning =
      '类型识别服务尚未初始化，请联系管理员完成数据库升级；当前类型已保留，可手动选择或继续上传';
    choice.classify.mockResolvedValue({ documentType: null, warning });
    await choice.recognize();
    expect(choice.notice.value).toBe(warning);
    expect(choice.type.value).toBe('人工类型');
    expect(choice.recognizing.value).toBe(false);
  });
  it('shows the server evidence verbatim without converting words inside the quoted source', async () => {
    const choice = setup();
    const warning =
      'AI建议依据原文“现确定下列权利义务”；待人工核对（复用相同内容的识别建议）';
    choice.classify.mockResolvedValue({ documentType: 'AI类型', warning });
    await choice.recognize();
    expect(choice.notice.value).toContain(warning);
    expect(choice.notice.value).toContain('待保存');
  });
  it('replaces a previous manual choice after an explicit AI click, without saving', async () => {
    const choice = setup();
    choice.select('人工类型');
    choice.classify.mockResolvedValue({ documentType: 'AI类型' });
    await choice.recognize();
    expect(choice.type.value).toBe('AI类型');
    expect(choice.notice.value).toContain('待保存');
    choice.select('人工类型');
    expect(choice.type.value).toBe('人工类型');
  });
  it('discards an older response after manual change or a newer request', async () => {
    const choice = setup();
    let finish!: (value: { documentType: string }) => void;
    choice.classify.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const pending = choice.recognize();
    choice.select('人工类型');
    expect(choice.classify.mock.calls[0]?.[0].aborted).toBe(true);
    finish({ documentType: 'AI类型' });
    await pending;
    expect(choice.type.value).toBe('人工类型');
    choice.classify.mockResolvedValue({ documentType: 'AI类型' });
    await choice.recognize();
    expect(choice.type.value).toBe('AI类型');
  });
  it('retains current choice on failures or a closed form', async () => {
    const choice = setup();
    choice.select('人工类型');
    choice.classify.mockRejectedValueOnce(new Error('timeout'));
    await choice.recognize();
    expect(choice.type.value).toBe('人工类型');
    choice.classify.mockResolvedValue({ documentType: null });
    await choice.recognize();
    expect(choice.type.value).toBe('人工类型');
    choice.reset();
    expect(choice.notice.value).toBe('');
  });
});
