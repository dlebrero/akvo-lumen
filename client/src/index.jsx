import React from 'react';
import { render } from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { browserHistory } from 'react-router';
import { syncHistoryWithStore } from 'react-router-redux';
import Root from './containers/Root';
import configureStore from './store/configureStore';
import * as auth from './auth';

function initAuthenticated(profile) {
  const initialState = {
    user: {
      name: profile.username,
      organization: 'Akvo Lumen',
    },
  };

  const store = configureStore(initialState);
  const history = syncHistoryWithStore(browserHistory, store);
  const rootElement = document.querySelector('#root');

  function doRender(Component) {
    render(
      <AppContainer>
        <Component
          history={history}
          store={store}
        />
      </AppContainer>,
      rootElement
    );
  }
  doRender(Root);
  if (module.hot) {
    /* eslint-disable global-require */
    module.hot.accept('./containers/Root', () => doRender(require('./containers/Root').default));
    /* eslint-enable global-require */
  }
}

function initNotAuthenticated() {
  document.querySelector('#root').innerHTML = 'Authentication required.';
}

auth.init()
  .then(profile => initAuthenticated(profile))
  .catch(() => initNotAuthenticated());
