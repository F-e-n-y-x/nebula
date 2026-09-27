#!/usr/bin/env python3
"""Downloads the debug demo artwork (Steam store art) next to games.json.

The images are copyrighted by their publishers, so they're not committed; games.json is. Run once
before building a debug APK for screenshots:  python3 nebula/tools/fetch-demo-art.py
Release builds never include src/debug, so none of this ships.
"""
import json
import pathlib
import urllib.request

DEMO = pathlib.Path(__file__).resolve().parent.parent / "src/debug/assets/demo"
CDN = "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/{appid}/{name}"
KINDS = {"poster": "library_600x900_2x.jpg", "hero": "library_hero.jpg", "logo": "logo.png", "header": "header.jpg"}


def get(url, dest):
    if dest.exists():
        return
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        dest.write_bytes(r.read())
    print("fetched", dest.name)


def main():
    games = json.loads((DEMO / "games.json").read_text())
    for g in games:
        appid = g["appid"]
        for kind, file in g["art"].items():
            get(CDN.format(appid=appid, name=KINDS[kind]), DEMO / file)
        shots = g.get("screenshots") or []
        if shots and not all((DEMO / s).exists() for s in shots):
            with urllib.request.urlopen(f"https://store.steampowered.com/api/appdetails?appids={appid}", timeout=30) as r:
                data = json.load(r)[str(appid)]["data"]
            for s, info in zip(shots, data.get("screenshots", [])):
                get(info["path_full"], DEMO / s)


if __name__ == "__main__":
    main()
