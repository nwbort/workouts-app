"""Copy the extracted Streek plans into the app's assets.

The app ships the plans rather than reading them from a calendar, so it works
offline and carries what the calendar events never had: the weekly coach's
notes, the glossary and the run prescriptions broken into parts.

This reuses the streek repo's own code for the two things that must agree with
the calendar tool: the day titles ("Run 10km + Strength" rather than "Combo")
and the filename each video was downloaded under. Deriving either a second
time here would drift the first time one changed.

Usage (from the repo root, with the streek repo checked out alongside):
    python3 tools/import_streek.py
    python3 tools/import_streek.py --streek ~/code/streek
"""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
DAY_INDEX = {d: i for i, d in enumerate(
    ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
)}
TABS = [("info", "Info"), ("technique", "Technique"), ("nutrition", "Nutrition"), ("gear", "Gear")]
# Display order for the video library, as in the streek video index: calf
# rehab is pulled out of strength/stretching into its own group at the end.
VIDEO_GROUPS = [("strength", "Strength workouts"), ("drill", "Drills"),
                ("stretching", "Stretching"), ("rolling", "Foam rolling")]


def num(x: float) -> str:
    return f"{x:g}"


def secs(s: int) -> str:
    return f"{s // 60} min" if s % 60 == 0 else f"{s} s"


def span(d: dict | None, unit: str) -> str | None:
    if not d:
        return None
    lo, hi = d.get("min"), d.get("max")
    if lo is None:
        return None
    return f"{num(lo)} {unit}" if hi in (None, lo) else f"{num(lo)}–{num(hi)} {unit}"


def run_tags(run: dict) -> list[str]:
    """Short labels for the parts of a run, shown as chips under its text."""
    tags = []
    if run.get("distance_km"):
        tags.append(f"{num(run['distance_km'])} km")
    if rw := run.get("run_walk"):
        if rw.get("run_s"):
            lab = f"Run {secs(rw['run_s'])} / walk {secs(rw['walk_s'])}"
            if rw.get("reps"):
                lab += f" ×{rw['reps']}"
        else:  # self-paced: walk breaks whenever you like
            lab = "Run / walk"
        tags.append(lab)
    if run.get("pace"):
        tags.append(f"{run['pace']} pace")
    if run.get("terrain"):
        tags.append(run["terrain"].replace(" Terrain", ""))
    if run.get("surface"):
        tags.append(run["surface"].capitalize())
    if elev := span(run.get("elevation_m"), "m"):
        tags.append(f"↑ {elev}")
    for it in run.get("intervals") or []:
        lab = f"{it['reps']} × "
        if it.get("length_m"):
            lab += f"{num(it['length_m'])} m "
        elif it.get("duration_s"):
            lab += f"{secs(it['duration_s'])} "
        lab += it["type"]
        if it.get("intensity"):
            lab += f" @ {it['intensity']}"
        tags.append(lab)
    if run.get("max_time_h"):
        tags.append(f"Max {num(run['max_time_h'])} h")
    if run.get("focus"):
        tags.append(f"{run['focus']} focus")
    return tags


def session_record(s: dict) -> dict:
    title = (s.get("title") or "").strip()
    text = (s.get("description") or "").strip()
    rec = {"title": title, "kind": s.get("kind") or "other"}
    if text:
        rec["text"] = text
    if m := re.search(r"/video/(\d+)", s.get("video_url") or ""):
        rec["video"] = m.group(1)
    tags = []
    if dur := span(s.get("duration_min"), "min"):
        tags.append(dur)
    if s.get("run"):
        tags.extend(run_tags(s["run"]))
    if s.get("equipment"):
        tags.append(s["equipment"])
    if tags:
        rec["tags"] = tags
    if title.lower().startswith("optional") or "optional" in text.lower()[:40]:
        rec["optional"] = True
    return rec


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--streek", type=Path, default=ROOT.parent / "streek",
                    help="path to the streek repo checkout (default ../streek)")
    args = ap.parse_args()

    sys.path.insert(0, str(args.streek.resolve()))
    from streek_extract.calendar_data import build, plan_files, video_table  # noqa: E402

    files = plan_files()
    compact = build()
    strings = compact["strings"]
    s = lambda i: None if i is None else strings[i]

    # The calendar tool's own day title, keyed by plan and (week, weekday).
    titles: dict[str, dict[tuple[int, int], str]] = {}
    for p in compact["plans"]:
        t = titles.setdefault(p["slug"], {})
        for day in p["days"]:
            typ = s(day.get("t")) or ""
            extra = s(day.get("rw")) or s(day.get("dur"))
            km = day.get("km")
            t[(day["w"], day["i"])] = (
                s(day.get("lbl")) or (f"{typ} {num(km)}km" if km else None)
                or (f"{typ} {extra}" if extra else None) or typ or "Session"
            )

    out_dir = ASSETS / "plans"
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("*.json"):
        old.unlink()

    index = []
    for f in files:
        d = json.loads(f.read_text(encoding="utf-8"))
        p = d["plan"]
        slug = p["slug"]
        race = None
        days, weeks = [], []
        for w in d["weeks"]:
            tabs = w.get("tabs") or {}
            notes = [{"label": label, "text": tabs[key].strip()}
                     for key, label in TABS if (tabs.get(key) or "").strip()]
            wk = {"week": w["week"], "notes": notes}
            if w.get("total_km"):
                wk["km"] = w["total_km"]
            weeks.append(wk)
            for day in w["days"]:
                di = DAY_INDEX.get(day["day"])
                if di is None:
                    continue
                sessions = [session_record(x) for x in day["sessions"]]
                if race is None and any(x["kind"] == "race" for x in sessions):
                    race = {"week": w["week"], "day": di}
                rec = {"week": w["week"], "day": di, "type": day.get("type") or "",
                       "title": titles[slug][(w["week"], di)], "sessions": sessions}
                if day.get("distance_km"):
                    rec["km"] = day["distance_km"]
                days.append(rec)
        days.sort(key=lambda r: (r["week"], r["day"]))
        title = html.unescape(p.get("title") or slug).strip()
        plan = {
            "slug": slug, "title": title, "level": p.get("level"), "weeks": len(d["weeks"]),
            "race": race, "days": days, "weekInfo": weeks,
            "glossary": [{"term": g["term"].strip(), "definition": g["definition"].strip()}
                         for g in d.get("glossary") or []],
        }
        (out_dir / f"{slug}.json").write_text(
            json.dumps(plan, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        index.append({"slug": slug, "title": title, "weeks": plan["weeks"],
                      "level": p.get("level"), "race": race})

    index.sort(key=lambda x: x["title"].lower())
    (out_dir / "index.json").write_text(
        json.dumps(index, ensure_ascii=False, indent=1), encoding="utf-8")

    natural = lambda name: tuple(int(t) if t.isdigit() else t for t in re.split(r"(\d+)", name))
    library = video_table(files)
    videos = []
    is_calf = lambda v: v["file"].startswith("calf-")
    for kind, heading in VIDEO_GROUPS:
        for v in sorted((v for v in library.values() if v["kind"] == kind and not is_calf(v)),
                        key=lambda v: natural(v["file"])):
            videos.append({"id": v["id"], "title": v["title"], "file": v["file"], "group": heading})
    for v in sorted((v for v in library.values() if is_calf(v)), key=lambda v: natural(v["file"])):
        videos.append({"id": v["id"], "title": v["title"], "file": v["file"], "group": "Calf rehab"})
    listed = {v["id"] for v in videos}
    for v in library.values():  # any kind without a group of its own
        if v["id"] not in listed:
            videos.append({"id": v["id"], "title": v["title"], "file": v["file"], "group": "Other"})
    (ASSETS / "videos.json").write_text(
        json.dumps(videos, ensure_ascii=False, indent=1), encoding="utf-8")

    size = sum(x.stat().st_size for x in out_dir.glob("*.json"))
    print(f"{len(index)} plans ({size / 1024:.0f} KB), {len(videos)} videos -> {ASSETS}")


if __name__ == "__main__":
    main()
