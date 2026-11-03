from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1]
spec=json.loads((root/'contracts/openapi.json').read_text(encoding='utf-8'))
schemas=spec['components']['schemas']
def ts(s):
 if '$ref' in s:return s['$ref'].split('/')[-1]
 if 'const' in s:return json.dumps(s['const'],ensure_ascii=False)
 if 'enum' in s:return ' | '.join(json.dumps(v,ensure_ascii=False) for v in s['enum'])
 if 'oneOf' in s:return ' | '.join('('+ts(x)+')' for x in s['oneOf'])
 if 'anyOf' in s and s.get('type')!='object':return ' | '.join('('+ts(x)+')' for x in s['anyOf'])
 t=s.get('type')
 if isinstance(t,list):return ' | '.join(ts({**s,'type':x}) for x in t)
 if t=='object':
  return '{ '+ '; '.join(json.dumps(k)+('' if k in s.get('required',[]) else '?')+': '+ts(v) for k,v in s.get('properties',{}).items())+' }'
 if t=='array':return '('+ts(s['items'])+')[]'
 return {'string':'string','integer':'number','number':'number','boolean':'boolean','null':'null'}.get(t,'unknown')

lines=['// 从 contracts/openapi.json 生成；禁止手工修改。','export const CONTRACT_VERSION = "1.0.0";']
for n,s in schemas.items():lines.append('export type '+n+' = '+ts(s)+';')
ops={}; defs=[]
for path,methods in spec['paths'].items():
 for method,o in methods.items():
  ps=o.get('parameters',[]); pb=o.get('requestBody',{}).get('content',{})
  response=next((r for code,r in o['responses'].items() if code.startswith('2')), {})
  media=response.get('content',{}).get('application/json',{})
  out='undefined'
  if media:
   resp=schemas[media['schema']['$ref'].split('/')[-1]]
   out=ts(resp['properties']['data'])
  elif path.endswith('/events'):out='EventPayload'
  elif method=='get' and path.endswith('/files/{id}'):out='Blob'
  prop={}
  for where in ['path','query']:
   pars=[p for p in ps if p['in']==where]
   if pars:prop[where]=ts({'type':'object','properties':{p['name']:p['schema'] for p in pars},'required':[p['name'] for p in pars if p['required']]})
  if pb:prop['body']=ts(next(iter(pb.values()))['schema']) if 'multipart/form-data' not in pb else 'FormData'
  inp='{ '+ '; '.join(k+('' if k=='path' or k=='body' or any(p['in']=='query' and p['required'] for p in ps) else '?')+': '+v for k,v in prop.items())+' }'
  defs.append(f'  {o["operationId"]}: {{ input: {inp}; output: {out} }};')
  ops[o['operationId']]={'path':path,'method':method.upper(),'title':o['summary'],'tag':o['tags'][0],'description':o['description'],'parameters':ps,'body':next(iter(pb.values()))['schema'] if pb else None,'multipart':'multipart/form-data' in pb,'response':media.get('schema'),'status':int(next(c for c in o['responses'] if c.startswith('2'))),'idempotent':any(p['name']=='Idempotency-Key' for p in ps)}
lines+=['export interface Operations {',*defs,'}']
(root/'src/api/generated.ts').write_text('\n'.join(lines)+'\n',encoding='utf-8')
(root/'src/api/contract-data.json').write_text(json.dumps({'version':spec['info']['version'],'schemas':schemas,'operations':ops},ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print('Generated',len(ops),'typed operations and',len(schemas),'schemas')
