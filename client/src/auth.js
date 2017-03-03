import fetch from 'isomorphic-fetch';
import Keycloak from 'keycloak-js';

let keycloak = null;
let authorization = null;

export function token() {
  if (keycloak == null) {
    throw new Error('Keycloak not initialized');
  }

  return new Promise((resolve, reject) =>
    keycloak.updateToken()
      .success(() => resolve(keycloak.token))
      .error(err => reject(err))
  );
}

export function au() {
  return authorization;
}

export function init() {
  if (keycloak != null) {
    throw new Error('Keycloak already initialized');
  }
  return fetch('/env')
    .then(response => response.json());
  ;
}
