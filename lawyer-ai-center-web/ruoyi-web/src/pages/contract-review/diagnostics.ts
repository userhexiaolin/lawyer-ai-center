/** Raw replies remain in the existing private conversation; export only on an explicit user click. */
export function downloadReviewDiagnostic(content: string) {
  const blob = new Blob([JSON.stringify({ exportedAt: new Date().toISOString(), reason: '结构化解析失败', rawResponse: content }, null, 2)], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url; link.download = '合同审查解析诊断.json'; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
