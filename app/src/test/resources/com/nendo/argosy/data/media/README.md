# 3DS banner parser fixture

`argosy-banner-fixture.bnr` is generated from original geometric artwork by
`scripts/generate_3ds_banner_fixture.py` using bannertool 1.2.3. Bannertool is
MIT-licensed: https://github.com/carstene1ns/3ds-bannertool/tree/1.2.3

The fixture contains no game-derived content. Its silent CWAV is included only
to satisfy bannertool's input requirements and exercise the CGFX resource
boundary; the application does not play it. The fixture exercises a
tool-generated CBMD container with a compressed CGFX image resource, not a
commercial-game banner.

## Renderer support

- Real banners: the decompressed CGFX is composited through the banner's own
  3D scene (DATA -> DICT[0] CMDL / DICT[1] TXOB) through the Home Menu's camera,
  producing a still and a 24-frame rotation GIF. See `ThreeDsBannerRenderer` and
  `CgfxBannerScene`.
- bannertool image banners: fallback single-texture path (256x128 RGBA4444 /
  RGB565 tile decoding).
- Arbitrary game-specific CGFX scenes: handled by the scene compositor; proving
  it against real commercial banners is pending validation.

## Cache

Successful renders are cached as `banner.png` (still) and `banner.gif` (rotation)
alongside the raw CGFX in the extracted-media cache, and are consumed through
`EmbeddedMediaRepository` -> `ImageCacheManager` when the "use embedded 3DS banner
for hero" preference is enabled.

## Regenerate it with

```powershell
python scripts\generate_3ds_banner_fixture.py --bannertool C:\path\to\bannertool.exe
```
