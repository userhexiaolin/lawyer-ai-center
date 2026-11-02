/** A download can return an R error envelope with HTTP 200. Never save that as Word. */
export async function readWordResponse(response: Response): Promise<Blob> {
  const blob = await response.blob();
  const prefix = new Uint8Array(await blob.slice(0, 4).arrayBuffer());
  if (!response.ok || prefix[0] !== 0x50 || prefix[1] !== 0x4b || prefix[2] !== 3 || prefix[3] !== 4) {
    let message = '下载失败，未收到有效 Word 文件，请重新生成后重试';
    if (blob.size < 65536) {
      try { message = JSON.parse(await blob.text()).msg || message; } catch { /* non-JSON server error */ }
    }
    throw new Error(message);
  }
  return blob;
}

export function wordFilename(title: string): string {
  return (title.replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').trim().slice(0, 150) || '检索报告') + '.docx';
}
