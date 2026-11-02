#!/usr/bin/env python3
"""静态验证规范包结构、指令关键点与eval manifest；不验证Codex实际行为。Python 3.9+，无外部依赖。"""
from pathlib import Path
import json, re, sys
ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    'AGENTS.md','.agents/skills/yulvxing-frontend/SKILL.md',
    'docs/frontend/README.md',
    *[f'docs/frontend/{i:02d}-{name}.md' for i,name in [
        (1,'DESIGN-SYSTEM'),(2,'UX-INTERACTION'),(3,'FRONTEND-ENGINEERING'),
        (4,'QUALITY-GATES'),(5,'AUDIT-CHECKLIST'),(6,'PAGE-ARCHETYPES'),
        (7,'INTERACTION-CONTRACTS'),(8,'DESIGN-DECISION-PROTOCOL'),
        (9,'ACCEPTANCE-ORACLES'),(10,'VISUAL-REGRESSION'),
        (11,'O001-PATTERNS'),(12,'SKILL-EVALS-AND-GOVERNANCE')]],
    'templates/DESIGN-DECISION.md','templates/INTERACTION-CONTRACT.md',
    'templates/UX-REVIEW-REPORT.md','templates/FRONTEND-TASK-PROMPT.md',
    'templates/NEW-FEATURE-BRIEF.md',
    'evals/scenarios.json','examples/design-tokens.sample.css',
    'scripts/validate_package.py','scripts/scaffold_design_report.py',
    'docs/frontend/00-PROJECT-ADAPTER.md','docs/frontend/INSTALL-V2.md',
    'docs/frontend/PACKAGE-V2-README.md','docs/frontend/CHANGELOG.md',
]
problems=[]
for p in REQUIRED:
    f=ROOT/p
    if not f.is_file() or f.stat().st_size<50: problems.append(f'missing/empty: {p}')
skill=ROOT/'.agents/skills/yulvxing-frontend/SKILL.md'
if skill.exists():
    s=skill.read_text('utf-8')
    if not re.match(r'\A---\nname:\s*yulvxing-frontend\ndescription:\s*.+\n---\n',s):
        problems.append('SKILL.md frontmatter must contain name and description')
    for phrase in ['P0','P1','P2','P3','P4','P5','P6','S 简单','M 常规','L 复杂']:
        if phrase not in s: problems.append(f'SKILL.md missing workflow marker {phrase}')
    if 'V2.0' not in s: problems.append('SKILL.md missing V2.0 version marker')
    if len(re.findall(r'^name:',s,re.M)) != 1:
        problems.append('SKILL.md must declare exactly one frontmatter name')
try:
    d=json.loads((ROOT/'evals/scenarios.json').read_text('utf-8'))
    if d.get('version') != '2.0.0': problems.append('eval version must be 2.0.0')
    seen=set()
    for case in d['scenarios']:
        for field in ('id','prompt','expected_mode','must','must_not'):
            if field not in case: problems.append(f'eval {case.get("id","?")} missing {field}')
        if case['id'] in seen: problems.append(f'duplicate eval {case["id"]}')
        seen.add(case['id'])
        if case['expected_mode'] not in ('S','M','L','NONE'): problems.append(f'bad mode {case["id"]}')
        for field in ('must','must_not'):
            if not isinstance(case.get(field),list) or not all(isinstance(item,str) and item for item in case[field]):
                problems.append(f'eval {case["id"]} {field} must be a string list')
    if len(seen)<10: problems.append('fewer than 10 scenarios')
except (OSError,ValueError,KeyError,TypeError) as e: problems.append(f'eval manifest invalid: {e}')
# Validate relative file paths explicitly referenced by filename in the index (not remote URLs).
idx=ROOT/'docs/frontend/README.md'
if idx.exists():
    x=idx.read_text('utf-8')
    for match in re.findall(r'`(\d\d-[A-Z0-9-]+\.md)`',x):
        if not (ROOT/'docs/frontend'/match).exists(): problems.append(f'index link not found: {match}')
# Repository adaptation: validate active package references only, not historical task reports.
# Do not recurse through node_modules, business code, backups or unrelated Skills.
active = [ROOT/p for p in REQUIRED if p.endswith('.md')]
references_checked = 0
for source in active:
    if not source.is_file(): continue
    value = source.read_text('utf-8')
    candidates = re.findall(r'\[[^\]\n]+\]\(([^)\n]+)\)',value)
    candidates += re.findall(r'`([^`\n]+\.(?:md|py|css|json))`',value)
    for raw in candidates:
        target = raw.strip('<>').split('#',1)[0]
        if not target or re.match(r'^[a-zA-Z][a-zA-Z0-9+.-]*:',target): continue
        if any(char in target for char in (' ', '…', '*', '|')): continue
        # These are discovery patterns, not declarations of a root-level file.
        if target == 'AGENTS.override.md': continue
        if target == 'package.json':
            references_checked += 1
            if not all((ROOT/folder/'package.json').is_file() for folder in ('ruoyi-web','ruoyi-admin/apps/web-antd')):
                problems.append('project package.json manifests missing')
            continue
        if re.match(r'^(?:docs|templates|scripts|evals|examples|\.agents)/',target):
            resolved = ROOT/target
        elif '/' not in target and target.endswith('.md'):
            matches = [file for file in [ROOT/'docs/frontend'/target, ROOT/'docs/product/o001'/target, ROOT/'templates'/target,
                       ROOT/'.agents/skills/yulvxing-frontend'/target,ROOT/target] if file.is_file()]
            resolved = matches[0] if len(matches) == 1 else source.parent/target
        else:
            resolved = source.parent/target
        references_checked += 1
        if not resolved.is_file(): problems.append(f'broken reference in {source.relative_to(ROOT)}: {target}')
        try: resolved.resolve().relative_to(ROOT.resolve())
        except ValueError: problems.append(f'reference escapes repository in {source.relative_to(ROOT)}: {target}')
print('STATIC_PACKAGE_CHECK:', 'PASS' if not problems else 'FAIL')
print('required_files:', len(REQUIRED), 'references_checked:', references_checked,
      'eval_scenarios:', len(seen) if 'seen' in locals() else 0)
for issue in problems: print('-',issue)
print('NOTE: static package check is not Codex runtime test, UI test or WCAG certification.')
sys.exit(bool(problems))
