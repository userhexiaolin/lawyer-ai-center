import { useUserStore } from '@/stores';

export interface OssUploadResult { url: string; fileName: string; ossId: string }
interface FileRequestOptions { signal?: AbortSignal; onProgress?: (percent: number) => void; timeout?: number }

/** Multipart requests leave Content-Type to the browser so the boundary is preserved. */
export function postFile<T>(path: string, file: File, options: FileRequestOptions = {}): Promise<T> {
  const user = useUserStore();
  return new Promise((resolve, reject) => {
    if (!user.token) { reject(new Error('请先登录后上传文件')); return; }
    const xhr = new XMLHttpRequest();
    const abort = () => xhr.abort();
    const cleanup = () => options.signal?.removeEventListener('abort', abort);
    xhr.open('POST', `${import.meta.env.VITE_API_URL.replace(/\/$/, '')}${path}`);
    xhr.setRequestHeader('Authorization', `Bearer ${user.token}`);
    xhr.setRequestHeader('clientid', import.meta.env.VITE_CLIENT_ID);
    xhr.timeout = options.timeout ?? 120000;
    xhr.upload.onprogress = event => {
      if (event.lengthComputable) options.onProgress?.(Math.min(99, Math.round(event.loaded / event.total * 100)));
    };
    xhr.onload = () => {
      cleanup();
      try {
        const body = JSON.parse(xhr.responseText);
        if (xhr.status === 401 || body.code === 401) user.handleAuthExpired('/contract-review');
        if (xhr.status < 200 || xhr.status >= 300 || body.code !== 200) throw new Error(body.msg || `文件请求失败（${xhr.status}）`);
        options.onProgress?.(100);
        resolve(body.data as T);
      }
      catch (error) { reject(error instanceof Error ? error : new Error('文件接口返回了无效响应')); }
    };
    xhr.onerror = () => { cleanup(); reject(new Error('文件请求失败，请检查网络后重试')); };
    xhr.ontimeout = () => { cleanup(); reject(new Error('文件处理超时，请重试或使用更小的文件')); };
    xhr.onabort = () => { cleanup(); reject(new DOMException('已取消', 'AbortError')); };
    if (options.signal?.aborted) { reject(new DOMException('已取消', 'AbortError')); return; }
    options.signal?.addEventListener('abort', abort, { once: true });
    const data = new FormData();
    data.append('file', file);
    xhr.send(data);
  });
}

export async function uploadFile(file: File, options?: FileRequestOptions): Promise<OssUploadResult> {
  const result = await postFile<OssUploadResult>('/resource/oss/upload', file, options);
  if (!result?.url || !result.ossId || !/^https?:\/\//i.test(result.url)) throw new Error('上传接口未返回有效的文件地址');
  return result;
}
