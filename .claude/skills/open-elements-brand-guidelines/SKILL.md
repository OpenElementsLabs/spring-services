---
name: open-elements-brand-guidelines
license: Apache-2.0
metadata:
  source: https://github.com/open-elements/claude-base
  author: Open Elements
description: Applies the official brand colors and typography of Open Elements to any sort of artifact that may benefit from having the look-and-feel of Open Elements. Use it when brand colors or style guidelines, visual formatting, or company design standards apply.
---

# Open Elements Brand Styling

## Overview

To access the official brand identity and style resources of Open Elements, use this skill.

## Online Design Reference

The canonical Open Elements corporate design reference is published online at:

- **https://design.production.open-elements.cloud** — Main entry point for the corporate design system
- **https://design.production.open-elements.cloud/color.html** — Authoritative color palette reference
- **https://design.production.open-elements.cloud/components.html** — UI components and their usage

When working on web projects or UI artifacts, treat this website as the source of truth. If the values in this skill ever diverge from the online reference, the online reference wins — fetch it to verify before applying styles to production artifacts.

## Brand Guidelines

### Colors

**Main Colors:**

- Dark: `#020144` - Can be used as backgrounds in header footer or for example as headline text color.
  In diagrams / technical illustrations often used for note texts or arrows.
- Black: `#000000` - Normal text color on bright backgrounds
- White: `#ffffff` - Normal background color
- Mid Gray: `#b0aea5` - Secondary elements
- Light Gray: `#e8e6dc` - Subtle backgrounds
- Primary Green: `#5CBA9E` - Can be used in texts, for highlighting or as background color for example in diagrams
- Primary Red: `#E63277` - Can be used in texts, for highlighting or as background color for example in diagrams

**Accent Colors:**

- Blue: `#5DB9F5` - Primary accent
- Yellow: `#F1E34B` - Secondary accent

**Light variants:**
(green) #BEE3D8, (red) #F5ADC9, (blue) #BEE3FB, (yellow) #F9F4B7

**Lighter variants:**
(green) #DEF1EC, (red) #FAD6E4, (blue) #DFF1FD, (yellow) #FCF9DB

**Dark variants:**
(green) #3E9279, (red) #BB1756, (blue) #2496EF, (yellow) #DCCB12

### CSS Custom Properties

When using the brand colors in web projects, define them as CSS custom properties:

```css
:root {
  --oe-dark: #020144;
  --oe-black: #000000;
  --oe-white: #ffffff;
  --oe-gray-mid: #b0aea5;
  --oe-gray-light: #e8e6dc;
  --oe-green: #5CBA9E;
  --oe-green-light: #BEE3D8;
  --oe-green-lighter: #DEF1EC;
  --oe-green-dark: #3E9279;
  --oe-red: #E63277;
  --oe-red-light: #F5ADC9;
  --oe-red-lighter: #FAD6E4;
  --oe-red-dark: #BB1756;
  --oe-blue: #5DB9F5;
  --oe-blue-light: #BEE3FB;
  --oe-blue-lighter: #DFF1FD;
  --oe-blue-dark: #2496EF;
  --oe-yellow: #F1E34B;
  --oe-yellow-light: #F9F4B7;
  --oe-yellow-lighter: #FCF9DB;
  --oe-yellow-dark: #DCCB12;
}
```

### Illustration Colors

The following colors are derived from analyzing the full Open Elements SVG illustration library (~210 illustrations).
They represent the actual color palette used in the corporate illustrations and should be used when creating diagrams, charts, graphs, or any visual graphics to match the Open Elements illustration style.

**Primary illustration fills (most frequently used across all illustrations):**

| Color | Hex | Usage |
|-------|-----|-------|
| Green (primary fill) | `#5CBA9E` | The dominant fill color, used in ~60% of all illustrations |
| Light mint | `#BEE3D8` | Large area fills, backgrounds of highlighted sections |
| Lighter mint | `#DEF1EC` | Subtle background fills, secondary areas |
| Medium mint | `#9DD6C5` | Intermediate green tone, frequently used alongside primary green |
| Sky blue | `#5DB9F5` | Secondary fill color for contrast elements |
| Light blue | `#BEE3FB` | Large area fills in blue-themed sections |
| Lighter blue | `#DFF1FD` | Subtle blue backgrounds |
| Medium blue | `#9ED5F9` | Intermediate blue tone, used for highlights and accents |

**Secondary illustration fills (used for emphasis or thematic elements):**

| Color | Hex | Usage |
|-------|-----|-------|
| Yellow | `#F1E34B` | Accent for stars, light bulbs, idea elements |
| Pale yellow | `#F9F4B7` | Subtle yellow backgrounds |
| Cream | `#FCF9DB` | Very subtle warm backgrounds |
| Hot pink | `#E63277` | Alert or energy accents |
| Dark pink | `#BB1756` | Danger, warning, or evil-themed elements |
| Light pink | `#F084AD` | Softer pink accents |
| Pale pink | `#FAD6E4` | Subtle pink backgrounds |
| Bright cyan | `#0FBBFB` | Used in newer illustration series as sky blue accent |
| Dark blue | `#15649F` | Technology, blockchain, and data-themed illustrations |
| Bright blue | `#2496EF` | Active/highlighted blue elements |

**Illustration outline and neutral colors:**

| Color | Hex | Usage |
|-------|-----|-------|
| Black | `#000000` | Primary outline/stroke color (~80% of illustrations) |
| Dark navy | `#020144` | Alternative outline/stroke color (~30% of illustrations) |
| Light gray | `#DADADA` | Neutral fills for inactive or background elements |
| White | `#FFFFFF` | Interior fills for contrast, eyes, highlights |

**Dark accent tones (used sparingly for depth):**

| Color | Hex | Usage |
|-------|-----|-------|
| Dark green | `#3E9279` | Shadows or dark-side fills on green elements |
| Very dark green | `#296251` | Deep shadows, rare accent |
| Dark olive | `#93870C` | Dark variant for yellow/gold elements |
| Teal | `#27BD9D` | Alternative green, slightly brighter than primary |

### Illustration Style Guide

The following style parameters are derived from the Open Elements SVG illustration library and should be applied when creating diagrams, charts, graphs, or any visual graphics.

**Stroke / Outlines:**

- Stroke width: ~1% of the canvas width (e.g. `16.67px` on a `1668px` canvas, `25px` on a `2500px` canvas, `6px` on a `600px` canvas)
- Stroke color: `#000000` (black) or `#020144` (dark navy) — use one consistently within a single graphic
- Stroke line cap: `round`
- Stroke line join: `round`
- Stroke miter limit: `1.5`

**Fill Style:**

- Use flat fills only — no gradients, no textures, no patterns
- No opacity/transparency effects (all fills are fully opaque)
- Use `fill-rule: evenodd`

**Color Usage Hierarchy:**

1. Use `#5CBA9E` (green) as the dominant fill color
2. Use lighter green tones (`#BEE3D8`, `#DEF1EC`, `#9DD6C5`) for secondary areas
3. Use blue tones (`#5DB9F5`, `#BEE3FB`, `#DFF1FD`, `#9ED5F9`) for contrasting elements
4. Use yellow (`#F1E34B`) and pink (`#E63277`, `#BB1756`) sparingly as accents
5. Use `#DADADA` for neutral/inactive elements
6. Keep outlines in `#000000` or `#020144`

**General Principles:**

- Illustrations are clean, flat-design vector graphics with bold rounded outlines
- No drop shadows, no glow effects, no 3D effects
- Rounded stroke caps and joins give a friendly, approachable look
- White space is used generously inside illustrations
- Color palette is intentionally limited per illustration (typically 3-5 colors plus black/white)

### Typography

- **Headings**: Montserrat (or Lato)
- **Body Text**: Lato
- **Source Code**: Source_Code_Pro
- In diagrams notes can be written in Permanent_Marker

The TTF font files are shipped with this skill in the `fonts/` subfolder and should be used directly whenever an artifact needs to embed or reference the brand fonts (PDF generation, image rendering, slide decks, offline diagram tools). Prefer the bundled files over re-downloading from Google Fonts.

- **fonts/Montserrat/** — Variable font (`Montserrat-VariableFont_wght.ttf`, `Montserrat-Italic-VariableFont_wght.ttf`) plus static weights in `fonts/Montserrat/static/` (Thin → Black, including italics)
- **fonts/Lato/** — Static weights: Thin, Light, Regular, Bold, Black (each with italic variants)
- **fonts/Source_Code_Pro/** — Variable font plus static weights in `fonts/Source_Code_Pro/static/` (ExtraLight → Black, including italics)
- **fonts/Permanent_Marker/** — Single regular weight (`PermanentMarker-Regular.ttf`)

All fonts are licensed under the SIL Open Font License (OFL); the license files (`OFL.txt` / `LICENSE.txt`) live alongside the font files in each subfolder. The same families are also available on [Google Fonts](https://fonts.google.com/) for web embedding.

### Logo

The open elements logo is available in the folder of this skill.
It is provided in PNG and SVG format.
All PNGs have a transparent background.
If possible, use the SVG version.

Here is an overview of the SVG logo files.
All PNG files are available in the same folder and have the same name as the SVG files.
- **open-elements-logo/logo-landscape-dark-background.svg**: Landscape logo with dark background
- **open-elements-logo/logo-landscape-light-background.svg**: Landscape logo with light background
- **open-elements-logo/logo-portrait-dark-background.svg**: Portrait logo with dark background. This is preferred for square logo placements.
- **open-elements-logo/logo-portrait-light-background.svg**: Portrait logo with light background. This is preferred for square logo placements.

On a dark backround only the *-dark-background.* logos must be used.
On a light background only the *-light-background.* logos must be used.

Next to that the graphic part of the logo without the "Open Elements" text is available (for example for use as fav-icon)
- **open-elements-logo/logo-icon.png**: 1024x1024 PNG file
- **open-elements-logo/logo-icon-0.5x.png**: 0.5x PNG file
- **open-elements-logo/logo-icon-0.25x.png**: 0.25x PNG file
- **open-elements-logo/logo-icon-0.33x.png**: 0.33x PNG file
- **open-elements-logo/logo-icon-0.75x.png**: 0.75x PNG file

### Sample Artifacts

The `samples/` folder contains reference artifacts produced by Open Elements. Use them as **templates** when generating similar deliverables (layout, tone, structure, color usage) and as **placeholders** when a real artifact is not yet available (e.g. embedding a sample illustration into a draft slide deck before the final asset is provided).

These samples are not meant to be redistributed as-is — they exist to seed new work in the correct visual language.

**Document templates:**

- **samples/pitch-deck-sample.pdf** — Reference pitch deck. Use as a template for slide structure, typography rhythm, illustration placement, and overall layout when generating new decks.
- **samples/flyer-example.pdf** — Reference flyer. Use as a template for printed/PDF one-pagers (proportions, headline treatment, color blocking).
- **samples/colors.png** — Visual color swatch reference matching the palette defined in the [Colors](#colors) section above.

**Illustration samples (`samples/illustrations/`):**

Flat-design vector illustrations that follow the [Illustration Style Guide](#illustration-style-guide). Use these as placeholders or as the stylistic reference when generating new illustrations.

- **sample-illustration-1.svg** through **sample-illustration-5.svg** — General-purpose illustration samples
- **sample-illustration-open-source-universe.svg** — Themed illustration (open-source universe)

**Pictogram samples (`samples/pictograms/`):**

Single-concept icon-style graphics in the Open Elements visual language. Use as placeholders for concept icons in slides, diagrams, or web content.

- **sample-pictogram-1.svg** through **sample-pictogram-10.svg** — Pictogram samples

When inserting a sample as a placeholder, mark it clearly in the surrounding artifact (e.g. a comment, alt text, or a TODO note) so the final asset is swapped in before publication.
