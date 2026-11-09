#!/usr/bin/env python3
"""P5 联调辅助脚本：以只读方式查询下载服务的 SQLite 库（download.db），输出 JSON。

用法：
    python scripts/qa-p5-sqlite-query.py <db-path> "<sql>"

说明：
    - 使用 ``mode=ro`` 打开，绝不会修改库文件；
    - 下载服务的库默认运行在 WAL 模式：查询前请把 ``download.db``、``download.db-wal``、
      ``download.db-shm`` 一起复制到同一临时目录（联调脚本的 Copy-DownloadDb 已处理）；
    - 输出 UTF-8 JSON 数组（中文不转义），列名即结果集列名；
    - 查询无结果时输出 ``[]``；SQL 错误时向 stderr 输出异常并以非 0 退出。
"""

import json
import sqlite3
import sys


def main() -> int:
    if len(sys.argv) < 3:
        print(
            '用法: python qa-p5-sqlite-query.py <db-path> "<sql>"',
            file=sys.stderr,
        )
        return 2

    db_path, sql = sys.argv[1], sys.argv[2]
    try:
        conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    except sqlite3.Error as exc:  # pragma: no cover - 仅排障用
        print(f"打开数据库失败: {exc}", file=sys.stderr)
        return 3

    conn.row_factory = sqlite3.Row
    try:
        rows = [dict(row) for row in conn.execute(sql).fetchall()]
    except sqlite3.Error as exc:
        print(f"执行 SQL 失败: {exc}", file=sys.stderr)
        return 4
    finally:
        conn.close()

    print(json.dumps(rows, ensure_ascii=False, default=str))
    return 0


if __name__ == "__main__":
    sys.exit(main())
