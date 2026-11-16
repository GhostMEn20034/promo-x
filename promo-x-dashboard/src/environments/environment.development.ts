import { environment as production } from './environment';

export const environment: typeof production = {
  apiUrl: 'http://localhost:8000',
  auth: {
    url: 'http://localhost:8080',
    realm: 'promo-x',
    clientId: 'promo-x-dashboard',
  },
};
