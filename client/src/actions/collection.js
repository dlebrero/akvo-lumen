import * as constants from '../constants/collection';

export default function createCollection(name) {
  const now = Date.now();
  const id = Math.round(Math.random() * 1e9);

  return {
    type: constants.CREATE,
    collection: {
      id,
      name,
      entities: [],
      created: now,
      modified: now,
    },
  };
}
