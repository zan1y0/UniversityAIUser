"""同步唯一 OpenAPI 契约并校验引用、示例及实际控制器路由。

依赖：pip install PyYAML jsonschema openapi-spec-validator
用法：python scripts/sync-contract.py [--check]
"""
import argparse
import copy
import hashlib
import json
from collections import Counter
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.tools/contract-python'))
import yaml
from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import validate_spec


def main():
    check = argparse.ArgumentParser()
    check.add_argument('--check', action='store_true')
    args = check.parse_args()
    source = ROOT / 'contracts/openapi.json'
    spec = json.loads(source.read_text(encoding='utf-8'))
    validate_spec(spec)
    schemas = spec['components']['schemas']
    examples = 0
    ids = set()
    routes = set()
    modules = Counter()

    def validate(schema, value):
        root = {'$defs': schemas, **copy.deepcopy(schema)}
        root = json.loads(json.dumps(root).replace('#/components/schemas/', '#/$defs/'))
        Draft202012Validator(root, format_checker=FormatChecker()).validate(value)

    def visit(node):
        nonlocal examples
        if isinstance(node, dict):
            if '$ref' in node:
                ref = node['$ref']
                assert ref.startswith('#/'), f'External reference: {ref}'
                target = spec
                for part in ref[2:].split('/'):
                    target = target[part.replace('~1', '/').replace('~0', '~')]
            if 'schema' in node and 'example' in node:
                validate(node['schema'], node['example'])
                examples += 1
            if 'type' in node or '$ref' in node:
                for value in node.get('examples', []) if isinstance(node.get('examples'), list) else []:
                    validate(node, value)
                    examples += 1
            for child in node.values():
                visit(child)
        elif isinstance(node, list):
            for child in node:
                visit(child)

    visit(spec)
    for path, methods in spec['paths'].items():
        for method, operation in methods.items():
            opid = operation['operationId']
            assert opid not in ids, f'Duplicate operationId: {opid}'
            ids.add(opid)
            routes.add((method.lower(), path))
            modules[operation['tags'][0]] += 1
            if method not in ('get', 'head'):
                assert any(p['name'] == 'X-CSRF-Token' and p['required'] for p in operation['parameters']), path

    implemented = set()
    for controller in (ROOT / 'src/main/java/com/kaoyan/ai/api/controller').glob('*.java'):
        code = controller.read_text(encoding='utf-8')
        prefix = re.search(r'@RequestMapping\("([^"]+)"\)', code)
        for method, path in re.findall(r'@(Get|Post|Put|Patch|Delete)Mapping\("([^"]+)"\)', code):
            implemented.add((method.lower(), (prefix.group(1) if prefix else '') + path))
    assert routes == implemented, f'Route differences: missing={implemented-routes}, obsolete={routes-implemented}'
    for model in schemas.values():
        Draft202012Validator.check_schema(model)

    negatives = [
        ({'type': 'string', 'format': 'uuid'}, '123'),
        ({'$ref': '#/components/schemas/LoginInput'}, {'loginName': 'student', 'password': 'Example123!', 'userId': '123'}),
        ({'$ref': '#/components/schemas/AuthRegisterInput'}, {'loginName': 'student', 'password': 'lettersOnly', 'displayName': '同学'}),
        ({'type': 'number', 'minimum': 0, 'maximum': 1}, 1.1),
        ({'$ref': '#/components/schemas/AvatarInteractionInput'}, {'contextId': '11111111-1111-4111-8111-111111111111', 'text': 'hello', 'fileId': '11111111-1111-4111-8111-111111111111'}),
    ]
    from jsonschema.exceptions import ValidationError
    for schema, value in negatives:
        try:
            validate(schema, value)
        except ValidationError:
            continue
        raise AssertionError(f'Negative case accepted: {value}')

    report = {
        'version': spec['info']['version'], 'paths': len(spec['paths']),
        'operations': len(ids), 'schemas': len(schemas), 'examplesValidated': examples,
        'negativeCases': len(negatives), 'businessModules': sum(k.startswith('M') for k in modules),
        'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'status': 'contract_validated_routes_matched', 'moduleOperations': dict(modules),
        'note': '路由一致和文档校验不代表所有业务、外部模型及 MySQL 已通过真实联调。',
    }
    yaml_text = yaml.safe_dump(spec, allow_unicode=True, sort_keys=False, width=80)
    outputs = {
        'contracts/openapi.yaml': yaml_text,
        'frontend/contracts/openapi.json': source.read_text(encoding='utf-8'),
        'frontend/contracts/openapi.yaml': yaml_text,
        'src/main/resources/contract/openapi.json': source.read_text(encoding='utf-8'),
        'src/main/resources/static/contract/openapi.json': source.read_text(encoding='utf-8'),
        'contracts/validation-report.json': json.dumps(report, ensure_ascii=False, indent=2) + '\n',
        'frontend/contracts/validation-report.json': json.dumps(report, ensure_ascii=False, indent=2) + '\n',
    }
    for filename, content in outputs.items():
        path = ROOT / filename
        if args.check:
            assert path.read_text(encoding='utf-8') == content, f'Outdated contract copy: {filename}'
        else:
            path.write_text(content, encoding='utf-8')
    if args.check:
        generated = json.loads((ROOT / 'frontend/src/api/contract-data.json').read_text(encoding='utf-8'))
        assert generated['version'] == spec['info']['version'], 'Outdated frontend metadata version'
        assert generated['schemas'] == schemas, 'Outdated frontend schemas'
        assert set(generated['operations']) == ids, 'Outdated frontend operation list'
        for path, methods in spec['paths'].items():
            for method, operation in methods.items():
                actual = generated['operations'][operation['operationId']]
                assert actual['path'] == path and actual['method'] == method.upper(), path
                assert actual['parameters'] == operation['parameters'], path
                body = operation.get('requestBody', {}).get('content', {})
                assert actual['body'] == (next(iter(body.values()))['schema'] if body else None), path
                success = next(r for code, r in operation['responses'].items() if code.startswith('2'))
                assert actual['response'] == success.get('content', {}).get('application/json', {}).get('schema'), path
        types = (ROOT / 'frontend/src/api/generated.ts').read_text(encoding='utf-8')
        assert re.search(r'CONTRACT_VERSION\s*=\s*[\"\']' + re.escape(spec['info']['version']), types), 'Outdated TypeScript version'
        for opid in ids:
            assert re.search(r'\b' + opid + r':\s*\{', types), f'Missing TypeScript operation: {opid}'
    print(f"Contract {report['version']}: {len(ids)} operations, {examples} examples; routes and copies verified")


if __name__ == '__main__':
    main()
