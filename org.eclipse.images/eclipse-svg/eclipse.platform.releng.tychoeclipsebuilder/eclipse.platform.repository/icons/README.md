# How to create an `.icns` macOS app icon for your Eclipse application

> How to make the application icon for macOS using `iconset` & `iconutil`

## Creating SVG image

Follow Apple's [Human Interface Guideline](https://developer.apple.com/design/human-interface-guidelines/app-icons) when designing your icon.

## Exporting PNG images

Save PNG files with the following names & dimensions:

| Name | Dimensions |
| ---- | ---------- |
| `icon_16x16.png` | `16x16` |
| `icon_16x16@2x.png` | `32x32` |
| `icon_32x32.png` | `32x32` |
| `icon_32x32@2x.png` | `64x64` |
| `icon_128x128.png` | `128x128` |
| `icon_128x128@2x.png` | `256x256` |
| `icon_256x256.png` | `256x256` |
| `icon_256x256@2x.png` | `512x512` |
| `icon_512x512.png` | `512x512` |
| `icon_512x512@2x.png` | `1024x1024` |

## Creating an `.iconset`

1. Move all of the images into a new folder
2. Rename the folder to: `Eclipse.iconset`

This will convert the folder of images into an iconset.

## Converting to `.icns`

1. Navigate to the directory containing your `icon.iconset` in the terminal
2. Run `iconutil` with the following command: `iconutil -c icns Eclipse.iconset`
3. Your `Eclipse.icns` will be generated in the current directory