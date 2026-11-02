import type { DocTemplate } from '#/api/legal/doc-template';

import { describe, expect, it, vi } from 'vitest';

import { saveTemplateMetadata } from '#/api/legal/doc-template';
import { requestClient } from '#/api/request';

vi.mock('#/api/request', () => ({
  requestClient: { putWithMsg: vi.fn() },
}));

const template: DocTemplate = {
  id: '19',
  name: '模板',
  documentType: '申请书',
  sortOrder: 0,
  status: '1',
  remark: '备注',
  content: '本金____元',
  sourceVersion: 'source',
  editableBlocks: ['p0'],
  fieldsConfirmed: true,
};

describe('basic template edits', () => {
  it('sends only metadata through the lightweight endpoint', async () => {
    vi.mocked(requestClient.putWithMsg).mockResolvedValueOnce(undefined);
    await saveTemplateMetadata(template);
    expect(requestClient.putWithMsg).toHaveBeenCalledExactlyOnceWith(
      '/legal/doc-template/19/metadata',
      {
        name: '模板',
        documentType: '申请书',
        sortOrder: 0,
        status: '1',
        remark: '备注',
      },
    );
  });

  it('propagates failures without automatically retrying a write', async () => {
    const error = new Error('请求超时，请稍后再试。');
    vi.mocked(requestClient.putWithMsg).mockRejectedValueOnce(error);
    await expect(saveTemplateMetadata(template)).rejects.toBe(error);
    expect(requestClient.putWithMsg).toHaveBeenCalledTimes(1);
  });
});
