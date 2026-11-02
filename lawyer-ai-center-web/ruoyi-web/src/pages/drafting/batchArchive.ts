/** Store-only ZIP: exported DOCX files are already compressed. UTF-8 preserves Chinese filenames. */
export function batchArchive(files: { name: string; bytes: Uint8Array }[]): Blob {
  const parts: Uint8Array<ArrayBuffer>[] = [],
    directory: Uint8Array<ArrayBuffer>[] = [];
  let offset = 0,
    size = 0;
  const encoder = new TextEncoder();
  const header = (length: number) => new Uint8Array(length);
  for (const file of files) {
    const name = encoder.encode(file.name),
      local = header(30),
      central = header(46),
      a = new DataView(local.buffer),
      b = new DataView(central.buffer);
    let crc = 0xffffffff;
    for (const byte of file.bytes) {
      crc ^= byte;
      for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
    }
    crc = (crc ^ 0xffffffff) >>> 0;
    a.setUint32(0, 0x04034b50, true);
    a.setUint16(4, 20, true);
    a.setUint16(6, 0x800, true);
    a.setUint16(12, 33, true);
    a.setUint32(14, crc, true);
    a.setUint32(18, file.bytes.length, true);
    a.setUint32(22, file.bytes.length, true);
    a.setUint16(26, name.length, true);
    b.setUint32(0, 0x02014b50, true);
    b.setUint16(4, 20, true);
    b.setUint16(6, 20, true);
    b.setUint16(8, 0x800, true);
    b.setUint16(14, 33, true);
    b.setUint32(16, crc, true);
    b.setUint32(20, file.bytes.length, true);
    b.setUint32(24, file.bytes.length, true);
    b.setUint16(28, name.length, true);
    b.setUint32(42, offset, true);
    parts.push(local, name, new Uint8Array(file.bytes));
    directory.push(central, name);
    offset += 30 + name.length + file.bytes.length;
    size += 46 + name.length;
  }
  const end = header(22),
    view = new DataView(end.buffer);
  const tail: Uint8Array<ArrayBuffer>[] = [];
  if (files.length >= 0xffff) {
    const record = header(56),
      locator = header(20),
      r = new DataView(record.buffer),
      l = new DataView(locator.buffer);
    r.setUint32(0, 0x06064b50, true);
    r.setBigUint64(4, 44n, true);
    r.setUint16(12, 45, true);
    r.setUint16(14, 45, true);
    r.setBigUint64(24, BigInt(files.length), true);
    r.setBigUint64(32, BigInt(files.length), true);
    r.setBigUint64(40, BigInt(size), true);
    r.setBigUint64(48, BigInt(offset), true);
    l.setUint32(0, 0x07064b50, true);
    l.setBigUint64(8, BigInt(offset + size), true);
    l.setUint32(16, 1, true);
    tail.push(record, locator);
  }
  view.setUint32(0, 0x06054b50, true);
  view.setUint16(8, Math.min(files.length, 0xffff), true);
  view.setUint16(10, Math.min(files.length, 0xffff), true);
  view.setUint32(12, size, true);
  view.setUint32(16, offset, true);
  return new Blob([...parts, ...directory, ...tail, end], { type: "application/zip" });
}
export function archiveName(index: number, fileName: string, type: string, extension: string) {
  return (
    String(index + 1).padStart(3, "0") +
    "-" +
    fileName
      .replace(/\.[^.]+$/, "")
      .replace(/[<>:"/\\|?*\x00-\x1f]/g, "_")
      .slice(0, 70) +
    "-" +
    type.replace(/[<>:"/\\|?*\x00-\x1f]/g, "_").slice(0, 30) +
    "." +
    extension
  );
}
