from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
PUBLIC = ROOT / "frontend" / "apps" / "storefront" / "public"
SIZE = 1024
SCALE = 3


def point(x, y):
    return (round(x * SCALE), round(y * SCALE))


def box(values):
    return tuple(round(value * SCALE) for value in values)


def rounded(draw, values, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box(values), radius=round(radius * SCALE), fill=fill, outline=outline, width=round(width * SCALE))


def polygon(draw, values, fill):
    draw.polygon([point(x, y) for x, y in values], fill=fill)


def gradient(size, start, end, horizontal=False):
    image = Image.new("RGBA", size)
    pixels = image.load()
    width, height = size
    for y in range(height):
        for x in range(width):
            t = x / max(width - 1, 1) if horizontal else y / max(height - 1, 1)
            color = tuple(round(a * (1 - t) + b * t) for a, b in zip(start, end))
            pixels[x, y] = (*color, 255)
    return image


def make_icon():
    # Warm paper and a cool lilac cast echo the frosted product surfaces in the app.
    image = gradient((SIZE * SCALE, SIZE * SCALE), (246, 247, 243), (218, 224, 220))
    glow = Image.new("RGBA", image.size)
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse(box((520, -190, 1200, 460)), fill=(218, 188, 222, 76))
    glow_draw.ellipse(box((-250, 610, 530, 1280)), fill=(174, 199, 186, 70))
    glow = glow.filter(ImageFilter.GaussianBlur(120 * SCALE))
    image = Image.alpha_composite(image, glow)

    shadow = Image.new("RGBA", image.size)
    shadow_draw = ImageDraw.Draw(shadow)
    rounded(shadow_draw, (151, 171, 874, 887), 190, (31, 42, 36, 74))
    shadow = shadow.filter(ImageFilter.GaussianBlur(30 * SCALE))
    image = Image.alpha_composite(image, shadow)

    # The dark glass tile keeps the mark legible at small sizes.
    tile = gradient((SIZE * SCALE, SIZE * SCALE), (53, 64, 57), (25, 31, 28))
    tile_alpha = Image.new("L", image.size)
    tile_alpha_draw = ImageDraw.Draw(tile_alpha)
    rounded(tile_alpha_draw, (132, 118, 892, 906), 214, 238)
    tile.putalpha(tile_alpha)
    image = Image.alpha_composite(image, tile)

    draw = ImageDraw.Draw(image, "RGBA")
    rounded(draw, (132, 118, 892, 906), 214, None, (255, 255, 255, 64), 2)
    rounded(draw, (153, 140, 871, 884), 193, None, (255, 255, 255, 25), 1)

    # Frosted highlight in the upper-left corner adds depth without a heavy bevel.
    sheen = Image.new("RGBA", image.size)
    sheen_draw = ImageDraw.Draw(sheen, "RGBA")
    rounded(sheen_draw, (178, 163, 545, 448), 126, (255, 255, 255, 30))
    sheen = sheen.filter(ImageFilter.GaussianBlur(18 * SCALE))
    image = Image.alpha_composite(image, sheen)
    draw = ImageDraw.Draw(image, "RGBA")

    # The monogram follows the LECTUS wordmark: an editorial L and its plum period.
    mark = Image.new("RGBA", image.size)
    mark_draw = ImageDraw.Draw(mark, "RGBA")
    polygon(mark_draw, [(324, 255), (464, 255), (464, 652), (710, 652), (710, 779), (324, 779)], (245, 246, 240, 250))
    mark_draw.line([point(346, 278), point(346, 726)], fill=(255, 255, 255, 100), width=3 * SCALE)
    mark_draw.line([point(491, 748), point(683, 748)], fill=(255, 255, 255, 80), width=3 * SCALE)
    image = Image.alpha_composite(image, mark)

    dot = Image.new("RGBA", image.size)
    dot_draw = ImageDraw.Draw(dot, "RGBA")
    dot_draw.ellipse(box((695, 220, 783, 308)), fill=(115, 26, 89, 80))
    dot = dot.filter(ImageFilter.GaussianBlur(10 * SCALE))
    image = Image.alpha_composite(image, dot)
    draw = ImageDraw.Draw(image, "RGBA")
    draw.ellipse(box((679, 202, 767, 290)), fill=(183, 43, 132, 255))
    draw.ellipse(box((697, 218, 727, 248)), fill=(239, 157, 205, 150))

    return image.resize((SIZE, SIZE), Image.Resampling.LANCZOS).convert("RGB")


if __name__ == "__main__":
    icon = make_icon()
    outputs = {
        "lectus-app-icon.png": 1024,
        "lectus-app-icon-512.png": 512,
        "lectus-app-icon-192.png": 192,
        "lectus-mini-program-icon.png": 144,
    }
    for name, size in outputs.items():
        icon.resize((size, size), Image.Resampling.LANCZOS).save(PUBLIC / name, optimize=True)
