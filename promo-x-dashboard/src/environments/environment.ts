import { Environment } from './environment.model';

export const environment: Environment = {
  apiUrl: 'http://localhost:8000',
  auth: {
    url: 'http://localhost:8080',
    realm: 'promo-x-dev',
    clientId: 'promo-x-web',
  },
};
