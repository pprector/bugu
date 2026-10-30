import { request } from '../request';

/**
 * 后台登录(E9):POST /api/v1/admin/auth/login
 * 服务端返回单 token(无 refresh 轮换),refreshToken 以同一 token 填充以满足 Soybean 存储约定。
 */
export async function fetchLogin(userName: string, password: string) {
  const res = await request<{ token: string }>({
    url: '/api/v1/admin/auth/login',
    method: 'post',
    data: {
      username: userName,
      password
    }
  });

  if (res.error || !res.data) {
    return { data: null, error: res.error, response: res.response };
  }

  return { data: { token: res.data.token, refreshToken: res.data.token }, error: null, response: res.response };
}

/** 后台 me:账号 + 角色 + 按钮权限点(E9 RBAC) */
export async function fetchGetUserInfo() {
  const res = await request<{
    id: number;
    username: string;
    nickname: string;
    role: string;
    perms: string[];
  }>({ url: '/api/v1/admin/auth/me' });

  if (res.error || !res.data) {
    return { data: null, error: res.error, response: res.response };
  }

  const { id, username, nickname, role, perms } = res.data;
  return {
    data: {
      userId: String(id),
      userName: username,
      nickname,
      roles: [role],
      buttons: perms
    },
    error: null,
    response: res.response
  };
}

/**
 * Refresh token
 *
 * 后台单 token 体系无 refresh(E9):过期经 VITE_SERVICE_LOGOUT_CODES=UNAUTHORIZED 走重新登录。
 */
export function fetchRefreshToken(refreshToken: string) {
  return Promise.resolve({ data: { token: refreshToken, refreshToken }, error: null, response: null });
}
