# This Repository - Archived

This repository contains icons that are used in Eclipse Platform projects.
It stores each icon as a Scalable Vector Graphics (SVG) so it is possible to create multiple versions of the icons in different resolutions.
This was useful until Eclipse SWT had support for runtime processing of SVG files with the 2025-06 release.
With the release, the SVG versions of the icons inside this repository have been added to the bundles in which they are used, which made this repository obsolete.
Note that the state of the icons in this repository is not completely identical to icons in the Eclipse release 2025-06 and before.

This repo also contains a Maven-Mojo for the generation of PNGs out of SVG (See https://github.com/eclipse-platform/eclipse.platform.images/blob/master/org.eclipse.images.renderer/src/main/java/org/eclipse/images/renderer/RenderMojo.java).
Another Maven-Mojo also generated galleries to review the icon set on light and dark background (See https://github.com/eclipse-platform/eclipse.platform.images/blob/master/org.eclipse.images.renderer/src/main/java/org/eclipse/images/renderer/GalleryMojo.java).

# How to Find Icons to (Re-)Use

Icons that may be reused or used as a starting point for new icons can be found in the bundles of the Eclipse Platform projects.
One way to browse the icons of an Eclipse installation is to use the [Plug-In Image Browser](https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.pde.doc.user%2Fguide%2Ftools%2Fviews%2Fimage_browser_view.htm) view.
Finally, this repository also remains as an archive and can thus be used for browsing existing icons, they may just not be up to date.

# What to Consider When Creating New SVGs

The "UI Graphics" chapter of the [Eclipse User Interface Guidelines](https://eclipse-platform.github.io/ui-best-practices) gives some advice about the stylistic as well as the implementation aspects of graphics used in eclipse based tools.

In the chapter "Icons Size & Placement" of the guideline states that object type icons should be 15x15 pixels big at max inside the 16x16 canvas. When drawing an object type icon keep in mind that object type icons will get "decorated" e.g. with the "inactive" and the "locked" decorator.
The `tools` folder contains a template for new graphics for model objects. The templates contains some hints about how big the graphics inside the SVGs should be. In addition it has a layer (invisible by default) that contains the `locked` and the `inactive` decorator. This layer can be turned to visible to judge if the icon looks good when it is annotated.

When re-drawing an SVG for an existing GIF, use the "color picker" tool of your graphics editing program to match colors of the new SVGs as close as possible with the GIFs.

After the SVGs are finished, copy the existing GIFs in the appropriate folder below the `gif` folder of this repo. Then use the steps described below to generate PNGs and galleries. The `sap-16-gifcompare.png` gallery is very helpful when judging if the icons look good on light and dark background. This gallery can also be used to compare the new PNGs with the old GIFs.
