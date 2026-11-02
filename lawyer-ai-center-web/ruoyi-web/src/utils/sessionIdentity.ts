import type { LoginUser } from '@/api/auth/types';
import { checkAuthExpiry } from '@/utils/authExpiry';

/** Hydrate only current account coordinates; never infer a tenant or user from a token. */
export async function fetchSessionIdentity(token: string, currentToken: () => string | undefined): Promise<LoginUser> {
  const response = await fetch(`${import.meta.env.VITE_API_URL}/system/user/getInfo`, {
    headers: { Authorization: `Bearer ${token}`, clientid: import.meta.env.VITE_CLIENT_ID },
    cache: 'no-store',
    signal: AbortSignal.timeout(15000),
  });
  if (currentToken() !== token)
    throw new Error('登录用户已变化，请重试');
  checkAuthExpiry(response.status);
  const body = await response.json();
  if (currentToken() !== token)
    throw new Error('登录用户已变化，请重试');
  checkAuthExpiry(response.status, body);
  if (!response.ok || body.code !== 200)
    throw new Error('暂时无法核对账号身份，请重试');
  const user = body.data?.user;
  const id = user?.userId;
  if (!user || typeof user.tenantId !== 'string' || !user.tenantId.trim()
    || !((typeof id === 'string' && /^[1-9]\d{0,18}$/.test(id) && BigInt(id) <= 9223372036854775807n)
      || (typeof id === 'number' && Number.isSafeInteger(id) && id > 0))) {
    throw new Error('账号身份信息不完整，请重新加载');
  }
  return {
    userId: String(id),
    tenantId: user.tenantId,
    username: typeof user.userName === 'string' ? user.userName : undefined,
    nickName: typeof user.nickName === 'string' ? user.nickName : undefined,
    avatar: typeof user.avatar === 'string' && !/^\d+$/.test(user.avatar) ? user.avatar : undefined,
    menuPermission: Array.isArray(body.data?.permissions) ? body.data.permissions.filter((value: unknown): value is string => typeof value === 'string') : [],
    rolePermission: Array.isArray(body.data?.roles) ? body.data.roles.filter((value: unknown): value is string => typeof value === 'string') : [],
  };
}
