import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';

import {
  InvalidMergeRequestError,
  mergeYjsState,
  type YjsMergeRequest,
} from './merge.js';

const MERGE_PATH = '/internal/yjs/merge';
const MAX_BODY_BYTES = 16 * 1024 * 1024;

export function createMergeServer(): Server {
  return createServer(async (request, response) => {
    try {
      await handleRequest(request, response);
    } catch (error) {
      writeJson(response, 500, { error: 'internal merge service error' });
      console.error('Unexpected merge service error', error);
    }
  });
}

async function handleRequest(request: IncomingMessage, response: ServerResponse): Promise<void> {
  if (request.method !== 'POST' || request.url !== MERGE_PATH) {
    writeJson(response, 404, { error: 'not found' });
    return;
  }

  try {
    const body = await readJsonBody(request);
    const result = mergeYjsState(body);
    writeJson(response, 200, result);
  } catch (error) {
    if (error instanceof InvalidMergeRequestError) {
      writeJson(response, 400, { error: error.message });
      return;
    }
    throw error;
  }
}

async function readJsonBody(request: IncomingMessage): Promise<YjsMergeRequest> {
  const chunks: Buffer[] = [];
  let receivedBytes = 0;

  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    receivedBytes += buffer.length;
    if (receivedBytes > MAX_BODY_BYTES) {
      throw new InvalidMergeRequestError(`request body exceeds ${MAX_BODY_BYTES} bytes`);
    }
    chunks.push(buffer);
  }

  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8')) as YjsMergeRequest;
  } catch {
    throw new InvalidMergeRequestError('request body must be valid JSON');
  }
}

function writeJson(response: ServerResponse, statusCode: number, body: unknown): void {
  response.writeHead(statusCode, { 'content-type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(body));
}

if (process.argv[1] !== undefined && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const server = createMergeServer();
  server.listen(3100, '0.0.0.0', () => {
    console.info('yjs-merge-service listening on port 3100');
  });
}
