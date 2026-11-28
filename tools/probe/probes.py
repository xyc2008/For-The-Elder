#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""For-The-Elder 文档探针：四类只读检查，结果可机器对账。

为什么这个文件住在 tools/probe/ 而不是 build/：
第 142 轮那套探针（probes.sh / classify3.py / probe*.py）落在 build/r142/ 里，
而 build/ 在 .gitignore 第 4 行上、且每轮收尾都整目录删掉 —— 于是「落盘前跑一趟、
落盘后再跑一趟、两趟相减」这套对账，趟本身跑得出来，差额却永远无法事后复现。
本文件把探针搬进受版本管理的位置，让读数成为可复查的物证。

四个 spec：
  ① tables  Markdown 表格块与数据行的形状（张数 / 行数 / 列数不齐的坏行）
  ② refs    `path:NNN` 形式的代码引用是否指向真实存在的文件与行
  ③ eol     行尾空白与裸 CR
  ④ xref    `<file>.kt:行号` 形式的宣称：按受管文件的最新轮次节把宣称分成
            LIVE（最新节内）/ HIST（更早的节），分别数「指向不存在的文件」
            与「行号越界」

用法：
  python tools/probe/probes.py --label pre  --json build/rNNN/pre.json  > build/rNNN/pre.log
  python tools/probe/probes.py --label post --json build/rNNN/post.json > build/rNNN/post.log
"""

import argparse
import json
import os
import re
import sys

# ---------------------------------------------------------------- 受管文档清单
# 十六份。docs/ 下全部 .md、records/ 下全部 .md，外加门面 README.md。
# 新守卫 ProbeToolGuardTest 会从下面这个数组的字面文本里把清单抠出来，
# 逐条核对文件真存在、并核对没有漏网的 docs/ 或 records/ 下的 .md。
MANAGED_FILES = [
    "docs/00-项目章程与需求计划书.md",
    "docs/01-需求规格说明书.md",
    "docs/02-概要设计说明书.md",
    "docs/03-详细设计说明书.md",
    "docs/04-数据库设计说明书.md",
    "docs/05-接口设计说明书.md",
    "docs/06-测试计划与用例.md",
    "docs/07-风险管理计划.md",
    "docs/08-部署与构建说明.md",
    "docs/09-用户手册.md",
    "records/01-迭代记录.md",
    "records/02-需求变更记录.md",
    "records/03-架构决策记录.md",
    "records/04-质量与验证记录.md",
    "records/CHANGELOG.md",
    "README.md",
]

# 本文件在 <repo>/tools/probe/probes.py ⇒ 仓库根 = 上两级。
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

FENCE_RE = re.compile(r"^\s*(```|~~~)")
CODE_EXT = ("kt", "kts", "java", "xml", "sh", "py", "toml", "json", "properties", "gradle")
CODE_EXT_RE = "|".join(CODE_EXT)
REF_RE = re.compile(r"`([\w./\-]+\.(" + CODE_EXT_RE + r")):(\d+)`")
CLAIM_RE = re.compile(r"([\w./\-]+\.(" + CODE_EXT_RE + r")):(\d+)")


def read_lines(rel):
    """行数口径照 Kotlin 守卫：readText().split("\\n")，末尾空元素丢掉。"""
    path = os.path.join(REPO_ROOT, rel)
    with open(path, "r", encoding="utf-8", newline="") as fh:
        text = fh.read()
    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    return lines


def is_blank(line):
    return line.strip() == ""


# 文档里点名代码用的是**短名**（`AppContainer.kt:202`，不带包路径），所以「有没有这个文件」
# 必须按基名去仓里找，不能拿仓根当参考系 —— 否则每一条短名都会被记成「文件不存在」，
# 这个读数就全部是口径噪声。第一次用到时才建索引，只建一次。
_PRUNE_DIRS = {".git", "build", ".gradle", ".kotlin", ".toolchain", "node_modules",
               ".idea", ".venv", ".pnpm-store", ".agents"}
_BASENAME_INDEX = None


def basename_index():
    global _BASENAME_INDEX
    if _BASENAME_INDEX is None:
        idx = {}
        for root, dirs, files in os.walk(REPO_ROOT):
            dirs[:] = [d for d in dirs if d not in _PRUNE_DIRS]
            for name in files:
                rel = os.path.relpath(os.path.join(root, name), REPO_ROOT).replace(os.sep, "/")
                idx.setdefault(name, []).append(rel)
        for key in idx:
            idx[key].sort(key=lambda p: (p.count("/"), len(p), p))
        _BASENAME_INDEX = idx
    return _BASENAME_INDEX


def resolve_target(path):
    """文档里写的那个路径 → 仓库里可能的候选（相对仓根）。空列表 = 找不到这个文件。"""
    p = path.lstrip("/")
    while p.startswith("./"):
        p = p[2:]
    if not p:
        return []
    if "/" in p:
        return [p] if os.path.isfile(os.path.join(REPO_ROOT, p)) else []
    return list(basename_index().get(p, []))


def target_line_exists(path, lineno):
    """目标文件在不在仓里、行号越不越界。返回 (nofile, oor, text|None)。

    短名可能有多个同名候选（比如 `TestTeardown.kt`）。行号判越界的口径取**最宽松**的那条：
    只要**有任何一个候选**能容纳这个行号，就不算越界 —— 越界是拿最长的候选比。
    """
    cands = resolve_target(path)
    if not cands:
        return True, False, None
    max_len = 0
    chosen = None
    for cand in cands:
        try:
            lines = read_lines(cand)
        except (OSError, UnicodeDecodeError):
            continue
        if len(lines) > max_len:
            max_len = len(lines)
            chosen = cand
    if chosen is None:
        return True, False, None
    if lineno < 1 or lineno > max_len:
        return False, True, None
    return False, False, read_lines(chosen)[lineno - 1]


# ------------------------------------------------------------------ spec ① tables
def split_row(line):
    """切 Markdown 表格行：去掉首尾竖线，按未转义竖线切，逐格 strip。"""
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|"):
        # 尾竖线前面是反斜杠 ⇒ 那是转义的竖线，不当分隔
        if not (len(s) >= 2 and s[-2] == "\\"):
            s = s[:-1]
    cells = []
    cur = []
    i = 0
    while i < len(s):
        ch = s[i]
        if ch == "\\" and i + 1 < len(s):
            cur.append(s[i + 1])
            i += 2
            continue
        if ch == "|":
            cells.append("".join(cur).strip())
            cur = []
            i += 1
            continue
        cur.append(ch)
        i += 1
    cells.append("".join(cur).strip())
    return cells


SEP_CELL_RE = re.compile(r"^:?-+:?$")


def is_separator_row(line):
    cells = split_row(line)
    if not cells:
        return False
    return all(SEP_CELL_RE.match(c) for c in cells)


def spec_tables():
    per_file = []
    total_tables = total_rows = total_issues = 0
    total_blocks = total_sep2 = total_nosep = 0
    for rel in MANAGED_FILES:
        lines = read_lines(rel)
        in_fence = False
        blocks = []
        cur = []
        for line in lines:
            if FENCE_RE.match(line):
                in_fence = not in_fence
                if cur:
                    blocks.append(cur)
                    cur = []
                continue
            if in_fence:
                continue
            if line.lstrip().startswith("|"):
                cur.append(line)
            else:
                if cur:
                    blocks.append(cur)
                    cur = []
        if cur:
            blocks.append(cur)

        # 口径逐条对齐 DocTableShapeGuardTest.census：tables 数**所有**竖线块（不要求第 2 行是
        # 分隔行），rows = 每个块的行数减 1（表头那行不算，分隔行算一行数据），issues 逐行比格数
        # 但跳过第 2 行（它是分隔行时）。第 2 行不是分隔行的块单独记进 strays。
        tables = rows = issues = strays = 0
        for block in blocks:
            tables += 1
            if len(block) < 2 or not is_separator_row(block[1]):
                strays += 1
            head_cells = len(split_row(block[0]))
            for k in range(1, len(block)):
                rows += 1
                if k == 1 and is_separator_row(block[k]):
                    continue
                if len(split_row(block[k])) != head_cells:
                    issues += 1
        total_blocks += len(blocks)
        total_sep2 += tables - strays
        total_nosep += strays
        total_tables += tables
        total_rows += rows
        total_issues += issues
        per_file.append((rel, tables, rows, issues))

    out = []
    for rel, tables, rows, issues in per_file:
        out.append("[spec tables] %s tables=%d rows=%d issues=%d" % (rel, tables, rows, issues))
    out.append("[spec tables] TOTAL tables=%d rows=%d issues=%d"
               % (total_tables, total_rows, total_issues))
    out.append("[spec tables] BLOCKS blocks=%d sep2=%d nosep=%d"
               % (total_blocks, total_sep2, total_nosep))
    data = {
        "total": {"tables": total_tables, "rows": total_rows, "issues": total_issues},
        "blocks": {"blocks": total_blocks, "sep2": total_sep2, "nosep": total_nosep},
        "per_file": {rel: {"tables": t, "rows": r, "issues": i} for rel, t, r, i in per_file},
    }
    return out, data


# -------------------------------------------------------------------- spec ② refs
def spec_refs():
    per_file = []
    total_refs = total_bad = 0
    nofile = oor = blank = 0
    for rel in MANAGED_FILES:
        text = "\n".join(read_lines(rel))
        seen = {}
        for m in REF_RE.finditer(text):
            key = m.group(0)
            if key in seen:
                continue
            seen[key] = (m.group(1), int(m.group(3)))
        refs = len(seen)
        bad = 0
        for _, (target, lineno) in sorted(seen.items()):
            nf, oo, line = target_line_exists(target, lineno)
            if nf:
                nofile += 1
                bad += 1
            elif oo:
                oor += 1
                bad += 1
            elif line is None or is_blank(line):
                blank += 1
                bad += 1
        total_refs += refs
        total_bad += bad
        per_file.append((rel, refs, bad))
    out = []
    for rel, refs, bad in per_file:
        out.append("[spec refs] %s refs=%d blank-or-out=%d" % (rel, refs, bad))
    out.append("[spec refs] TOTAL refs=%d blank-or-out=%d" % (total_refs, total_bad))
    out.append("[spec refs] BREAKDOWN nofile=%d oor=%d blank=%d" % (nofile, oor, blank))
    data = {
        "total": {"refs": total_refs, "blankOrOut": total_bad},
        "breakdown": {"nofile": nofile, "oor": oor, "blank": blank},
        "per_file": {rel: {"refs": r, "blankOrOut": b} for rel, r, b in per_file},
    }
    return out, data


# --------------------------------------------------------------------- spec ③ eol
def spec_eol():
    per_file = []
    total_hits = 0
    hit_files = 0
    for rel in MANAGED_FILES:
        path = os.path.join(REPO_ROOT, rel)
        with open(path, "r", encoding="utf-8", newline="") as fh:
            text = fh.read()
        lines = text.split("\n")
        if lines and lines[-1] == "":
            lines.pop()
        trailing = 0
        lone_cr = 0
        for line in lines:
            body = line[:-1] if line.endswith("\r") else line
            if body != "" and (body.endswith(" ") or body.endswith("\t")):
                trailing += 1
            # 裸 CR：行内出现非行尾的 \r
            if "\r" in body:
                lone_cr += body.count("\r")
        hits = trailing + lone_cr
        total_hits += hits
        if hits > 0:
            hit_files += 1
        per_file.append((rel, hits, trailing, lone_cr))
    out = []
    for rel, hits, trailing, lone_cr in per_file:
        out.append("[spec eol] %s hits=%d trailing-ws=%d lone-cr=%d"
                   % (rel, hits, trailing, lone_cr))
    out.append("[spec eol] TOTAL %d HITFILES %d" % (total_hits, hit_files))
    data = {
        "total": {"hits": total_hits, "hitFiles": hit_files},
        "per_file": {rel: {"hits": h, "trailingWs": t, "loneCr": c}
                     for rel, h, t, c in per_file},
    }
    return out, data


# -------------------------------------------------------------------- spec ④ xref
SEC_PATTERNS = [
    ("records01", re.compile(r"^##\s+第\s*(\d+)\s*轮")),
    ("records04", re.compile(r"^##\s+第[零一二三四五六七八九十百]+节\s*·\s*第\s*(\d+)\s*轮")),
    ("docs03_06", re.compile(r"^###\s+[\d.]+\s+第\s*(\d+)\s*轮")),
]


def latest_section_start(lines):
    """返回 (起始行下标, 轮次)，找不到返回 (None, None)。"""
    best_idx = None
    best_round = None
    for idx, line in enumerate(lines):
        for _, pat in SEC_PATTERNS:
            m = pat.match(line)
            if m:
                rnd = int(m.group(1))
                if best_round is None or rnd >= best_round:
                    best_round = rnd
                    best_idx = idx
                break
    return best_idx, best_round


def spec_xref():
    per_file = []
    total = live = hist = live_bad = hist_nofile = 0
    for rel in MANAGED_FILES:
        lines = read_lines(rel)
        start, _rnd = latest_section_start(lines)
        text = "\n".join(lines)
        pos_offsets = []
        off = 0
        for line in lines:
            pos_offsets.append(off)
            off += len(line) + 1
        live_start_char = pos_offsets[start] if start is not None else len(text)

        seen = {}
        for m in CLAIM_RE.finditer(text):
            key = (m.group(1), int(m.group(3)))
            if key in seen:
                continue
            seen[key] = m.start()

        f_total = f_live = f_hist = f_live_bad = f_hist_nofile = 0
        for (target, lineno), pos in sorted(seen.items(), key=lambda kv: kv[1]):
            f_total += 1
            nf, oo, _ = target_line_exists(target, lineno)
            if pos >= live_start_char:
                f_live += 1
                if nf or oo:
                    f_live_bad += 1
            else:
                f_hist += 1
                if nf:
                    f_hist_nofile += 1
        total += f_total
        live += f_live
        hist += f_hist
        live_bad += f_live_bad
        hist_nofile += f_hist_nofile
        per_file.append((rel, f_total, f_live, f_hist, f_live_bad, f_hist_nofile))
    out = []
    for rel, t, l, h, lb, hn in per_file:
        out.append("[spec xref] %s TOTAL=%d LIVE=%d HIST=%d LIVE_BAD=%d HIST_NOFILE=%d"
                   % (rel, t, l, h, lb, hn))
    out.append("[spec xref] TOTAL=%d LIVE=%d HIST=%d LIVE_BAD=%d HIST_NOFILE=%d"
               % (total, live, hist, live_bad, hist_nofile))
    data = {
        "total": {"total": total, "live": live, "hist": hist,
                  "liveBad": live_bad, "histNofile": hist_nofile},
        "per_file": {rel: {"total": t, "live": l, "hist": h,
                           "liveBad": lb, "histNofile": hn}
                     for rel, t, l, h, lb, hn in per_file},
    }
    return out, data


SPECS = [
    ("tables", spec_tables),
    ("refs", spec_refs),
    ("eol", spec_eol),
    ("xref", spec_xref),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", default="run")
    ap.add_argument("--json", default="")
    args = ap.parse_args()

    print("probes label=%s repo=%s managed=%d" % (args.label, REPO_ROOT, len(MANAGED_FILES)))
    payload = {"label": args.label, "managedFiles": len(MANAGED_FILES), "specs": {}}
    for name, fn in SPECS:
        out, data = fn()
        for line in out:
            print(line)
        payload["specs"][name] = data
    print("PROBES-EXIT=0")
    if args.json:
        out_path = args.json
        if not os.path.isabs(out_path):
            out_path = os.path.join(REPO_ROOT, out_path)
        os.makedirs(os.path.dirname(out_path), exist_ok=True)
        with open(out_path, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, ensure_ascii=False, indent=2, sort_keys=True)
        print("PROBES-JSON=%s" % out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
