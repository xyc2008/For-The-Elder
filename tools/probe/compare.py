#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""两份 probes.py 结果的对账器。

用途：同一轮迭代里，「任何文档落盘之前」跑一趟 probes.py 得 pre.json，
「全部记录落盘之后」再跑一趟得 post.json，本脚本把两趟逐指标相减。

判定语义：末行 COMPARE143 总判定 PASS 的意思是「两份可比、差额已算出」——
不是「差额为零」。差额是多少，就照实打多少；文档轮次里表格与引用必然增长。

用法：
  python tools/probe/compare.py build/rNNN/pre.json build/rNNN/post.json
"""

import json
import os
import subprocess
import sys

# 每个 spec 下要逐项相减的标量指标（路径 -> 中文名）
METRICS = [
    ("tables", "total", "tables", "表格张数"),
    ("tables", "total", "rows", "表格数据行数"),
    ("tables", "total", "issues", "列数不齐的坏行"),
    ("tables", "blocks", "blocks", "候选块数"),
    ("tables", "blocks", "sep2", "第二行是分隔行的块"),
    ("tables", "blocks", "nosep", "第二行不是分隔行的块"),
    ("refs", "total", "refs", "代码引用总数"),
    ("refs", "total", "blankOrOut", "指向空行或越界或缺失的引用"),
    ("refs", "breakdown", "nofile", "目标文件不存在"),
    ("refs", "breakdown", "oor", "行号越界"),
    ("refs", "breakdown", "blank", "指向空行"),
    ("eol", "total", "hits", "行尾空白与裸 CR"),
    ("eol", "total", "hitFiles", "命中的文件数"),
    ("xref", "total", "total", "代码行宣称总数"),
    ("xref", "total", "live", "最新轮次节内的宣称"),
    ("xref", "total", "hist", "更早节里的宣称"),
    ("xref", "total", "liveBad", "最新节里指向不存在文件或越界"),
    ("xref", "total", "histNofile", "更早节里点名不存在文件"),
]

# 逐份文件要对比的指标
PER_FILE = [
    ("tables", (("tables", "表格张数"), ("rows", "数据行数"), ("issues", "坏行"))),
    ("refs", (("refs", "引用数"), ("blankOrOut", "坏引用"))),
    ("eol", (("hits", "命中"), ("trailingWs", "行尾空白"), ("loneCr", "裸 CR"))),
    ("xref", (("total", "宣称"), ("live", "LIVE"), ("liveBad", "LIVE_BAD"))),
]


def load(path):
    full = path
    if not os.path.isabs(full):
        full = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(
            os.path.abspath(__file__)))), full)
    with open(full, "r", encoding="utf-8") as fh:
        return json.load(fh), full


def git_rev():
    try:
        out = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
            stderr=subprocess.DEVNULL)
        return out.decode("ascii", "replace").strip()
    except Exception:
        return "unknown"


def main():
    if len(sys.argv) < 3:
        print("usage: compare.py PRE.json POST.json")
        print("COMPARE-EXIT=1")
        return 1
    pre, pre_path = load(sys.argv[1])
    post, post_path = load(sys.argv[2])
    print("compare pre=%s (label=%s)" % (pre_path, pre.get("label")))
    print("compare post=%s (label=%s)" % (post_path, post.get("label")))
    print("compare git=%s" % git_rev())

    diffs = 0
    for spec, section, key, cname in METRICS:
        a = pre["specs"][spec].get(section, {}).get(key)
        b = post["specs"][spec].get(section, {}).get(key)
        if a is None or b is None:
            print("[%s] %s: MISSING pre=%s post=%s" % (spec, cname, a, b))
            continue
        d = b - a
        if d != 0:
            diffs += 1
        print("[%s] %-28s PRE=%d POST=%d DIFF=%+d" % (spec, cname, a, b, d))

    print("--- 逐份文件变动 ---")
    per_file_changes = 0
    for spec, keys in PER_FILE:
        pre_pf = pre["specs"][spec].get("per_file", {})
        post_pf = post["specs"][spec].get("per_file", {})
        names = sorted(set(pre_pf) | set(post_pf))
        for rel in names:
            a = pre_pf.get(rel, {})
            b = post_pf.get(rel, {})
            for key, cname in keys:
                va = a.get(key)
                vb = b.get(key)
                if va is None or vb is None or va != vb:
                    per_file_changes += 1
                    print("[%s] %s %s: PRE=%s POST=%s DIFF=%s"
                          % (spec, rel, cname, va, vb,
                             "n/a" if (va is None or vb is None) else "%+d" % (vb - va)))
    if per_file_changes == 0:
        print("（逐份文件无变动）")

    print("compare 汇总：标量变动项=%d 逐文件变动项=%d" % (diffs, per_file_changes))
    print("COMPARE143 总判定 PASS  （PASS = 两份可比且差额已算出，不代表差额为零）")
    print("COMPARE-EXIT=0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
