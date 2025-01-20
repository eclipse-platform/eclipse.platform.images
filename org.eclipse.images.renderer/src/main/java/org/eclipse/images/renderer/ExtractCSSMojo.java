/*******************************************************************************
 * (c) Copyright 2016, 2025 l33t labs LLC and others.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     l33t labs LLC and others - initial contribution
 *******************************************************************************/

package org.eclipse.images.renderer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.svg.SVGDocument;

/**
 * Extracts CSS styles from SVG icons and places them in an external stylesheet.
 */
@Mojo(name="extract-css")
@Execute(goal="extract-css", phase = LifecyclePhase.GENERATE_RESOURCES)
public class ExtractCSSMojo extends AbstractMojo {

	/** Maven logger */
	Log log;

	/** A list of directories with svg sources to extract from. */
	private List<IconEntry> icons = new ArrayList<>();

	/** */
	private File iconDirectoryRoot;

	/**
	 * 
	 */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		log = getLog();

		String workingDirectory = System.getProperty("user.dir");
		String sourceDir = "eclipse-svg";
		String targetDir = "eclipse-css";
		String dirSuffix = "/" + targetDir + "/";
		File outputDir = new File(workingDirectory + dirSuffix);
		iconDirectoryRoot = new File(sourceDir + "/");

		if (!iconDirectoryRoot.exists()) {
			log.error("Source directory' " + sourceDir + "' does not exist.");
			return;
		}

		// Search each subdir in the root dir for svg icons
		for (File file : iconDirectoryRoot.listFiles()) {
			if (!file.isDirectory()) {
				continue;
			}

			String dirName = file.getName();

			// Where to place the rendered icon
			String child = dirName;
			File outputBase = new File(outputDir, child);

			IconGatherer.gatherIcons(icons, "svg", file, file, outputBase, true, FolderState.include);
		}

		ForkJoinPool forkJoinPool = new ForkJoinPool(4);

		try {
			forkJoinPool.submit(() -> {
				icons.parallelStream().forEach(this::createCSS);
				return null;
			}).get();
		} catch (Exception e) {
			log.error("Error while rendering icons: " + e.getMessage(), e);
		}
	}

	/**
	 * 
	 * @param icon
	 */
	public void createCSS(IconEntry icon) {
		if (icon == null) {
			log.error("Null icon definition, skipping.");
			return;
		}

		if (icon.inputPath == null) {
			log.error("Null icon input path, skipping: " + icon.nameBase);
			return;
		}

		if (!icon.inputPath.exists()) {
			log.error("Input path specified does not exist, skipping: " + icon.nameBase);
			return;
		}

		File svgInput = icon.inputPath;
		String absolutePath = icon.inputPath.getAbsolutePath();

		String styled = absolutePath.replace("/icons/", "/styles/stock/");
		String cssDir = styled.replace("eclipse-svg", "eclipse-css");
		String css = cssDir.replace(".svg", ".scss");

		File newOutput = new File(css);

		icon.outputPath = newOutput;

		if (icon.outputPath != null && !icon.outputPath.exists()) {
			File parent = icon.outputPath.getParentFile();
			parent.mkdirs();
		}
		 
		try (StringWriter stream = new StringWriter()) {
			// Create the document to rasterize
			SVGDocument svgDocument = generateSVGDocument(icon);

			if (svgDocument == null) {
				return;
			}

			if (icon.outputPath.exists() && !icon.outputPath.delete()) {
				throw new MojoExecutionException(
						"Couldn't delete existing css for " + icon.outputPath.getAbsolutePath());
			}

			if (!icon.outputPath.createNewFile()) {
				throw new MojoExecutionException("Couldn't create output css for " + icon.outputPath.getAbsolutePath());
			}

			stream.write("@import \"stock\";\n\n");

			URI rootUri = iconDirectoryRoot.toURI();
			URI outputUri = svgInput.getParentFile().toURI();

			String relativePath = rootUri.relativize(outputUri).getPath();
			String backSteps = countBackSteps(svgInput);
			String cssUrlPath = backSteps + "eclipse-svg/" + relativePath + "/" + svgInput.getName();

			SVGDocument doc = generateSVGDocument(icon);

			Element documentElement = doc.getDocumentElement();

			writeStyles(cssUrlPath, documentElement, stream);

			log.info("Creating css for: " + css);
			
			Files.writeString(newOutput.toPath(), stream.toString());
		} catch (Exception e) {
			log.error("Error creating CSS: " + e.getMessage(), e);
		}
	}

	private String countBackSteps(File file) {
		File currentDir = file;
		String backStep = "../";
		while (!"eclipse-svg".equals(currentDir.getName())) {
			currentDir = currentDir.getParentFile();
			backStep += "../";
		}

		return backStep;
	}

	/**
	 * <p>
	 * Extracts and formats the inline styles within the supplied node, which
	 * are written to the writer.
	 * </p>
	 * 
	 * @param writer
	 * 
	 * @throws IOException
	 */
	private void writeStyles(String urlPath, Node node, Writer writer) throws IOException {
		NodeList nodes = node.getChildNodes();
		int len = nodes.getLength();
		for (int i = 0; i < len; i++) {
			Node item = nodes.item(i);

			if (!(item instanceof Element elem)) {
				continue;
			}

			Attr idAttr = elem.getAttributeNodeNS(null, "id");
			if (idAttr != null && idAttr.getValue() != null && !"".equals(idAttr.getValue())) {
				Attr styleAttr = elem.getAttributeNodeNS(null, "style");

				if (styleAttr != null && styleAttr.getValue() != null && !"".equals(styleAttr.getValue())) {
					String style = styleAttr.getValue();
					String fixedStyle = style.replaceAll("-inkscape-", "inkscape-");
					writer.write("#" + idAttr.getValue() + " {\n");
					writer.write(formatStyles(fixedStyle, urlPath) + "\n");
					writer.write("}\n\n");
					
					log.debug("Writing: " + "#" + idAttr.getValue() + " {\n");
				}
			}

			writeStyles(urlPath, item, writer);
		}
	}

	/**
	 * <p>
	 * Formats all of the inline styles in the supplied style as entries in a
	 * CSS ID definition block.
	 * </p>
	 * 
	 * @param styles
	 *            a semi colon delimited list of styles, as used with inline
	 *            definitions
	 * 
	 * @return a formatted list of CSS styles
	 */
	private String formatStyles(String styles, String urlPath) {
		Stream<String> rawEntries = Arrays.stream(styles.trim().split(";"));

		return rawEntries.map(style -> createStyleEntry(style, urlPath))
						.filter(this::isValidStyle)
						.collect(Collectors.joining("\n"));
	}

	private boolean isValidStyle(String style) {
		return style != null && !"".equals(style);
	}

	/**
	 * <p>
	 * Creates a formatted CSS style entry for a style definition.
	 * </p>
	 * 
	 * @param styleLine
	 *            the existing style line
	 * 
	 * @return a formatted CSS style entry
	 */
	private String createStyleEntry(String styleLine, String urlPath) {
		String[] split = styleLine.trim().split(":");

		if (split.length != 2) {
			log.error("Invalid style: " + styleLine);
			return "";
		}

		String name = split[0].trim();
		String value = split[1].trim();

		if (value.startsWith("url(#")) {
			value = repathUrl(value, urlPath);
		}

		return "\t" + name + ": " + value + ";";
	}

	/**
	 * Adds paths to the related SVG icon, which is required for styling
	 * references (gradients, etc).
	 * 
	 * @param url
	 *            the current url
	 * @param url
	 *            the path to the related SVG to prepend to the url.
	 */
	private String repathUrl(String url, String urlPath) {
		return url.replace("url(", "url(" + urlPath);
	}

	/**
	 * <p>
	 * Generates a Batik SVGDocument for the supplied IconEntry's input file.
	 * </p>
	 *
	 * @param icon
	 *            the icon entry to generate an SVG document for
	 *
	 * @return a batik SVGDocument instance or null if one could not be
	 *         generated
	 */
	private SVGDocument generateSVGDocument(IconEntry icon) {
		// Load the document and find out the native height/width
		// We reuse the document later for rasterization
		SVGDocument svgDocument = null;
		
		try (InputStream iconDocumentStream = Files.newInputStream(icon.inputPath.toPath())){

			String parser = XMLResourceDescriptor.getXMLParserClassName();
			SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);

			// What kind of URI is batik expecting here??? the docs don't say
			svgDocument = f.createSVGDocument("file://" + icon.nameBase + ".svg", iconDocumentStream);
		} catch (Exception e) {
			log.error("Error parsing SVG icon document: " + e.getMessage(), e);
			return null;
		}
		
		return svgDocument;
	}

}
