import * as Y from 'yjs';

export interface YjsMergeRequest {
  baseState: string | null;
  updates: string[];
}

export interface YjsMergeResponse {
  mergedState: string;
}

export class InvalidMergeRequestError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InvalidMergeRequestError';
  }
}

export function mergeYjsState(request: YjsMergeRequest): YjsMergeResponse {
  validateRequest(request);

  const document = new Y.Doc();
  if (request.baseState !== null) {
    Y.applyUpdate(document, decodeBase64(request.baseState, 'baseState'));
  }

  for (const [index, update] of request.updates.entries()) {
    Y.applyUpdate(document, decodeBase64(update, `updates[${index}]`));
  }

  return {
    mergedState: Buffer.from(Y.encodeStateAsUpdate(document)).toString('base64'),
  };
}

function validateRequest(request: YjsMergeRequest): void {
  if (request === null || typeof request !== 'object') {
    throw new InvalidMergeRequestError('request body must be a JSON object');
  }
  if (request.baseState !== null && typeof request.baseState !== 'string') {
    throw new InvalidMergeRequestError('baseState must be a base64 string or null');
  }
  if (!Array.isArray(request.updates) || request.updates.some((update) => typeof update !== 'string')) {
    throw new InvalidMergeRequestError('updates must be an array of base64 strings');
  }
}

function decodeBase64(value: string, field: string): Uint8Array {
  if (!isCanonicalBase64(value)) {
    throw new InvalidMergeRequestError(`${field} must be valid base64`);
  }

  return new Uint8Array(Buffer.from(value, 'base64'));
}

function isCanonicalBase64(value: string): boolean {
  if (value.length === 0) {
    return true;
  }
  if (value.length % 4 !== 0 || !/^[A-Za-z0-9+/]*={0,2}$/.test(value)) {
    return false;
  }

  return Buffer.from(value, 'base64').toString('base64') === value;
}
