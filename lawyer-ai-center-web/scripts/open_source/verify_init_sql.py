#!/usr/bin/env python3
"""Import the real bootstrap in an explicitly isolated MySQL instance.

Never starts a server or connects to a normal deployment. The server must have
its data directory under this checkout's tmp/ and listen on a non-3306 loopback
port. Only randomly named databases created by this run are removed.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import secrets
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql", required=True, type=Path)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--data-dir", required=True, type=Path)
    parser.add_argument("--report", type=Path, default=ROOT / "tmp/open-source-mysql/validation.json")
    args = parser.parse_args()
    expected = args.data_dir.resolve()
    if not expected.is_relative_to((ROOT / "tmp").resolve()) or args.port == 3306:
        raise SystemExit("Refusing test: use a dedicated tmp/ datadir and a non-3306 port.")
    try:
        import bcrypt
        import pymysql
    except ImportError:
        raise SystemExit("Install verification tools: python -m pip install bcrypt PyMySQL")

    env = dict(os.environ)
    env["MYSQL_TEST_LOGIN_FILE"] = str(ROOT / "tmp/open-source-mysql/no-login-file")
    command = [str(args.mysql.resolve()), "--no-defaults", "--protocol=TCP",
               "--host=127.0.0.1", f"--port={args.port}", "--user=root", "--password=",
               "--connect-timeout=5", "--default-character-set=utf8mb4", "--binary-mode",
               "--raw", "--batch", "--skip-column-names"]

    def run(sql: str) -> subprocess.CompletedProcess:
        return subprocess.run(command, input=sql.encode("utf-8"), capture_output=True,
                              env=env, timeout=180)

    connection = pymysql.connect(host="127.0.0.1", port=args.port, user="root",
                                 password="", charset="utf8mb4", autocommit=True,
                                 connect_timeout=5, read_timeout=30, write_timeout=30,
                                 client_flag=pymysql.constants.CLIENT.MULTI_STATEMENTS)

    def query(sql: str) -> list[list[str]]:
        rows = []
        with connection.cursor() as cursor:
            cursor.execute(sql)
            while True:
                if cursor.description:
                    rows.extend([["NULL" if value is None else str(value) for value in row]
                                 for row in cursor.fetchall()])
                if not cursor.nextset():
                    break
        return rows

    identity = query("SELECT @@datadir,@@port,@@version;")[0]
    actual = Path(identity[0]).resolve()
    if actual != expected or int(identity[1]) != args.port:
        raise SystemExit("Refusing test: server datadir or port does not match the isolated instance.")

    checks: dict[str, str] = {}
    details: dict = {}
    created: list[str] = []
    prefix = "yl_verify_" + secrets.token_hex(6)
    original = (ROOT / "ruoyi-ai/docs/script/sql/ruoyi-ai.sql").read_text(encoding="utf-8")

    def check(name: str, condition: bool) -> None:
        if not condition:
            checks[name] = "FAIL"
            raise AssertionError(name)
        checks[name] = "PASS"
        print("PASS:", name, flush=True)

    def import_variant(text: str, suffix: str) -> tuple[str, str, str]:
        main_db, job_db = f"{prefix}_{suffix}_main", f"{prefix}_{suffix}_jobs"
        check(f"{suffix}: database names absent", not query(
            f"SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('{main_db}','{job_db}');"))
        created.extend([main_db, job_db])
        prepared = text.replace("`ruoyi-ai-lawyer`", f"`{main_db}`").replace("`snail_job`", f"`{job_db}`")
        result = run(prepared)
        # Diagnostics are local and excluded from publication; do not log SQL input.
        log_dir = ROOT / "tmp/open-source-mysql"
        (log_dir / f"{suffix}-stdout.log").write_bytes(result.stdout)
        (log_dir / f"{suffix}-stderr.log").write_bytes(result.stderr)
        check(f"{suffix}: full import exits zero", result.returncode == 0)
        check(f"{suffix}: completion marker", b"YULVXING_DATABASE_INITIALIZATION_COMPLETE" in result.stdout)
        return main_db, job_db, prepared

    def scalar(db: str, sql: str) -> str:
        rows = query(f"USE `{db}`; {sql}")
        if not rows or not rows[0]:
            raise AssertionError(f"Expected scalar result from {db}: {sql}")
        return rows[0][0]

    error = None
    try:
        main_db, job_db, prepared = import_variant(original, "public")
        main_tables = {r[0] for r in query(f"SELECT table_name FROM information_schema.tables WHERE table_schema='{main_db}';")}
        mapped = set()
        for base in [ROOT / "ruoyi-ai/ruoyi-modules", ROOT / "ruoyi-ai/ruoyi-common"]:
            for folder, dirs, files in os.walk(base):
                dirs[:] = [d for d in dirs if d not in {"target", "node_modules", ".git"}]
                for name in files:
                    if name.endswith(".java"):
                        java = (Path(folder) / name).read_text(encoding="utf-8-sig")
                        mapped.update(re.findall(r'@TableName\(\s*(?:value\s*=\s*)?"(\w+)"', java))
        details["mapped_tables"] = len(mapped)
        details["main_tables"] = len(main_tables)
        details["scheduler_tables"] = int(query(
            f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{job_db}';")[0][0])
        check("all named MyBatis entity tables exist", mapped <= main_tables)
        details["missing_mapped_tables"] = sorted(mapped - main_tables)
        check("public admin is locked and has no password", scalar(main_db,
            "SELECT COUNT(*) FROM sys_user WHERE user_id=1 AND status='1' AND password='';") == "1")
        check("only one local bootstrap account", scalar(main_db, "SELECT COUNT(*) FROM sys_user;") == "1")
        check("all four assistants disabled", scalar(main_db,
            "SELECT COUNT(*) FROM agent_info WHERE status='1';") == "4")
        names = {r[0] for r in query(f"SELECT agent_name FROM `{main_db}`.agent_info;")}
        details["assistant_names"] = sorted(names)
        check("Chinese assistant names preserved as UTF-8", names == {"法律咨询助手", "合同审查官", "文书起草助手", "法律意见书助手"})
        check("models hidden and have no API key", scalar(main_db,
            "SELECT COUNT(*) FROM chat_model WHERE model_show<>'N' OR COALESCE(api_key,'')<>'';") == "0")
        check("providers disabled", scalar(main_db, "SELECT COUNT(*) FROM chat_provider WHERE status<>'1';") == "0")
        check("registration disabled", scalar(main_db,
            "SELECT config_value FROM sys_config WHERE tenant_id='000000' AND config_key='sys.account.registerUser';") == "false")
        check("no shared initial user password", scalar(main_db,
            "SELECT config_value='' FROM sys_config WHERE tenant_id='000000' AND config_key='sys.user.initPassword';") == "1")
        check("ordinary role remains self-only", scalar(main_db,
            "SELECT COUNT(*) FROM sys_role WHERE role_key='user' AND data_scope='5';") == "1")
        check("no orphan menu parents", scalar(main_db,
            "SELECT COUNT(*) FROM sys_menu c LEFT JOIN sys_menu p ON p.menu_id=c.parent_id WHERE c.parent_id<>0 AND p.menu_id IS NULL;") == "0")
        check("no orphan role-menu links", scalar(main_db,
            "SELECT COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_role r ON r.role_id=rm.role_id LEFT JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE r.role_id IS NULL OR m.menu_id IS NULL;") == "0")
        check("template types initialized", scalar(main_db, "SELECT COUNT(*) FROM legal_doc_template_type;") == "6")
        business = sorted(t for t in main_tables if t.startswith(("lawyer_case", "lawyer_billing_", "legal_search_report"))
                          and t != "lawyer_billing_plan")
        business += ["chat_session", "chat_message", "knowledge_attach", "knowledge_fragment", "knowledge_info",
                     "sys_oss", "sys_oss_config", "sys_logininfor", "sys_oper_log", "sys_social", "adi_user"]
        # Check all empty tables in one read-only statement rather than launching
        # a separate native client process for every individual COUNT.
        counts = query(f"USE `{main_db}`; " + " UNION ALL ".join(
            f"SELECT '{t}',COUNT(*) FROM `{t}`" for t in business) + ";")
        check("all business-table counts returned", len(counts) == len(business))
        check("business tables, source files and logs contain no runtime records",
              all(value == "0" for _, value in counts))
        details["empty_business_tables_checked"] = len(business)
        check("billing packages not enabled", scalar(main_db,
            "SELECT COUNT(*) FROM lawyer_billing_plan WHERE enabled<>0;") == "0")
        scheduler_counts = query(f"USE `{job_db}`; " + " UNION ALL ".join(
            f"SELECT '{t}',COUNT(*) FROM `{t}`" for t in
            ["sj_system_user", "sj_group_config", "sj_job", "sj_retry", "sj_workflow"]) + ";")
        check("scheduler has no users, tokens or jobs",
              len(scheduler_counts) == 5 and all(value == "0" for _, value in scheduler_counts))

        query(f"UPDATE `{main_db}`.sys_user SET nick_name='repeat-import-sentinel' WHERE user_id=1;")
        repeat = run(prepared)
        check("repeat import fails on existing database", repeat.returncode != 0 and b"ERROR 1007" in repeat.stderr)
        check("repeat import preserves prior row", scalar(main_db,
            "SELECT nick_name FROM sys_user WHERE user_id=1;") == "repeat-import-sentinel")

        # Exercise the actual password-preparation entry point without printing
        # the random in-memory password or the resulting hash.
        spec = importlib.util.spec_from_file_location("configure_admin", ROOT / "scripts/open_source/configure_admin.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        password = secrets.token_urlsafe(24)
        private_file = ROOT / f"tmp/{prefix}.local.sql"
        with patch.object(sys, "argv", ["configure_admin.py", "--output", str(private_file)]), \
             patch.object(module.getpass, "getpass", side_effect=[password, password]):
            module.main()
        private_sql = private_file.read_text(encoding="utf-8")
        check("private SQL does not contain plaintext password", password not in private_sql)
        private_db, _, _ = import_variant(private_sql, "private")
        check("installer-configured admin enabled", scalar(private_db,
            "SELECT status FROM sys_user WHERE user_id=1;") == "0")
        stored_hash = scalar(private_db, "SELECT password FROM sys_user WHERE user_id=1;")
        check("installer password BCrypt verification", bcrypt.checkpw(password.encode(), stored_hash.encode()))
        check("private setup leaves assistants disabled", scalar(private_db,
            "SELECT COUNT(*) FROM agent_info WHERE status='1';") == "4")
    except Exception as exc:
        import traceback
        traceback.print_exc()
        error = type(exc).__name__ + ": " + str(exc)
    finally:
        # All identifiers originate here; the server datadir was verified first.
        for db in reversed(created):
            if not re.fullmatch(re.escape(prefix) + r"_(public|private)_(main|jobs)", db):
                raise RuntimeError("Unexpected cleanup database name")
            query(f"DROP DATABASE IF EXISTS `{db}`;")
        report = {
            "time_utc": datetime.now(timezone.utc).isoformat(),
            "mysql_version": identity[2], "port": args.port,
            "isolated_datadir_verified": True,
            "checks": checks, "details": details,
            "status": "FAIL" if error else "PASS", "error": error,
            "temporary_databases_removed": len(created),
        }
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        connection.close()
    if error:
        raise SystemExit(error)
    print(f"PASS: {len(checks)} checks; evidence: {args.report}")


if __name__ == "__main__":
    main()
