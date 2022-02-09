/*******************************************************************************
 * (c) Copyright 2015 l33t labs LLC and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     l33t labs LLC and others - initial contribution
 *******************************************************************************/

package org.eclipse.images.renderer;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.gvt.renderer.ImageRenderer;
import org.apache.batik.gvt.renderer.StaticRenderer;
import org.apache.batik.transcoder.ErrorHandler;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.svg.SVGDocument;

import com.jhlabs.image.ContrastFilter;
import com.jhlabs.image.GrayscaleFilter;
import com.jhlabs.image.HSBAdjustFilter;

/**
 * <p>
 * Mojo which renders SVG icons into PNG format.
 * </p>
 *
 * @goal render-icons
 * @phase generate-resources
 */
public class RenderMojo extends AbstractMojo {

	/** Maven logger */
	Log log;

	/** Used for high resolution (HiDPI) rendering support. */
	public static final String ECLIPSE_SVG_SCALE = "eclipse.svg.scale";

	/** Used to specify the number of render threads when rasterizing icons. */
	public static final String RENDERTHREADS = "eclipse.svg.renderthreads";

	/** Used to specify the directory name where the SVGs are taken from. */
	public static final String SOURCE_DIR = "eclipse.svg.sourcedirectory";

	/** Used to specify the directory name where the PNGs are saved to. */
	public static final String TARGET_DIR = "eclipse.svg.targetdirectory";

	/**
	 * Used to specify whether to create separate fragments or putting the high
	 * resolution icons next to the low-resolution icons.
	 */
	public static final String CREATE_FRAGMENTS = "eclipse.svg.createFragments";

	/** Used to specify whether to use stylesheets. */
	public static final String USE_STYLESHEET = "eclipse.svg.stylesheet";

	/**
	 * Used to specify whether to recreate css styles with SASS or use the
	 * existing css files.
	 */
	public static final String REGENERATE_STYLES = "eclipse.svg.stylesheet.regenerate";

	/** A list of directories with svg sources to rasterize. */
	private List<IconEntry> icons;

	/** The number of threads to use when rendering icons. */
	private int threads;

	private final class CustomTranscoder extends PNGTranscoder {
		@Override
		protected ImageRenderer createRenderer() {
			ImageRenderer renderer = new StaticRenderer();

			RenderingHints renderHints = renderer.getRenderingHints();

			renderHints.add(
					new RenderingHints(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF));

			renderHints.add(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY));

			renderHints.add(new RenderingHints(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE));

			renderHints.add(
					new RenderingHints(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC));

			renderHints.add(new RenderingHints(RenderingHints.KEY_ALPHA_INTERPOLATION,
					RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY));

			renderHints.add(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));

			renderHints.add(
					new RenderingHints(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY));

			renderHints.add(new RenderingHints(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE));

			renderHints.add(new RenderingHints(RenderingHints.KEY_FRACTIONALMETRICS,
					RenderingHints.VALUE_FRACTIONALMETRICS_ON));

			renderer.setRenderingHints(renderHints);

			return renderer;
		}
	}

	/**
	 * A counter used to keep track of the number of rendered icons. Atomic is
	 * used to make it easy to access between threads concurrently.
	 */
	private AtomicInteger counter;

	/** List of icons that failed to render, made safe for parallel access */
	List<IconEntry> failedIcons = Collections.synchronizedList(new ArrayList<IconEntry>(5));

	/** The amount of scaling to apply to rasterized images. */
	private double outputScale;

	/** An absolute path to a stylesheet to use when rendering icons. */
	private String stylesheetName;

	/**
	 * If true, existing css stylesheets will be deleted and recreated with SASS
	 * for the supplied theme, only used during CSS-based rendering.
	 */
	private boolean regenerateCss = false;

	/**
	 * @return the number of icons rendered at the time of the call
	 */
	public int getIconsRendered() {
		return counter.get();
	}

	/**
	 * @return the number of icons that failed during the rendering process
	 */
	public int getFailedIcons() {
		return failedIcons.size();
	}

	/**
	 * <p>
	 * Generates raster images from the input SVG vector image.
	 * </p>
	 *
	 * @param icon
	 *            the icon to render
	 * @param grayFilter
	 * @param desaturator
	 * @param decontrast
	 */
	public void rasterize(IconEntry icon) {
		if (icon == null) {
			log.error("Null icon definition, skipping.");
			failedIcons.add(icon);
			return;
		}

		if (icon.inputPath == null) {
			log.error("Null icon input path, skipping: " + icon.nameBase);
			failedIcons.add(icon);
			return;
		}

		if (!icon.inputPath.exists()) {
			log.error("Input path specified does not exist, skipping: " + icon.nameBase);
			failedIcons.add(icon);
			return;
		}

		if (icon.outputPath != null && !icon.outputPath.exists()) {
			icon.outputPath.mkdirs();
		}

		if (icon.disabledPath != null && !icon.disabledPath.exists()) {
			icon.disabledPath.mkdirs();
		}

		// Create the document to rasterize
		SVGDocument svgDocument = generateSVGDocument(icon);

		if (svgDocument == null) {
			return;
		}

		// Determine the output sizes (native, double, quad)
		// We render at quad size and resample down for output
		Element svgDocumentNode = svgDocument.getDocumentElement();
		String nativeWidthStr = svgDocumentNode.getAttribute("width");
		String nativeHeightStr = svgDocumentNode.getAttribute("height");
		int nativeWidth = -1;
		int nativeHeight = -1;

		try {
			if (!"".equals(nativeWidthStr) && !"".equals(nativeHeightStr)) {
				nativeWidthStr = stripOffPx(nativeWidthStr);
				nativeHeightStr = stripOffPx(nativeHeightStr);
				nativeWidth = Integer.parseInt(nativeWidthStr);
				nativeHeight = Integer.parseInt(nativeHeightStr);
			} else {
				// Vector graphics editing programs don't always output height
				// and width attributes on SVG.
				// As fall back: parse the viewBox attribute (which is almost
				// always set).
				String viewBoxStr = svgDocumentNode.getAttribute("viewBox");
				if ("".equals(viewBoxStr)) {
					log.error("Icon defines neither width/height nor a viewBox, skipping: " + icon.nameBase);
					failedIcons.add(icon);
					return;
				}
				String[] splitted = viewBoxStr.split(" ");
				if (splitted.length != 4) {
					log.error("Dimension could not be parsed. Skipping: " + icon.nameBase);
					failedIcons.add(icon);
					return;
				}
				String widthStr = splitted[2];
				widthStr = stripOffPx(widthStr);
				String heightStr = splitted[3];
				heightStr = stripOffPx(heightStr);

				nativeWidth = Integer.parseInt(widthStr);
				nativeHeight = Integer.parseInt(heightStr);
			}
		} catch (NumberFormatException e) {
			log.error("Dimension could not be parsed ( " + e.getMessage() + "), skipping: " + icon.nameBase);
			failedIcons.add(icon);
			return;
		}

		int outputWidth = (int) (nativeWidth * outputScale);
		int outputHeight = (int) (nativeHeight * outputScale);

		// Guesstimate the PNG size in memory, BAOS will enlarge if necessary.
		int outputInitSize = nativeWidth * nativeHeight * 4 + 1024;
		ByteArrayOutputStream iconOutput = new ByteArrayOutputStream(outputInitSize);

		// Render to SVG
		try {
			log.info(Thread.currentThread().getName() + " " + " Rasterizing: " + icon.nameBase + ".png at "
					+ outputWidth + "x" + outputHeight);

			TranscoderInput svgInput = new TranscoderInput(svgDocument);

			boolean success = renderIcon(icon, outputWidth, outputHeight, svgInput, iconOutput);

			if (!success) {
				log.error("Failed to render icon: " + icon.nameBase + ".png, skipping.");
				failedIcons.add(icon);
				return;
			} else {
				counter.getAndAdd(1);
			}
		} catch (Exception e) {
			log.error("Failed to render icon: " + e.getMessage(), e);
			failedIcons.add(icon);
			return;
		}

		// Generate a buffered image from Batik's png output
		byte[] imageBytes = iconOutput.toByteArray();
		ByteArrayInputStream imageInputStream = new ByteArrayInputStream(imageBytes);

		BufferedImage inputImage = null;
		try {
			inputImage = ImageIO.read(imageInputStream);

			if (inputImage == null) {
				log.error(
						"Failed to generate BufferedImage from rendered icon, ImageIO returned null: " + icon.nameBase);
				failedIcons.add(icon);
				return;
			}
		} catch (IOException e2) {
			log.error(
					"Failed to generate BufferedImage from rendered icon: " + icon.nameBase + " - " + e2.getMessage());
			failedIcons.add(icon);
			return;
		}

		writeIcon(icon, outputWidth, outputHeight, inputImage);

		try {
			if (icon.disabledPath != null) {
				GrayscaleFilter grayFilter = new GrayscaleFilter();

				HSBAdjustFilter desaturator = new HSBAdjustFilter();
				desaturator.setSFactor(0.0f);

				ContrastFilter decontrast = new ContrastFilter();
				decontrast.setBrightness(2.9f);
				decontrast.setContrast(0.2f);

				BufferedImage desaturated16 = desaturator.filter(grayFilter.filter(inputImage, null), null);

				BufferedImage decontrasted = decontrast.filter(desaturated16, null);

				String outputName = getOutputName(icon.nameBase);
				ImageIO.write(decontrasted, "PNG", new File(icon.disabledPath, outputName));
			}
		} catch (Exception e1) {
			log.error("Failed to render disabled icon: " + icon.nameBase, e1);
			failedIcons.add(icon);
		}
	}

	private String stripOffPx(String dimensionString) {
		if (dimensionString.endsWith("px")) {
			return dimensionString.substring(0, dimensionString.length() - 2);
		}
		return dimensionString;
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
		try (FileInputStream iconDocumentStream = new FileInputStream(icon.inputPath)) {

			String parser = XMLResourceDescriptor.getXMLParserClassName();
			SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);

			// What kind of URI is batik expecting here??? the docs don't say
			svgDocument = f.createSVGDocument("file://" + icon.nameBase + ".svg", iconDocumentStream);
		} catch (Exception e3) {
			log.error("Error parsing SVG icon document: " + e3.getMessage());
			failedIcons.add(icon);
			return null;
		}
		return svgDocument;
	}

	/**
	 * <p>
	 * Resizes the supplied inputImage to the specified width and height, using
	 * lanczos resampling techniques.
	 * </p>
	 *
	 * @param icon
	 *            the icon that's being resized
	 * @param width
	 *            the desired output width after rescaling operations
	 * @param height
	 *            the desired output height after rescaling operations
	 * @param sourceImage
	 *            the source image to resource
	 */
	private void writeIcon(IconEntry icon, int width, int height, BufferedImage sourceImage) {
		try {
			String outputName = getOutputName(icon.nameBase);
			ImageIO.write(sourceImage, "PNG", new File(icon.outputPath, outputName));
		} catch (Exception e1) {
			log.error("Failed to resize rendered icon to output size: " + icon.nameBase, e1);
			failedIcons.add(icon);
		}
	}

	/**
	 * 
	 * @param outputName
	 * @return
	 */
	private String getOutputName(String outputName) {
		if (outputScale != 1) {
			String scaleId = outputScale == (double) (int) outputScale ? Integer.toString((int) outputScale)
					: Double.toString(outputScale);
			outputName += "@" + scaleId + "x";
		}
		outputName += ".png";
		return outputName;
	}

	/**
	 * Use batik to rasterize the input SVG into a raster image at the specified
	 * image dimensions.
	 *
	 * @param icon
	 * @param width
	 *            the width to render the icons at
	 * @param height
	 *            the height to render the icon at
	 * @param transcoderInput
	 *            the SVG transcoder input
	 * @param stream
	 *            the stream to write the PNG data to
	 * 
	 * @return true if the icon was rendered successfully, false otherwise
	 * @throws MojoExecutionException
	 */
	public boolean renderIcon(final IconEntry icon, int width, int height, TranscoderInput transcoderInput,
			OutputStream stream) throws MojoExecutionException {
		PNGTranscoder transcoder = new CustomTranscoder();

		removeStyleDashPrefix(transcoderInput.getDocument().getDocumentElement());

		if (stylesheetName != null) {
			String cssRoot = icon.inputPath.getAbsolutePath().replace("eclipse-svg", "eclipse-css");
			cssRoot = cssRoot.replace("/icons/", "/styles/" + stylesheetName + "/");
			cssRoot = cssRoot.replace(".svg", ".scss");
			File cssPath = new File(cssRoot);

			File preprocessedCss = generateCSS(icon.nameBase, cssPath.getAbsolutePath());

			if (!preprocessedCss.exists()) {
				log.error("Could not resolve supplied stylesheet: " + preprocessedCss.getAbsolutePath()
						+ ", using defaults.");
			} else {
				removeInlineStyle(transcoderInput.getDocument().getDocumentElement());

				transcoder.addTranscodingHint(PNGTranscoder.KEY_USER_STYLESHEET_URI,
						preprocessedCss.toURI().toString());
			}
		}

		transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, Float.valueOf(width));
		transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, Float.valueOf(height));

		transcoder.setErrorHandler(new ErrorHandler() {
			@Override
			public void warning(TranscoderException arg0) throws TranscoderException {
				log.error("Icon: " + icon + " - WARN: " + arg0.getMessage());
			}

			@Override
			public void fatalError(TranscoderException arg0) throws TranscoderException {
				log.error("Icon: " + icon + " - FATAL: " + arg0.getMessage());
			}

			@Override
			public void error(TranscoderException arg0) throws TranscoderException {
				log.error("Icon: " + icon + " - ERROR: " + arg0.getMessage());
			}
		});

		// Transcode the SVG document input to a PNG via the output stream
		TranscoderOutput output = new TranscoderOutput(stream);

		try {
			transcoder.transcode(transcoderInput, output);
			return true;
		} catch (Exception e) {
			log.error("Error transcoding SVG to bitmap.", e);
			return false;
		} finally {
			try {
				stream.close();
			} catch (IOException e) {
				log.error("Error closing transcoder stream.", e);
			}
		}
	}

	/**
	 * <p>
	 * Initializes rasterizer defaults
	 * </p>
	 *
	 * @param threads
	 *            the number of threads to render with
	 * @param scale
	 *            multiplier to use with icon output dimensions
	 */
	private void init(int threads, double scale) {
		this.threads = threads;
		this.outputScale = Math.max(1, scale);
		icons = new ArrayList<>();
		counter = new AtomicInteger();
	}

	/**
	 * @see AbstractMojo#execute()
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {
		log = getLog();

		// Default to 2x the number of processor cores but allow override via
		// jvm arg
		int systemCores = Math.max(1, Runtime.getRuntime().availableProcessors());
		String threadStr = System.getProperty(RENDERTHREADS);
		if (threadStr != null) {
			try {
				threads = Integer.parseInt(threadStr);
			} catch (Exception e) {
				log.error("Could not parse thread count, using default thread count.", e);
				threads = systemCores;
			}
		}

		// if high res is enabled, the icons output size will be scaled by
		// iconScale
		// Defaults to 1, meaning native size
		double iconScale = 1;
		String iconScaleStr = System.getProperty(ECLIPSE_SVG_SCALE);
		if (iconScaleStr != null) {
			iconScale = Double.parseDouble(iconScaleStr);
			if (iconScale != 1 && iconScale != 1.5 && iconScale != 2) {
				log.warn("Unusual scale factor: " + iconScaleStr + " (@" + iconScale + "x)");
			}
		}

		// Defaults to "eclipse-svg"
		String sourceDir = "eclipse-svg";
		String sourceDirProp = System.getProperty(SOURCE_DIR);
		if (sourceDirProp != null) {
			sourceDir = sourceDirProp;
		}

		// Defaults to "eclipse-png"
		String targetDir = "eclipse-png";
		String targetDirProp = System.getProperty(TARGET_DIR);
		if (targetDirProp != null) {
			targetDir = targetDirProp;
		}

		// Defaults to "true"
		boolean createFragements = true;
		String createFragmentsProp = System.getProperty(CREATE_FRAGMENTS);
		if (createFragmentsProp != null) {
			createFragements = Boolean.parseBoolean(createFragmentsProp);
		}

		// Defaults to "false"
		String inputStylesheet = System.getProperty(USE_STYLESHEET);
		if (inputStylesheet != null) {
			stylesheetName = inputStylesheet;
		}

		// Defaults to "false"
		String regenerateStyles = System.getProperty(REGENERATE_STYLES);
		if (regenerateStyles != null) {
			regenerateCss = Boolean.parseBoolean(regenerateStyles);
		}

		// Track the time it takes to render the entire set
		long totalStartTime = System.currentTimeMillis();

		// initialize defaults (the old renderer was instantiated via
		// constructor)
		init(systemCores, iconScale);

		String workingDirectory = System.getProperty("user.dir");

		String dirSuffix = "/" + targetDir + "/";
		if ((iconScale != 1) && createFragements) {
			dirSuffix = "/" + targetDir + "-hidpi/";
		}

		if(stylesheetName != null) {
			if ((iconScale != 1) && createFragements) {
				dirSuffix = "/" + targetDir + "-" + stylesheetName + "-hidpi/";
			} else {
				dirSuffix = "/" + targetDir + "-" + stylesheetName + "/";
			}
		}
		
		File outputDir = new File(workingDirectory + dirSuffix);
		File iconDirectoryRoot = new File(sourceDir + "/");

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
			if ((iconScale != 1) && createFragements) {
				child = dirName + ".hidpi";
			}

			File outputBase = new File(outputDir, child);
			if ((iconScale != 1) && createFragements) {
				createFragmentFiles(outputBase, dirName);
			}

			IconGatherer.gatherIcons(icons, "svg", file, file, outputBase, true, FolderState.include);
		}

		log.info("Working directory: " + outputDir.getAbsolutePath());
		log.info("SVG Icon Directory: " + iconDirectoryRoot.getAbsolutePath());
		log.info("Rendering icons with " + systemCores + " threads, scaling output to " + iconScale + "x");
		long startTime = System.currentTimeMillis();

		ForkJoinPool forkJoinPool = new ForkJoinPool(threads);

		try {
			forkJoinPool.submit(() -> {
				icons.parallelStream().forEach(this::rasterize);
				return null;
			}).get();
		} catch (Exception e) {
			log.error("Error while rendering icons.", e);
		}

		// Print summary of operations
		int iconRendered = getIconsRendered();
		int failedIcons = getFailedIcons();
		int fullIconCount = iconRendered - failedIcons;

		log.info(fullIconCount + " Icons Rendered");
		log.info(failedIcons + " Icons Failed");
		log.info("Took: " + (System.currentTimeMillis() - startTime) + " ms.");

		log.info("Rasterization operations completed, Took: " + (System.currentTimeMillis() - totalStartTime) + " ms.");
	}

	private void createFragmentFiles(File outputBase, String dirName) {
		createFile(new File(outputBase, "build.properties"), "bin.includes = META-INF/,icons/,.\n");
		createFile(new File(outputBase, ".project"),
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<projectDescription>\n" + "    <name>" + dirName
						+ ".hidpi</name>\n" + "    <comment></comment>\n" + "    <projects>\n" + "    </projects>\n"
						+ "    <buildSpec>\n" + "        <buildCommand>\n"
						+ "            <name>org.eclipse.pde.ManifestBuilder</name>\n" + "            <arguments>\n"
						+ "            </arguments>\n" + "        </buildCommand>\n" + "        <buildCommand>\n"
						+ "            <name>org.eclipse.pde.SchemaBuilder</name>\n" + "            <arguments>\n"
						+ "            </arguments>\n" + "        </buildCommand>\n" + "    </buildSpec>\n"
						+ "    <natures>\n" + "        <nature>org.eclipse.pde.PluginNature</nature>\n"
						+ "    </natures>\n" + "</projectDescription>\n");
		createFile(new File(outputBase, "META-INF/MANIFEST.MF"),
				"Manifest-Version: 1.0\n" + "Bundle-ManifestVersion: 2\n" + "Bundle-Name: " + dirName + ".hidpi\n"
						+ "Bundle-SymbolicName: " + dirName + ".hidpi\n" + "Bundle-Version: 0.1.0.qualifier\n"
						+ "Fragment-Host: " + dirName + "\n");
	}

	/**
	 * 
	 * @param file
	 * @param contents
	 */
	private void createFile(File file, String contents) {
		try (FileWriter writer = new FileWriter(file)) {
			file.getParentFile().mkdirs();
			writer.write(contents);
		} catch (IOException e) {
			log.error(e);
		}
	}

	/**
	 * <p>
	 * Uses SASS to generate a CSS style sheet for the given style name.
	 * </p>
	 * 
	 * @param inputStylesheet
	 * @return
	 * @throws MojoExecutionException
	 */
	private File generateCSS(String styleName, String inputStylesheet) throws MojoExecutionException {
		String workingDirectory = System.getProperty("user.dir");

		File styleDir = new File(workingDirectory, "eclipse-css/styles/");
		File targetStyleSheet = new File(inputStylesheet);
		File targetStyleDir = targetStyleSheet.getParentFile();

		try {
			File outputCss = new File(targetStyleDir, styleName + ".css");

			if (regenerateCss && outputCss.exists() && !outputCss.delete()) {
				throw new MojoExecutionException(
						"Could not delete existing CSS during preprocessing: " + outputCss.getAbsolutePath());
			}

			if (regenerateCss || !outputCss.exists()) {
				ProcessBuilder procBuilder = new ProcessBuilder("sass", "--sourcemap=none", "-I" + styleDir,
						targetStyleSheet.getAbsolutePath(), outputCss.getAbsolutePath()).directory(targetStyleDir);

				Process process = procBuilder.start();

				log.info(
						"Running SASS precompiler: " + procBuilder.command().stream().collect(Collectors.joining(" ")));
				int waitFor = process.waitFor();

				if (waitFor > 0) {
					throw new MojoExecutionException(
							"Error generating CSS from SASS input, is SASS installed on your machine?");
				}
			}

			return outputCss;
		} catch (IOException | InterruptedException e) {
			log.error("Error generating CSS stylesheet from SASS.", e);
			throw new MojoExecutionException("Error while SASS preprocessing styles: " + e.getMessage(), e);
		}
	}

	/**
	 * <p>
	 * Removes broken inkscape prefix from documents, preventing broken
	 * rendering with Batik, see:
	 * 
	 * https://issues.apache.org/jira/browse/BATIK-1112
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=493994
	 * </p>
	 * 
	 * @param nodes
	 */
	private void removeStyleDashPrefix(Node node) {
		NodeList nodes = node.getChildNodes();
		int len = nodes.getLength();
		for (int i = 0; i < len; i++) {
			Node item = nodes.item(i);

			if (item instanceof Element) {
				Element elem = (Element) item;
				Attr attr = elem.getAttributeNodeNS(null, "style");

				if (attr != null) {
					String style = attr.getValue();
					String replaceAll = style.replaceAll("-inkscape", "inkscape");
					elem.setAttributeNS(null, "style", replaceAll);
				}
			}

			removeStyleDashPrefix(item);
		}
	}

	/**
	 * <p>
	 * Strips inline CSS styles from the supplied node and all descendents.
	 * </p>
	 * 
	 * @param node
	 */
	private void removeInlineStyle(Node node) {
		NodeList nodes = node.getChildNodes();
		int len = nodes.getLength();
		for (int i = 0; i < len; i++) {
			Node item = nodes.item(i);

			if (item instanceof Element) {
				Element elem = (Element) item;
				elem.setAttributeNS(null, "style", "");
			}

			removeInlineStyle(item);
		}
	}

}
