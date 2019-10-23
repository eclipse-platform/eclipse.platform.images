org.eclipse.images.renderer
==============================

org.eclipse.images provides the a Maven generator of svg images located in the org.eclipse.images plug-in.

org.eclipse.images.renderer plug-in usage
--------------------------------------------

Install the org.eclipse.images.renderer plug-in:

Go to the project root i.e. eclipse.platform.images:

cd eclipse.platform.images

and run:

mvn clean install

After the renderer plugin is installed, change into the root of the images project:

cd org.eclipse.images

Finally, execute the icon render mojo with:

mvn org.eclipse.images:org.eclipse.images.renderer:render-icons

This renders all of the svg icons in "eclipse-svg" into the "eclipse-png" folder of the org.eclipse.images project, maintaining the directory structure (i.e. eclipse-svg/icondir will be rendered into org.eclipse.images/eclipse-png/icondir).

To render scaled images execute the icon render mojo with:

mvn org.eclipse.images:org.eclipse.images.renderer:render-icons -Declipse.svg.scale=2 -Declipse.svg.createFragments=false

This renders scaled images out of all of the svg icons in "eclipse-svg" into the "eclipse-png" folder of the org.eclipse.images project, maintaining the directory structure (i.e. eclipse-svg/icondir will be rendered into org.eclipse.images/eclipse-png/icondir).

Supported runtime arguments are:

eclipse.svg.scale           - an integer that is used to scale output images (e.g. 2 will render a 16x16 svg at 32x32)
eclipse.svg.createFragments - a boolean that specifies whether to create separate fragments or putting the high resolution icons next to the low-resolution icons (defaults to "true")
eclipse.svg.renderthreads   - an integer that specifies how many threads to use simultaneously while rendering
eclipse.svg.sourcedirectory - a string that specifies the directory name where the SVGs are taken from (defaults to "eclipse-svg")
eclipse.svg.targetdirectory - a string that specifies the directory name where the PNGs are written to (defaults to "eclipse-png")

Note: The renderer always renders all svg icons. The renderer may produce binary different png files (that look identical) on different hardware. So it's a good idea only to commit the files that "really" did change and reset the changes to all the other files.

SASS/CSS Stylesheet Rendering (Experimental)

Icons can be rendered using an alternate stylesheet theme, which are located in eclipse-css. Rendering with stylesheets
requires the open source SASS stylesheet preprocessor to be installed on your system and available on your system path.

To enable stylesheet rendering, simply specify the eclipse.svg.stylesheet property when invoking the render-icons mojo.
If no stylesheet theme is specified, the inline styles from the SVG document are used.

The original icon theme is available as the "stock" theme, located in eclipse-css/styles.

Stylesheet options:
eclipse.svg.stylesheet - the name of a style theme in the eclipse-css/styles folder to use when rendering icons
eclipse.svg.stylesheet.regenerate - if true, all SASS stylesheets will be processed into CSS, replacing the current CSS files

New themes can be created by using the Create CSS Theme mojo:

mvn org.eclipse.images:org.eclipse.images.renderer:create-css-theme -Declipse.svg.newThemeName=myThemeName

This will create a copy of the "stock" theme, which is the original set of Eclipse styles for the icons renamed with
"myThemeName." The resulting SASS styles are available in each icon root for tweaking and modification.

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