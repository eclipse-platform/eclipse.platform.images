# org.eclipse.images

org.eclipse.images provides svg versions of the Eclipse SDK images. The "eclipse-svg" folder contains svg version of the icons, while the "eclipse-png" folder contain generated png files. These icons can be used in custom Eclipse plug-ins or in arbitrary rich client applications.
The "tools" folder contains the eclipse style color palette in differnent file formats:
- As GIMP palette (file extension ".gpl") (can be used in Inkscape as well)
- As Adobe Illustrator document (file extension ".ai")
- As Adobe color file (file extension ".aco")

# Generate png files

To generate the png files based on the svg files, see the README.md file in the org.eclipse.images.renderer plug-in.

When you add a file "example.svg", also add both the generated "example.png" and "example@2x.png".

##License

[Eclipse Public License (EPL) v2.0][1]

[1]: http://wiki.eclipse.org/EPL
