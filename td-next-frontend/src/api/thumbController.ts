// @ts-ignore
/* eslint-disable */
import request from '@/libs/request';

/** doThumb POST /api/thumb/do */
export async function doThumbUsingPost(body: API.DoThumbRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean_>('/api/thumb/do', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** undoThumb POST /api/thumb/undo */
export async function undoThumbUsingPost(
  body: API.DoThumbRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/thumb/undo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}
