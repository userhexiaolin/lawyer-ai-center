import type { DocumentTypeOption } from '#/api/legal/doc-template';

import { describe, expect, it } from 'vitest';

import { templateTypeFromFilename } from '../template-filename-type';

const options: DocumentTypeOption[] = [
  '民事起诉状',
  '民事答辩状',
  '律师函',
  '合同文书',
  '法律意见书',
  '自由描述文书',
].map((value) => ({ value, label: value }));

describe('filename type matching', () => {
  it.each([
    ['01_民事起诉状（示例）.docx', '民事起诉状'],
    ['答辩状_第二版.pdf', '民事答辩状'],
    ['催收律师函.DOCX', '律师函'],
    ['法律意见书_终稿.md', '法律意见书'],
    ['房屋租赁合同.doc', '合同文书'],
    ['起诉状.txt', '民事起诉状'],
    ['合同纠纷_民事起诉状.docx', '民事起诉状'],
    ['民事 起诉状_全角１.odt', '民事起诉状'],
  ])('matches %s to %s', (filename, expected) => {
    expect(templateTypeFromFilename(filename, options)).toBe(expected);
  });

  it.each(['模板01.docx', '律师函_民事起诉状.docx', '合同_答辩状.pdf'])(
    'does not guess when %s is unknown or ambiguous',
    (filename) => {
      expect(templateTypeFromFilename(filename, options)).toBeUndefined();
    },
  );

  it('uses current enabled custom names and labels, preferring a specific complete name', () => {
    const custom = [
      ...options,
      { value: '诉状', label: '诉状' },
      { value: '执行申请书', label: '执行申请书' },
      { value: 'rent', label: '房屋租赁合同' },
      { value: 'English', label: 'Legal Notice' },
    ];
    expect(templateTypeFromFilename('民事起诉状.docx', custom)).toBe(
      '民事起诉状',
    );
    expect(templateTypeFromFilename('执行申请书.docx', custom)).toBe(
      '执行申请书',
    );
    expect(templateTypeFromFilename('房屋租赁合同.docx', custom)).toBe('rent');
    expect(templateTypeFromFilename('LEGAL NOTICE.PDF', custom)).toBe(
      'English',
    );
  });

  it('ignores disabled types, empty keywords and names that exist only in the extension', () => {
    expect(
      templateTypeFromFilename('律师函.docx', [
        { value: '律师函', label: '律师函', disabled: true },
        { value: '', label: '' },
        { value: 'docx', label: 'docx' },
      ]),
    ).toBeUndefined();
    expect(
      templateTypeFromFilename('模板.docx', [
        { value: '__proto__', label: '__proto__' },
      ]),
    ).toBeUndefined();
  });
});
