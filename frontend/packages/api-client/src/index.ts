import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './generated/schema';

export interface IsmClientOptions {
  baseUrl?: string;
  getAccessToken?: () => string | undefined;
  fetch?: typeof globalThis.fetch;
}

export function createIsmClient(options: IsmClientOptions = {}) {
  const client = createClient<paths>({
    baseUrl: options.baseUrl ?? '/api',
    fetch: options.fetch,
  });

  const auth: Middleware = {
    async onRequest({ request }) {
      const token = options.getAccessToken?.();
      if (token) request.headers.set('Authorization', `Bearer ${token}`);
      return request;
    },
  };
  client.use(auth);
  return client;
}

export type { components, operations, paths } from './generated/schema';
