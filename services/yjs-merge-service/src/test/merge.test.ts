import assert from 'node:assert/strict';
import test from 'node:test';

import * as Y from 'yjs';

import { InvalidMergeRequestError, mergeYjsState } from '../merge.js';

test('duplicate updates merge to the same Yjs document state', () => {
  const source = new Y.Doc();
  source.getText('content').insert(0, 'duplicate safe');
  const update = Y.encodeStateAsUpdate(source);

  const result = mergeYjsState({
    baseState: null,
    updates: [toBase64(update), toBase64(update)],
  });

  assert.equal(readText(result.mergedState), 'duplicate safe');
});

test('out-of-order updates converge after Yjs applies pending structs', () => {
  const source = new Y.Doc();
  const baseVector = Y.encodeStateVector(source);
  source.getText('content').insert(0, 'first');
  const firstUpdate = Y.encodeStateAsUpdate(source, baseVector);
  const firstVector = Y.encodeStateVector(source);
  source.getText('content').insert(5, ' second');
  const secondUpdate = Y.encodeStateAsUpdate(source, firstVector);

  const result = mergeYjsState({
    baseState: null,
    updates: [toBase64(secondUpdate), toBase64(firstUpdate)],
  });

  assert.equal(readText(result.mergedState), 'first second');
});

test('base snapshot and incremental updates restore the latest document state', () => {
  const source = new Y.Doc();
  source.getText('content').insert(0, 'base');
  const baseState = Y.encodeStateAsUpdate(source);
  const baseVector = Y.encodeStateVector(source);
  source.getText('content').insert(4, ' + update');
  const incrementalUpdate = Y.encodeStateAsUpdate(source, baseVector);

  const result = mergeYjsState({
    baseState: toBase64(baseState),
    updates: [toBase64(incrementalUpdate)],
  });

  assert.equal(readText(result.mergedState), 'base + update');
});

test('invalid base64 is rejected before it reaches Yjs', () => {
  assert.throws(
    () => mergeYjsState({ baseState: 'not base64!', updates: [] }),
    InvalidMergeRequestError,
  );
});

function toBase64(update: Uint8Array): string {
  return Buffer.from(update).toString('base64');
}

function readText(mergedState: string): string {
  const document = new Y.Doc();
  Y.applyUpdate(document, Buffer.from(mergedState, 'base64'));
  return document.getText('content').toString();
}
