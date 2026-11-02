#!/usr/bin/env python3
"""Create a local bootstrap copy with a private, installer-chosen password."""
import argparse
import getpass
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MARKER = "SET @YULVXING_ADMIN_BCRYPT = NULL; -- INSTALLER_BCRYPT_HASH"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "tmp/yulvxing-init.local.sql")
    args = parser.parse_args()
    try:
        import bcrypt
    except ImportError:
        raise SystemExit("Install the optional password preparation tool: python -m pip install bcrypt")
    password = getpass.getpass("Choose admin password (12+ characters; input hidden): ")
    if len(password) < 12 or len(password.encode("utf-8")) > 72:
        raise SystemExit("Password must have at least 12 characters and no more than 72 UTF-8 bytes.")
    if password != getpass.getpass("Confirm password: "):
        raise SystemExit("Passwords do not match; no file was written.")
    hashed = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=12)).decode("ascii")
    text = (ROOT / "ruoyi-ai/docs/script/sql/ruoyi-ai.sql").read_text(encoding="utf-8")
    if text.count(MARKER) != 1:
        raise SystemExit("Unexpected bootstrap format; no file was written.")
    output = args.output.resolve()
    if output == (ROOT / "ruoyi-ai/docs/script/sql/ruoyi-ai.sql").resolve():
        raise SystemExit("Use a private local output file, not the distributed bootstrap.")
    output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation avoids overwriting an earlier local installation copy.
    descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
        stream.write(text.replace(MARKER, f"SET @YULVXING_ADMIN_BCRYPT = '{hashed}'; -- PRIVATE LOCAL COPY"))
    print(f"Created {output}. Execute this ONE SQL file; do not commit or share it.")


if __name__ == "__main__":
    main()
