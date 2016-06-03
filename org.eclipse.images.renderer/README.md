org.eclipse.images.renderer
==============================

org.eclipse.images provides the a Maven generator of svg images located in the org.eclipse.images plug-in.

org.eclipse.images.renderer plug-in usage
--------------------------------------------

Install the org.eclipse.images.renderer plug-in:

cd org.eclipse.images.renderer
mvn clean install

After the renderer plugin is installed, change into the root of the images project:

cd org.eclipse.images

** Work around bug 493994 in Apache Batik [3]: **
Apache Batik produces bad PNGs for some SVGs created by Inkscape.
The workaround is to run the Ant script /org.eclipse.images/build.xml.
This may modify some SVG files. You should commit these changes.

Finally, execute the icon render mojo with:

mvn org.eclipse.images:org.eclipse.images.renderer:render-icons

This renders all of the svg icons in "eclipse-svg" into the "eclipse-png" folder of the org.eclipse.images project, maintaining the directory structure (i.e. eclipse-svg/icondir will be rendered into org.eclipse.images/eclipse-png/icondir).

Supported runtime arguments (e.g mvn -Declipse.svg.scale=2 ...):

eclipse.svg.scale           - an integer that is used to scale output images (e.g. 2 will render a 16x16 svg at 32x32)
eclipse.svg.createFragments - a boolean that specifies whether to create separate fragments or putting the high resolution icons next to the low-resolution icons (defaults to "true")
eclipse.svg.renderthreads   - an integer that specifies how many threads to use simultaneously while rendering
eclipse.svg.sourcedirectory - a string that specifies the directory name where the SVGs are taken from (defaults to "eclipse-svg")
eclipse.svg.targetdirectory - a string that specifies the directory name where the PNGs are written to (defaults to "eclipse-png")

Once the icon sets have been rendered, you can create galleries for evaluation and feedback with the gallery mojo:

mvn org.eclipse.images:org.eclipse.images.renderer:render-galleries

This will create a set of galleries and gif comparisons comprised of the newly rendered icons, located in the target/ output directory.

Supported runtime arguments :

eclipse.svg.pngdirectory - a string that specifies the directory name where the PNGs are taken from (defaults to "eclipse-png")
eclipse.svg.gifdirectory - a string that specifies the directory name where the GIFs are taken from (defaults to "eclipse-gif")

License
-------

[Eclipse Public License (EPL) v1.0][2]

[1]: http://wiki.eclipse.org/Platform_UI
[2]: http://wiki.eclipse.org/EPL
[3]: https://bugs.eclipse.org/493994