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

export function setToken(rpt) {
  if (keycloak == null) {
    throw new Error('Keycloak not initialized');
  }

  keycloak.token = rpt;
}

export function au() {
  return authorization;
}

export function init() {
  if (keycloak != null) {
    throw new Error('Keycloak already initialized');
  }
  return fetch('/env')
    .then(response => response.json())
        .then(({ keycloakClient, keycloakURL, tenant }) => new Promise((resolve, reject) => {
          keycloak = new Keycloak({
            url: keycloakURL,
            realm: 'akvo',
            clientId: keycloakClient,
          });

          keycloak.init({ onLoad: 'login-required' }).success((authenticated) => {
            if (authenticated) {
              keycloak.loadUserProfile().success((profile) => {

                authorization = new KeycloakAuthorization(keycloak);
                resolve(Object.assign(
              {},
              profile,
              { admin: keycloak.hasRealmRole(`akvo:lumen:${tenant}:admin`) }
            ));
              }).error(() => {
                reject(new Error('Could not load user profile'));
              });
            } else {
              reject(new Error('Could not authenticate'));
            }
          }).error(() => {
            reject(new Error('Login attempt failed'));
          });
        }
  ));
}
