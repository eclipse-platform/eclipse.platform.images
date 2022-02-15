# This Repository
The this repository contains icons to be used in eclipse platform.

This repo stores each icon as a vector graphics (SVG) so it is possible to create multiple versions of the icons in different resolutions.

This repo also contains a Maven-Mojo for the generation of PNGs out of SVG (See https://github.com/eclipse-platform/eclipse.platform.images/blob/master/org.eclipse.images.renderer/src/main/java/org/eclipse/images/renderer/RenderMojo.java).
Another Maven-Mojo also generated galleries to review the icon set on light and dark background (See https://github.com/eclipse-platform/eclipse.platform.images/blob/master/org.eclipse.images.renderer/src/main/java/org/eclipse/images/renderer/GalleryMojo.java).

# What to consider when creating new SVGs
The "UI Graphics" chapter of the [Eclipse User Interface Guidelines](https://wiki.eclipse.org/index.php/User_Interface_Guidelines) gives some advice about the stylistic as well as the implementation aspects of graphics used in eclipse based tools.

In the chapter "Icons Size & Placement" of the guideline states that object type icons should be 15x15 pixels big at max inside the 16x16 canvas. When drawing an object type icon keep in mind that object type icons will get "decorated" e.g. with the "inactive" and the "locked" decorator.
The `tools` folder contains a template for new graphics for model objects. The templates contains some hints about how big the graphics inside the SVGs should be. In addition it has a layer (invisible by default) that contains the `locked` and the `inactive` decorator. This layer can be turned to visible to judge if the icon looks good when it is annotated.

When re-drawing a SVG for an existing GIF use the "color picker" tool of your graphics editing program to match colors of the new SVGs as close as possible with the GIFs.

After the SVGs are finished copy the existing GIFs in the appropriate folder below the `gif` folder of this repo. Then use the steps described below to generate PNGs and galleries. The `sap-16-gifcompare.png` gallery is very helpful when judging if the icons look good on light and dark background. This gallery can also be used to compare the new PNGs with the old GIFs.
