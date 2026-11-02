#!/usr/bin/env python3
"""按指定任务名复制三份设计记录模板；不会修改现有项目业务代码。"""
from pathlib import Path
import argparse, shutil, sys
parser=argparse.ArgumentParser()
parser.add_argument('--name',required=True,help='task slug, e.g. evidence-review')
parser.add_argument('--out',required=True,help='output directory')
args=parser.parse_args()
if not args.name.replace('-','').replace('_','').isalnum() or '/' in args.name or '\\' in args.name:
    parser.error('name must contain only letters, numbers, - or _')
root=Path(__file__).resolve().parents[1]
dst=Path(args.out).resolve()/args.name
if dst.exists():
    sys.exit(f'Output exists; refusing overwrite: {dst}')
dst.mkdir(parents=True)
for f in ('DESIGN-DECISION.md','INTERACTION-CONTRACT.md','UX-REVIEW-REPORT.md'):
    shutil.copy2(root/'templates'/f,dst/f)
print('Created (unfilled, not validated):',dst)
