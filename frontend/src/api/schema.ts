import data from './contract-data.json' with { type: 'json' };
import { ApiError } from './errors';

export interface Schema {
  $ref?: string;
  type?: string | string[];
  properties?: Record<string, Schema>;
  required?: string[];
  additionalProperties?: boolean;
  enum?: unknown[];
  const?: unknown;
  oneOf?: Schema[];
  anyOf?: Schema[];
  allOf?: Schema[];
  if?: Schema;
  then?: Schema;
  else?: Schema;
  items?: Schema;
  minimum?: number;
  maximum?: number;
  minLength?: number;
  maxLength?: number;
  minItems?: number;
  maxItems?: number;
  uniqueItems?: boolean;
  pattern?: string;
  format?: string;
  default?: unknown;
  description?: string;
}
export interface Parameter {
  name: string;
  in: string;
  required: boolean;
  schema: Schema;
}
export interface Operation {
  path: string;
  method: string;
  title: string;
  tag: string;
  description: string;
  parameters: Parameter[];
  body: Schema | null;
  multipart: boolean;
  response?: Schema | null;
  status: number;
  idempotent: boolean;
}
export const schemas = data.schemas as unknown as Record<string, Schema>;
export const operations = data.operations as unknown as Record<string, Operation>;
export function resolveSchema(schema: Schema): Schema {
  return schema.$ref ? resolveSchema(schemas[schema.$ref.split('/').at(-1)!]!) : schema;
}
export function displaySchema(schema: Schema): Schema {
  const value = resolveSchema(schema);
  return value.anyOf?.find((s) => resolveSchema(s).type !== 'null')
    ? displaySchema(value.anyOf.find((s) => resolveSchema(s).type !== 'null')!)
    : value;
}
const equal = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);
export function schemaIssues(schema: Schema, value: unknown, path = '请求'): string[] {
  const s = resolveSchema(schema);
  const issues: string[] = [];
  const check = (condition: boolean, message: string) => {
    if (!condition) issues.push(`${path}：${message}`);
  };
  if ('const' in s) check(equal(value, s.const), '取值不符合固定约定');
  if (s.enum)
    check(
      s.enum.some((v) => equal(v, value)),
      '请选择允许的选项',
    );
  if (s.oneOf)
    check(
      s.oneOf.filter((x) => !schemaIssues(x, value, path).length).length === 1,
      '输入方式必须且只能匹配一种',
    );
  if (s.anyOf)
    check(
      s.anyOf.some((x) => !schemaIssues(x, value, path).length),
      '输入类型或必填项不正确',
    );
  if (s.allOf) s.allOf.forEach((x) => issues.push(...schemaIssues(x, value, path)));
  if (s.if) {
    const branch = schemaIssues(s.if, value, path).length === 0 ? s.then : s.else;
    if (branch) issues.push(...schemaIssues(branch, value, path));
  }
  const record = typeof value === 'object' && value !== null && !Array.isArray(value);
  if (s.required && record)
    s.required.forEach((key) => check(key in (value as object), `${key} 必填`));
  if (s.type) {
    const types = Array.isArray(s.type) ? s.type : [s.type];
    const matches = types.some((t) =>
      t === 'null'
        ? value === null
        : t === 'object'
          ? record
          : t === 'array'
            ? Array.isArray(value)
            : t === 'integer'
              ? Number.isInteger(value)
              : t === 'number'
                ? typeof value === 'number' && Number.isFinite(value)
                : typeof value === t,
    );
    check(matches, '字段类型不正确');
    if (!matches) return issues;
  }
  if (record) {
    const v = value as Record<string, unknown>;
    if (s.additionalProperties === false)
      Object.keys(v).forEach((key) => check(key in (s.properties || {}), `不允许字段 ${key}`));
    Object.entries(s.properties || {}).forEach(([key, sub]) => {
      if (key in v) issues.push(...schemaIssues(sub, v[key], `${path}.${key}`));
    });
  }
  if (typeof value === 'string') {
    check(s.minLength === undefined || value.length >= s.minLength, '内容太短');
    check(s.maxLength === undefined || value.length <= s.maxLength, '内容太长');
    check(!s.pattern || new RegExp(s.pattern).test(value), '格式不正确');
    if (s.format === 'uuid')
      check(
        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value),
        '请选择有效的关联记录',
      );
    if (s.format === 'date') {
      const date = new Date(`${value}T00:00:00Z`);
      check(
        /^\d{4}-\d{2}-\d{2}$/.test(value) &&
          Number.isFinite(date.getTime()) &&
          date.toISOString().slice(0, 10) === value,
        '日期无效',
      );
    }
    if (s.format === 'date-time') check(Number.isFinite(Date.parse(value)), '时间无效');
    if (s.format === 'uri') {
      try {
        new URL(value);
      } catch {
        issues.push(`${path}：链接无效`);
      }
    }
  }
  if (typeof value === 'number') {
    check(s.minimum === undefined || value >= s.minimum, `不能小于 ${s.minimum}`);
    check(s.maximum === undefined || value <= s.maximum, `不能大于 ${s.maximum}`);
  }
  if (Array.isArray(value)) {
    check(s.minItems === undefined || value.length >= s.minItems, `至少选择 ${s.minItems} 项`);
    check(s.maxItems === undefined || value.length <= s.maxItems, `最多选择 ${s.maxItems} 项`);
    if (s.uniqueItems)
      check(new Set(value.map((v) => JSON.stringify(v))).size === value.length, '不能重复选择');
    if (s.items)
      value.forEach((v, i) => issues.push(...schemaIssues(s.items!, v, `${path}[${i}]`)));
  }
  return issues;
}
export function assertSchema(schema: Schema, value: unknown, response = false): void {
  const issues = schemaIssues(schema, value);
  if (issues.length)
    throw new ApiError(
      response ? `服务返回的数据不符合约定：${issues[0]}` : issues[0]!,
      response ? 'INVALID_RESPONSE' : 'VALIDATION_FAILED',
    );
}
