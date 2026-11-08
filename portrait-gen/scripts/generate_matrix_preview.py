#!/usr/bin/env python3
"""
Portrait Generation Tool: 3x3 Attire vs. Hairstyle Synthesis Matrix Preview.
Simulates AvatarSynthesizer.synthesizePortrait locally with PIL to verify
component alignment, transparent layers, and border framing.
"""

import os
import sys
from PIL import Image

def generate_matrix():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    root_dir = os.path.dirname(script_dir)
    avatar_dir = os.path.join(root_dir, "assets", "avatar")
    border_path = os.path.join(root_dir, "assets", "borders", "gold_fantasy_frame.png")
    bg_path = os.path.join(root_dir, "assets", "reference_portraits", "aethel.jpg")

    size = 512
    frame = Image.open(border_path).convert("RGBA").resize((size, size), Image.Resampling.NEAREST)

    # Base background (dark mystical vignette)
    bg = Image.new("RGBA", (size, size), (20, 18, 28, 255))

    bodies = [
        ("Mage Robes", Image.open(os.path.join(avatar_dir, "body_mage.png")).convert("RGBA")),
        ("Knight Plate", Image.open(os.path.join(avatar_dir, "body_knight.png")).convert("RGBA")),
        ("Scout Mantle", Image.open(os.path.join(avatar_dir, "body_scout.png")).convert("RGBA"))
    ]

    hairs = [
        ("Short Spiky", Image.open(os.path.join(avatar_dir, "hair_spiky.png")).convert("RGBA")),
        ("Medium Parted", Image.open(os.path.join(avatar_dir, "hair_parted.png")).convert("RGBA")),
        ("Long Braids", Image.open(os.path.join(avatar_dir, "hair_long.png")).convert("RGBA"))
    ]

    head = Image.open(os.path.join(avatar_dir, "head_base.png")).convert("RGBA")

    grid_w = size * len(hairs)
    grid_h = size * len(bodies)
    grid = Image.new("RGBA", (grid_w, grid_h), (10, 10, 15, 255))

    for row, (body_name, body_img) in enumerate(bodies):
        for col, (hair_name, hair_img) in enumerate(hairs):
            p = Image.new("RGBA", (size, size))
            p.paste(bg, (0, 0))
            p.paste(body_img, (0, 0), body_img)
            p.paste(head, (0, 0), head)
            p.paste(hair_img, (0, 0), hair_img)
            p.paste(frame, (0, 0), frame)

            grid.paste(p, (col * size, row * size))
            print(f"Composited: {body_name} + {hair_name} at grid ({col}, {row})")

    out_path = os.path.join(root_dir, "assets", "matrix_preview.png")
    grid.save(out_path)
    print(f"Matrix saved to {out_path} ({grid_w}x{grid_h})")

if __name__ == "__main__":
    generate_matrix()
