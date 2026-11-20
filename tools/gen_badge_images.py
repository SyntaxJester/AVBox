#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
重新生成徽章配图资源。

背景：徽章包（res/raw/*badges.json）里的 imageURL 全部指向境外 CDN，
国内网络下取不到，界面上就只剩文字徽章。这里把包里引用到的所有图
一次性抓下来、统一转成 WebP 打进 app/src/main/assets/badge_images，
运行时由 BadgeImageStore 直接读本地，完全不走网络。

文件名规则：URL 的 SHA-1 前 8 字节（16 位小写十六进制）+ ".webp"，
必须和 BadgeImageStore.assetName() 完全一致，改这里要同步改 Kotlin。

依赖：
    pip install pillow resvg-py

用法：
    python3 tools/gen_badge_images.py            # 增量：已存在的图跳过
    python3 tools/gen_badge_images.py --force    # 全量重抓
"""
import argparse
import concurrent.futures
import hashlib
import io
import json
import os
import re
import sys
import time
import urllib.request

try:
    from PIL import Image
except ImportError:
    sys.exit("缺少 pillow：pip install pillow")

try:
    import resvg_py
except ImportError:
    sys.exit("缺少 resvg-py：pip install resvg-py")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW_DIR = os.path.join(ROOT, "app/src/main/res/raw")
ASSET_DIR = os.path.join(ROOT, "app/src/main/assets/badge_images")
TMP_DIR = os.path.join(ROOT, "build/badge_src")

TARGET_H = 120      # 播放页徽章实际高度 26dp，按 3~4 倍密度渲染
QUALITY = 86
UA = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
}


def asset_name(url):
    return hashlib.sha1(url.strip().encode("utf-8")).hexdigest()[:16] + ".webp"


def collect_urls():
    urls = []
    for name in sorted(os.listdir(RAW_DIR)):
        if not (name.endswith(".json") and "badge" in name):
            continue
        with open(os.path.join(RAW_DIR, name), encoding="utf-8") as f:
            try:
                pack = json.load(f)
            except ValueError as e:
                print("跳过 %s：%s" % (name, e))
                continue
        for item in pack.get("filters", []):
            url = (item.get("imageURL") or "").strip()
            if url.startswith("http") and url not in urls:
                urls.append(url)
    return urls


def mirror_of(url):
    prefix = "https://raw.githubusercontent.com/"
    if not url.startswith(prefix):
        return None
    parts = url[len(prefix):].split("/", 3)
    if len(parts) != 4:
        return None
    return "https://cdn.jsdelivr.net/gh/%s/%s@%s/%s" % tuple(parts)


def download(url, force=False):
    dst = os.path.join(TMP_DIR, hashlib.sha1(url.encode("utf-8")).hexdigest()[:16])
    if not force and os.path.exists(dst) and os.path.getsize(dst) > 100:
        return dst, "cached"
    for candidate in filter(None, [url, mirror_of(url)]):
        for _ in range(2):
            try:
                req = urllib.request.Request(candidate, headers=UA)
                with urllib.request.urlopen(req, timeout=25) as resp:
                    data = resp.read()
                if len(data) < 50:
                    continue
                with open(dst, "wb") as f:
                    f.write(data)
                return dst, "ok"
            except Exception:
                time.sleep(0.3)
    return None, "failed"


def render_svg(path):
    with open(path, "rb") as f:
        svg = f.read().decode("utf-8", "replace")
    box = re.search(r'viewBox="([\-0-9. ]+)"', svg)
    if box:
        vb = [float(x) for x in box.group(1).split()]
    else:
        w = re.search(r'width="([\-0-9.]+)', svg)
        h = re.search(r'height="([\-0-9.]+)', svg)
        if not w or not h:
            raise ValueError("无法确定 SVG 尺寸")
        vb = [0, 0, float(w.group(1)), float(h.group(1))]
    if vb[2] <= 0 or vb[3] <= 0:
        raise ValueError("SVG 尺寸非法")
    # 原图常带 mm 单位，resvg 不认，去掉后交给 viewBox 控制
    svg = re.sub(r'(<svg\b[^>]*?)\swidth="[^"]*"', r"\1", svg, count=1)
    svg = re.sub(r'(<svg\b[^>]*?)\sheight="[^"]*"', r"\1", svg, count=1)
    height = TARGET_H
    width = max(1, int(round(height * vb[2] / vb[3])))
    png = resvg_py.svg_to_bytes(svg_string=svg, width=width, height=height, background=None)
    return Image.open(io.BytesIO(png)).convert("RGBA")


def render_raster(path):
    image = Image.open(path)
    image.load()
    image = image.convert("RGBA")
    w, h = image.size
    if h > TARGET_H:
        image = image.resize((max(1, int(round(w * TARGET_H / h))), TARGET_H), Image.LANCZOS)
    return image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="忽略已有缓存重新抓取")
    args = parser.parse_args()

    os.makedirs(TMP_DIR, exist_ok=True)
    os.makedirs(ASSET_DIR, exist_ok=True)

    urls = collect_urls()
    print("徽章包引用图地址：%d 条" % len(urls))

    failed = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
        results = list(pool.map(lambda u: (u,) + download(u, args.force), urls))

    done = 0
    for url, path, state in results:
        if not path:
            failed.append(url)
            continue
        out = os.path.join(ASSET_DIR, asset_name(url))
        if state == "cached" and os.path.exists(out):
            done += 1
            continue
        try:
            image = render_svg(path) if url.lower().endswith(".svg") else render_raster(path)
            image.save(out, "WEBP", quality=QUALITY, method=6)
            done += 1
        except Exception as e:
            failed.append("%s (%s)" % (url, e))

    print("生成完成：%d 张，失败 %d 条" % (done, len(failed)))
    for item in failed[:20]:
        print("  !", item)
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
