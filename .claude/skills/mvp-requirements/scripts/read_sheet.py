#!/usr/bin/env python3
"""xlsx/csv 를 마크다운 표로 출력한다.

표준 라이브러리만 사용한다 (openpyxl/pandas 설치를 요구하지 않는다).
xlsx 는 XML 을 담은 zip 이므로 직접 파싱한다.

    python3 read_sheet.py <파일>                    # xlsx 면 시트 목록
    python3 read_sheet.py <파일> --sheet 기능목록    # 시트명 또는 0-기반 인덱스
    python3 read_sheet.py <파일> --max-rows 300
"""

import argparse
import csv
import datetime
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
NS_REL_DOC = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
NS_REL_PKG = "http://schemas.openxmlformats.org/package/2006/relationships"

CSV_ENCODINGS = ("utf-8-sig", "cp949", "utf-16")
DEFAULT_MAX_ROWS = 100

# 엑셀 기본 날짜/시간 서식 ID (ECMA-376 18.8.30)
BUILTIN_DATE_FORMATS = frozenset({14, 15, 16, 17, 18, 19, 20, 21, 22, 45, 46, 47})


def q(tag):
    return f"{{{NS_MAIN}}}{tag}"


def col_index(cell_ref):
    """'BC12' -> 54 (0-기반 열 인덱스)."""
    letters = re.match(r"([A-Z]+)", cell_ref or "")
    if not letters:
        return None
    n = 0
    for ch in letters.group(1):
        n = n * 26 + (ord(ch) - ord("A") + 1)
    return n - 1


def read_shared_strings(zf):
    try:
        raw = zf.read("xl/sharedStrings.xml")
    except KeyError:
        return []
    strings = []
    for si in ET.fromstring(raw).findall(q("si")):
        # <si> 안의 모든 <t> 를 이어붙인다 (서식 조각으로 쪼개져 있을 수 있다).
        strings.append("".join(t.text or "" for t in si.iter(q("t"))))
    return strings


def is_date_format(code):
    """서식 문자열이 날짜/시간인지. 리터럴("..."), 색상/조건([...]) 은 빼고 본다."""
    code = re.sub(r'"[^"]*"|\[[^\]]*\]|\\.', "", code)
    return any(ch in code for ch in "ymdhs")


def read_date_styles(zf):
    """날짜 서식이 걸린 셀 스타일 인덱스 집합. cellXfs 순서가 셀의 s 속성 값이다."""
    try:
        styles = ET.fromstring(zf.read("xl/styles.xml"))
    except KeyError:
        return frozenset()

    custom = {
        int(fmt.get("numFmtId")): fmt.get("formatCode", "")
        for fmt in styles.iter(q("numFmt"))
        if fmt.get("numFmtId")
    }

    date_styles = set()
    cell_xfs = styles.find(q("cellXfs"))
    if cell_xfs is None:
        return frozenset()
    for i, xf in enumerate(cell_xfs.findall(q("xf"))):
        fmt_id = int(xf.get("numFmtId", 0))
        if fmt_id in BUILTIN_DATE_FORMATS or is_date_format(custom.get(fmt_id, "")):
            date_styles.add(i)
    return frozenset(date_styles)


def serial_to_date(serial, epoch_1904):
    """엑셀 날짜 일련번호 → 문자열. 1900 체계는 존재하지 않는 1900-02-29 를 포함하므로
    기준일을 1899-12-30 으로 잡아 보정한다."""
    base = datetime.datetime(1904, 1, 1) if epoch_1904 else datetime.datetime(1899, 12, 30)
    try:
        moment = base + datetime.timedelta(days=float(serial))
    except (ValueError, OverflowError):
        return None
    if moment.time() == datetime.time(0, 0):
        return moment.strftime("%Y-%m-%d")
    return moment.strftime("%Y-%m-%d %H:%M:%S")


def uses_1904_epoch(zf):
    workbook = ET.fromstring(zf.read("xl/workbook.xml"))
    props = workbook.find(q("workbookPr"))
    return props is not None and props.get("date1904") in ("1", "true")


def list_sheets(zf):
    """[(시트명, zip 내부 경로)] 를 워크북에 정의된 순서대로 반환."""
    workbook = ET.fromstring(zf.read("xl/workbook.xml"))

    targets = {}
    try:
        rels = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
    except KeyError:
        rels = None
    if rels is not None:
        for rel in rels.findall(f"{{{NS_REL_PKG}}}Relationship"):
            target = rel.get("Target", "")
            if target.startswith("/"):
                path = target.lstrip("/")
            elif target.startswith("../"):
                path = target[3:]
            else:
                path = f"xl/{target}"
            targets[rel.get("Id")] = path

    sheets = []
    for i, sheet in enumerate(workbook.iter(q("sheet"))):
        rid = sheet.get(f"{{{NS_REL_DOC}}}id")
        path = targets.get(rid) or f"xl/worksheets/sheet{i + 1}.xml"
        sheets.append((sheet.get("name") or f"sheet{i + 1}", path))
    return sheets


def cell_text(cell, shared, date_styles, epoch_1904):
    kind = cell.get("t")
    if kind == "inlineStr":
        return "".join(t.text or "" for t in cell.iter(q("t")))
    value = cell.find(q("v"))
    if value is None or value.text is None:
        return ""
    if kind == "s":
        try:
            return shared[int(value.text)]
        except (ValueError, IndexError):
            return ""
    if kind == "str":  # 수식 결과 문자열
        return value.text
    if kind == "b":
        return "TRUE" if value.text == "1" else "FALSE"
    # 숫자 셀. 날짜 서식이면 일련번호(46235)가 아니라 날짜로 보여준다.
    if int(cell.get("s", -1)) in date_styles:
        as_date = serial_to_date(value.text, epoch_1904)
        if as_date:
            return as_date
    return value.text


def read_xlsx_rows(zf, path, shared, date_styles, epoch_1904):
    """행 목록을 반환. 셀의 r 속성으로 열 위치를 복원해 빈 칸이 밀리지 않게 한다."""
    sheet = ET.fromstring(zf.read(path))
    rows = []
    for row in sheet.iter(q("row")):
        cells = {}
        fallback = 0
        for cell in row.findall(q("c")):
            idx = col_index(cell.get("r"))
            if idx is None:
                idx = fallback
            fallback = idx + 1
            text = cell_text(cell, shared, date_styles, epoch_1904)
            if text:
                cells[idx] = text
        if not cells:
            rows.append([])
            continue
        width = max(cells) + 1
        rows.append([cells.get(i, "") for i in range(width)])
    return rows


def read_csv_rows(path):
    for encoding in CSV_ENCODINGS:
        try:
            with open(path, encoding=encoding, newline="") as f:
                return list(csv.reader(f))
        except UnicodeDecodeError:
            continue
    sys.exit(f"인코딩을 판별하지 못했습니다 (시도: {', '.join(CSV_ENCODINGS)}): {path}")


def escape(text):
    return text.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "<br>").strip()


def print_markdown(rows, max_rows):
    rows = [r for r in rows if any(c.strip() for c in r)]
    if not rows:
        print("(빈 시트)")
        return

    total = len(rows)
    shown = rows[:max_rows]
    width = max(len(r) for r in shown)

    def line(cells):
        padded = list(cells) + [""] * (width - len(cells))
        return "| " + " | ".join(escape(c) for c in padded) + " |"

    print(line(shown[0]))
    print("|" + "---|" * width)
    for row in shown[1:]:
        print(line(row))

    if total > max_rows:
        print(f"\n… {total}행 중 {max_rows}행만 표시했습니다 (--max-rows 로 조정).")


def main():
    parser = argparse.ArgumentParser(description="xlsx/csv 를 마크다운 표로 출력")
    parser.add_argument("file", help="읽을 .xlsx 또는 .csv 파일")
    parser.add_argument("--sheet", help="시트명 또는 0-기반 인덱스 (xlsx 전용)")
    parser.add_argument("--max-rows", type=int, default=DEFAULT_MAX_ROWS)
    args = parser.parse_args()

    if args.file.lower().endswith(".csv"):
        print_markdown(read_csv_rows(args.file), args.max_rows)
        return

    if not zipfile.is_zipfile(args.file):
        sys.exit(f"xlsx 로 읽을 수 없는 파일입니다 (구형 .xls 는 미지원): {args.file}")

    with zipfile.ZipFile(args.file) as zf:
        sheets = list_sheets(zf)
        if not sheets:
            sys.exit(f"시트를 찾지 못했습니다: {args.file}")

        if args.sheet is None:
            if len(sheets) > 1:
                print(f"시트 {len(sheets)}개 — --sheet 으로 선택하세요:\n")
                for i, (name, _) in enumerate(sheets):
                    print(f"  {i}  {name}")
                return
            target = sheets[0]
        else:
            match = [s for s in sheets if s[0] == args.sheet]
            if match:
                target = match[0]
            elif args.sheet.isdigit() and int(args.sheet) < len(sheets):
                target = sheets[int(args.sheet)]
            else:
                names = ", ".join(name for name, _ in sheets)
                sys.exit(f"그런 시트가 없습니다: {args.sheet} (있는 시트: {names})")

        rows = read_xlsx_rows(
            zf, target[1], read_shared_strings(zf), read_date_styles(zf), uses_1904_epoch(zf)
        )
        print(f"# {target[0]}\n")
        print_markdown(rows, args.max_rows)


if __name__ == "__main__":
    main()
