export interface RequestInstanceState {
  /** the promise of refreshing token */
  refreshTokenPromise: Promise<boolean> | null;
  /** the request error message stack */
  errMsgStack: string[];
  /** 会话过期是否已提示,避免并发 401 重复弹窗 */
  sessionExpiredShown?: boolean;
  [key: string]: unknown;
}
