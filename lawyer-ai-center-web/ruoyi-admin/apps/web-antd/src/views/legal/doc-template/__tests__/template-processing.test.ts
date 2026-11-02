import type { DocTemplate, TemplateFieldTask } from '#/api/legal/doc-template';

import { describe, expect, it } from 'vitest';

import { templateProcessing } from '../template-processing';

const row: DocTemplate = {
  name: '合成模板',
  content: '',
  documentType: '律师函',
  status: '0',
  remark: '',
  sortOrder: 0,
  sourceVersion: 'v1',
  parseStatus: 'confirmed',
  fieldsConfirmed: true,
};
const task = (state: TemplateFieldTask['state']): TemplateFieldTask => ({
  state,
  taskId: 'job',
  phase: '',
  warning: '',
  startedAt: '',
  expiresAt: '',
});
describe('template next-step classification', () => {
  it('prioritizes new suggestions and failures over an older confirmed configuration', () => {
    expect(templateProcessing({ ...row, fieldAnalysis: task('ready') })).toBe(
      'review',
    );
    expect(templateProcessing({ ...row, fieldAnalysis: task('partial') })).toBe(
      'review',
    );
    expect(templateProcessing({ ...row, fieldAnalysis: task('running') })).toBe(
      'running',
    );
    expect(templateProcessing({ ...row, fieldAnalysis: task('failed') })).toBe(
      'error',
    );
    expect(templateProcessing({ ...row, fieldAnalysis: task('stale') })).toBe(
      'error',
    );
    expect(
      templateProcessing({ ...row, fieldAnalysis: task('reviewed') }),
    ).toBe('confirmed');
  });
  it('keeps activation separate from parsing and manual confirmation', () => {
    expect(templateProcessing({ ...row, status: '1' })).toBe('confirmed');
    expect(
      templateProcessing({
        ...row,
        parseStatus: 'uploaded',
        fieldsConfirmed: false,
      }),
    ).toBe('unparsed');
    expect(
      templateProcessing({
        ...row,
        parseStatus: 'rules',
        fieldsConfirmed: false,
      }),
    ).toBe('review');
    expect(templateProcessing({ ...row, sourceVersion: undefined })).toBe(
      'text',
    );
  });
});
