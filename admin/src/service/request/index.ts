import type { AxiosError, AxiosResponse } from 'axios';
import { BACKEND_ERROR_CODE, createFlatRequest, createRequest } from '@sa/axios';
import { getServiceBaseURL } from '@/utils/service';
import { localStg } from '@/utils/storage';
import { $t } from '@/locales';
import { useAuthStore } from '@/store/modules/auth';
import { getAuthorization, handleExpiredRequest, handleSessionExpired, showErrorMsg } from './shared';
import type { RequestInstanceState } from './type';

const isHttpProxy = import.meta.env.DEV && import.meta.env.VITE_HTTP_PROXY === 'Y';
const { baseURL, otherBaseURL } = getServiceBaseURL(import.meta.env, isHttpProxy);

/** geita 服务端错误文案字段为 message,兼容 Soybean 约定的 msg。 */
function resolveMsg(data: App.Service.Response<any> | undefined): string {
  return (data?.message || data?.msg || '') as string;
}

/** 登录接口:其 401 是账号密码错误,不能按「会话过期」处理。 */
const LOGIN_URL = '/admin/auth/login';

function isLoginRequest(url?: string): boolean {
  return Boolean(url && url.includes(LOGIN_URL));
}

/** 后端文案是否已是中文,英文技术性文案(如 "Bad Request")不直接给用户看。 */
function hasChinese(text: string): boolean {
  return /[\u4E00-\u9FA5]/.test(text);
}

/** axios / HTTP 错误 → 中文可读提示;原始错误码由调用处打印到控制台。 */
function translateError(error: AxiosError<any>, backendMessage: string): string {
  if (backendMessage && hasChinese(backendMessage)) {
    return backendMessage;
  }

  if (isLoginRequest(error.config?.url)) {
    return '登录失败，请稍后重试';
  }

  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT' || /timeout/i.test(error.message || '')) {
    return '请求超时，请稍后重试';
  }

  if (!error.response) {
    return '网络异常，请检查网络连接后重试';
  }

  const status = error.response.status;

  switch (status) {
    case 400:
      return '参数错误，请检查后重试';
    case 403:
      return '无权限执行该操作';
    case 404:
      return '请求的资源不存在';
    case 409:
      return '数据冲突，请刷新后重试';
    case 429:
      return '操作过于频繁，请稍后再试';
    default:
      return status >= 500 ? '服务暂时不可用，请稍后重试' : `请求失败（错误码：${status}）`;
  }
}

export const request = createFlatRequest(
  {
    baseURL
  },
  {
    defaultState: {
      errMsgStack: [],
      refreshTokenPromise: null
    } as RequestInstanceState,
    transform(response: AxiosResponse<App.Service.Response<any>>) {
      return response.data.data;
    },
    async onRequest(config) {
      const Authorization = getAuthorization();
      Object.assign(config.headers, { Authorization });

      return config;
    },
    isBackendSuccess(response) {
      // when the backend response code is "0000"(default), it means the request is success
      // to change this logic by yourself, you can modify the `VITE_SERVICE_SUCCESS_CODE` in `.env` file
      return String(response.data.code) === import.meta.env.VITE_SERVICE_SUCCESS_CODE;
    },
    async onBackendFail(response, instance) {
      const authStore = useAuthStore();
      const responseCode = String(response.data.code);

      function handleLogout() {
        authStore.resetStore();
      }

      function logoutAndCleanup() {
        handleLogout();
        window.removeEventListener('beforeunload', handleLogout);

        request.state.errMsgStack = request.state.errMsgStack.filter(msg => msg !== resolveMsg(response.data));
      }

      // when the backend response code is in `logoutCodes`, it means the user will be logged out and redirected to login page
      const logoutCodes = import.meta.env.VITE_SERVICE_LOGOUT_CODES?.split(',') || [];
      if (logoutCodes.includes(responseCode)) {
        handleSessionExpired(request.state);
        return null;
      }

      // when the backend response code is in `modalLogoutCodes`, it means the user will be logged out by displaying a modal
      const modalLogoutCodes = import.meta.env.VITE_SERVICE_MODAL_LOGOUT_CODES?.split(',') || [];
      if (modalLogoutCodes.includes(responseCode) && !request.state.errMsgStack?.includes(resolveMsg(response.data))) {
        request.state.errMsgStack = [...(request.state.errMsgStack || []), resolveMsg(response.data)];

        // prevent the user from refreshing the page
        window.addEventListener('beforeunload', handleLogout);

        window.$dialog?.error({
          title: $t('common.error'),
          content: resolveMsg(response.data),
          positiveText: $t('common.confirm'),
          maskClosable: false,
          closeOnEsc: false,
          onPositiveClick() {
            logoutAndCleanup();
          },
          onClose() {
            logoutAndCleanup();
          }
        });

        return null;
      }

      // when the backend response code is in `expiredTokenCodes`, it means the token is expired, and refresh token
      // the api `refreshToken` can not return error code in `expiredTokenCodes`, otherwise it will be a dead loop, should return `logoutCodes` or `modalLogoutCodes`
      const expiredTokenCodes = import.meta.env.VITE_SERVICE_EXPIRED_TOKEN_CODES?.split(',') || [];
      if (expiredTokenCodes.includes(responseCode)) {
        const success = await handleExpiredRequest(request.state);
        if (success) {
          const Authorization = getAuthorization();
          Object.assign(response.config.headers, { Authorization });

          return instance.request(response.config) as Promise<AxiosResponse>;
        }
      }

      return null;
    },
    onError(error) {
      // when the request is fail, you can show error message

      const backendMessage = resolveMsg(error.response?.data);
      const backendErrorCode = String(error.response?.data?.code || '');

      // 原始错误只进控制台:保留 url / HTTP 状态 / 错误码,便于排查
      console.warn('[request] 请求失败', {
        url: error.config?.url,
        status: error.response?.status,
        backendCode: backendErrorCode,
        axiosCode: error.code,
        rawMessage: error.message
      });

      // 会话过期:HTTP 401 或后端业务错误码 UNAUTHORIZED。
      // 登录接口的 401 代表账号密码错误,不能按「会话过期」处理。
      const isUnauthorized = error.response?.status === 401 || backendErrorCode === 'UNAUTHORIZED';
      if (isUnauthorized && !isLoginRequest(error.config?.url)) {
        handleSessionExpired(request.state);
        return;
      }

      // the error message is displayed in the modal
      const modalLogoutCodes = import.meta.env.VITE_SERVICE_MODAL_LOGOUT_CODES?.split(',') || [];
      if (modalLogoutCodes.includes(backendErrorCode)) {
        return;
      }

      // when the token is expired, refresh token and retry request, so no need to show error message
      const expiredTokenCodes = import.meta.env.VITE_SERVICE_EXPIRED_TOKEN_CODES?.split(',') || [];
      if (expiredTokenCodes.includes(backendErrorCode)) {
        return;
      }

      showErrorMsg(request.state, translateError(error, backendMessage));
    }
  }
);

export const demoRequest = createRequest(
  {
    baseURL: otherBaseURL.demo
  },
  {
    transform(response: AxiosResponse<App.Service.DemoResponse>) {
      return response.data.result;
    },
    async onRequest(config) {
      const { headers } = config;

      // set token
      const token = localStg.get('token');
      const Authorization = token ? `Bearer ${token}` : null;
      Object.assign(headers, { Authorization });

      return config;
    },
    isBackendSuccess(response) {
      // when the backend response code is "200", it means the request is success
      // you can change this logic by yourself
      return response.data.status === '200';
    },
    async onBackendFail(_response) {
      // when the backend response code is not "200", it means the request is fail
      // for example: the token is expired, refresh token and retry request
    },
    onError(error) {
      // when the request is fail, you can show error message

      let message = error.message;

      // show backend error message
      if (error.code === BACKEND_ERROR_CODE) {
        message = error.response?.data?.message || message;
      }

      window.$message?.error(message);
    }
  }
);
