#!/usr/bin/env python3
"""Build the offline, single-file MySQL bootstrap. No database connection is made."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SQL = ROOT / "ruoyi-ai/docs/script/sql"
OUTPUT = SQL / "ruoyi-ai.sql"
MANIFEST = SQL / "bootstrap/manifest.json"

# These changes are already represented in the reviewed baseline schema/seeds.
# Keep every migration classified: an unknown new migration fails the build.
BASELINE = {
    "update/2026-06-15-trace.sql",
    "update/2026-07-20-knowledge-fragment-fid.sql",
    "update/2026-07-21-chat-provider-icon-length.sql",
    "update/2026-07-21-sys-config-node-template.sql",
    "update/2026-07-29-sys-config-node-template-all.sql",
    "update/2026-07-29-zhipu-web-search-node.sql",
    "update/2026-08-30-mcp-market-tool-tenant.sql",
    "update/2026-09-01-sys-url.sql",
    # All four columns are in the baseline. Deliberately omit the external
    # paid-video model example; operators provision their own model accounts.
    "update/2026-09-30-script-refinement-seedance-25.sql",
}
MIGRATIONS = [
    "update/2026-07-24-register-default-role-and-missing-menus.sql",
    "lawyer-daily-usage.sql",
    "lawyer-message-usage.sql",
    "lawyer-message-usage-default.sql",
    "lawyer-legal-metadata.sql",
    "lawyer-cases.sql",
    "lawyer-work-time.sql",
    "lawyer-billing.sql",
    "lawyer-phase3-review-prompt.sql",
    "contract-review-upload-permission.sql",
    "update/2026-09-11-knowledge-retrieval-node.sql",
    "update/2026-09-30-short-drama-visual-asset.sql",
    "update/2026-10-01-short-drama-skill-bindings.sql",
    "update/2026-10-02-chat-model-sort-order.sql",
    "update/2026-10-02-pc-login-idle-timeout.sql",
    "update/2026-10-03-short-drama-flash-default.sql",
    "update/2026-10-03-short-drama-original-idea.sql",
    "update/2026-10-15-yulvxing-branding.sql",
    "update/2026-10-17-case-tasks.sql",
    "update/2026-10-17-legal-citation-source.sql",
    "update/2026-10-17-legal-opinion.sql",
    "update/2026-10-17-legal-search-report.sql",
    "update/2026-10-18-contract-review-issues.sql",
    "update/2026-10-18-personal-reminders.sql",
    "update/2026-10-19-admin-menu-groups.sql",
    "update/2026-10-19-hide-tool-workflow-task-menus.sql",
    "update/2026-10-19-lawyer-menu.sql",
    "update/2026-10-19-session-binding.sql",
    "update/2026-10-19-legal-doc-templates.sql",
    "update/2026-10-20-template-management.sql",
    "update/2026-10-20-template-files.sql",
    "update/2026-10-21-dossier-materials.sql",
    "update/2026-10-21-bank-review.sql",
    "update/2026-10-22-dossier-claims.sql",
    "update/2026-10-22-dossier-events.sql",
    "update/2026-10-26-template-field-tasks.sql",
    "update/2026-10-26-template-snapshots.sql",
    "update/2026-10-27-template-type-decisions.sql",
    "update/2026-10-30-open-source-missing-tables.sql",
]


def statements(text: str) -> list[str]:
    """Split ordinary MySQL SQL without splitting strings or keeping comments.

    Bootstrap inputs intentionally contain no DELIMITER directives/routines.
    Never use a semicolon regex against prompts or JSON values.
    """
    result, buf = [], []
    quote = None
    i = 0
    while i < len(text):
        c = text[i]
        if quote:
            buf.append(c)
            if c == "\\" and i + 1 < len(text):
                i += 1
                buf.append(text[i])
            elif c == quote:
                if i + 1 < len(text) and text[i + 1] == quote:
                    i += 1
                    buf.append(text[i])
                else:
                    quote = None
        elif c in "'\"`":
            quote = c
            buf.append(c)
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            if end < 0:
                raise ValueError("Unterminated SQL block comment")
            i = end + 1
            buf.append(" ")
        elif c == "#" or (text.startswith("--", i) and
                           (i + 2 == len(text) or text[i + 2].isspace())):
            end = text.find("\n", i)
            i = len(text) if end < 0 else end
            buf.append("\n")
        elif c == ";":
            if "".join(buf).strip():
                result.append("".join(buf).strip() + ";")
            buf = []
        else:
            buf.append(c)
        i += 1
    if quote:
        raise ValueError("Unterminated SQL string")
    if "".join(buf).strip():
        raise ValueError("SQL input must end in a semicolon")
    return result


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def build() -> tuple[bytes, bytes]:
    updates = {p.relative_to(SQL).as_posix() for p in (SQL / "update").glob("*.sql")}
    rollback = {p for p in updates if p.endswith("-rollback.sql")}
    classified = BASELINE | set(MIGRATIONS) | rollback
    root_patches = {p.name for p in SQL.glob("*.sql")} - {"ruoyi-ai.sql", "snail_job_mysql.sql"}
    unknown = (updates | root_patches) - classified
    if unknown:
        raise ValueError(f"Unclassified migrations: {sorted(unknown)}")
    if len(MIGRATIONS) != len(set(MIGRATIONS)) or BASELINE & set(MIGRATIONS):
        raise ValueError("Migration order contains duplicate classifications")

    header = """-- 与律行 / YuLvXing — fresh installation ONLY / 仅限全新安装
-- Generated by: python scripts/open_source/build_init_sql.py
-- MySQL >= 8.0.30, utf8mb4. Execute this entire file in ONE connection.
-- Creates BOTH `ruoyi-ai-lawyer` and `snail_job`; STOP ON THE FIRST ERROR.
-- Both names must be absent. Never use --force or run against an existing deployment.
-- No DROP DATABASE/TABLE, no production data, no external SOURCE/file dependency.
-- DDL auto-commits. A failure is not transactionally rolled back; see SQL README.
-- Admin is LOCKED unless an installer supplies their own BCrypt hash below.
-- Use scripts/open_source/configure_admin.py to prepare a local copy securely.
-- Models/agents start DISABLED. Supply your own model and storage credentials.
-- Upstream copyright and license notices: repository LICENSE and NOTICE.

SET NAMES utf8mb4;
SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
SET @YULVXING_ADMIN_BCRYPT = NULL; -- INSTALLER_BCRYPT_HASH
CREATE DATABASE `ruoyi-ai-lawyer` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE `snail_job` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `ruoyi-ai-lawyer`;
"""
    inputs = ["bootstrap/schema.sql", "bootstrap/seed.sql", *MIGRATIONS]
    sections, hashes = [header], {}
    for name in inputs:
        data = (SQL / name).read_text(encoding="utf-8-sig").replace("\r\n", "\n").encode("utf-8")
        hashes[name] = sha(data)
        body = data.decode("utf-8-sig").strip()
        if re.search(r"(?im)^\s*(?:DROP\s+(?:DATABASE|TABLE)|SOURCE\b|USE\b|CREATE\s+DATABASE|DELIMITER\b)", body):
            raise ValueError(f"Unexpected destructive, database-switch or client directive in {name}")
        statements(body)  # Check quote/comment boundaries before distributing.
        sections.append(f"\n-- BEGIN SOURCE: {name}\n-- SHA-256: {hashes[name]}\n{body}\n-- END SOURCE: {name}\n")

    sections.append("""
-- Safe publication defaults; configure and enable each assistant explicitly.
UPDATE agent_info SET status='1' WHERE tenant_id=0;
UPDATE chat_provider SET status='1' WHERE tenant_id=0;
UPDATE chat_model SET model_show='N', api_key='' WHERE tenant_id=0;
UPDATE sys_config SET config_value='false' WHERE tenant_id='000000' AND config_key='sys.account.registerUser';
USE `snail_job`;
""")
    name = "bootstrap/snail-schema.sql"
    data = (SQL / name).read_text(encoding="utf-8-sig").replace("\r\n", "\n").encode("utf-8")
    hashes[name] = sha(data)
    body = data.decode("utf-8-sig").strip()
    if any(not re.match(r"CREATE TABLE\b", s, re.I) for s in statements(body)):
        raise ValueError("Scheduler baseline must contain table definitions only")
    sections.append(f"\n-- BEGIN SOURCE: {name}\n-- SHA-256: {hashes[name]}\n{body}\n-- END SOURCE: {name}\n")
    sections.append("""
INSERT INTO sj_namespace(id,name,unique_id,deleted) VALUES
 (1,'Default','764d604ec6fc45f68cd92514c40e9e1a',0),
 (2,'Development','dev',0),(3,'Production','prod',0);
-- No scheduler users, access tokens, running jobs, or real business records.
-- SnailJob is disabled in the application by default; provision it separately if needed.
USE `ruoyi-ai-lawyer`;
SELECT 'YULVXING_DATABASE_INITIALIZATION_COMPLETE' AS result;
""")
    output = "\n".join(sections).encode("utf-8")
    manifest = {
        "format": 1,
        "generated_file": "ruoyi-ai.sql",
        "mysql_minimum": "8.0.30",
        "databases": ["ruoyi-ai-lawyer", "snail_job"],
        "input_sha256": hashes,
        "included_migrations_in_order": MIGRATIONS,
        "represented_in_baseline": sorted(BASELINE),
        "baseline_exceptions": {
            "update/2026-09-30-script-refinement-seedance-25.sql":
                "DDL merged; optional external paid-video example model deliberately omitted."
        },
        "excluded_rollback_scripts": sorted(rollback),
        "output_sha256": sha(output),
    }
    return output, (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if generated files are stale")
    args = parser.parse_args()
    output, manifest = build()
    for target, data in [(OUTPUT, output), (MANIFEST, manifest)]:
        if args.check:
            if not target.exists() or target.read_bytes() != data:
                raise SystemExit(f"FAIL: stale generated file: {target.relative_to(ROOT)}")
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    print(f"PASS: {'checked' if args.check else 'generated'} {OUTPUT.relative_to(ROOT)} ({len(output)} bytes)")


if __name__ == "__main__":
    main()
