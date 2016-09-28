/*******************************************************************************
 * (c) Copyright 2015, 2016 l33t labs LLC and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     l33t labs LLC and others - initial contribution
 *******************************************************************************/

package org.eclipse.images.renderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

import com.mortennobel.imagescaling.ResampleFilters;
import com.mortennobel.imagescaling.ResampleOp;

/**
 * <p>
 * Mojo which renders galleries for comparing and evaluating icons..
 * </p>
 *
 * @goal render-galleries
 * @phase generate-resources
 */
public class GalleryMojo extends AbstractMojo {

	/** Maven logger */
	Log log;

	/** Used for finding gif files by extension. */
	public static final String GIF_EXT = ".gif";

	/** Used to specify the directory name where the SVGs are taken from. */
	public static final String PNG_DIR = "eclipse.svg.pngdirectory";

	/** Used to specify the directory name where the SVGs are taken from. */
	public static final String GIF_DIR = "eclipse.svg.gifdirectory";

	/**
	 * <p>
	 * Mojo takes rendered images and generates various galleries for testing
	 * and evaluation.
	 * </p>
	 */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		log = getLog();

		// Defaults to "eclipse-png"
		String pngDir = "eclipse-png";
		String pngDirProp = System.getProperty(PNG_DIR);
		if (pngDirProp != null) {
			pngDir = pngDirProp;
		}

		// Defaults to "eclipse-gif"
		String gifDir = "eclipse-gif";
		String gifDirProp = System.getProperty(GIF_DIR);
		if (gifDirProp != null) {
			gifDir = gifDirProp;
		}

		File iconDirectoryRoot = new File(pngDir + "/");
		if (!iconDirectoryRoot.exists()) {
			log.error("PNG directory' " + pngDir + "' does not exist.");
			return;
		}

		Map<String, List<IconEntry>> galleryIconSets = new HashMap<>();

		// Search each subdir in the root dir for svg icons
		for (File file : iconDirectoryRoot.listFiles()) {
			if (!file.isDirectory()) {
				continue;
			}

			List<IconEntry> icons = new ArrayList<>();
			IconGatherer.gatherIcons(icons, "png", file, file, iconDirectoryRoot, false, FolderState.exclude);

			galleryIconSets.put(file.getName(), icons);
		}

		File mavenTargetDir = new File("target/");
		File galleryDir = new File(mavenTargetDir, "gallery/");
		File gifCompare = new File(galleryDir, "gifcompare/");
		File master = new File(galleryDir, "master/");

		if (galleryDir.exists()) {
			galleryDir.delete();
		}

		galleryDir.mkdirs();
		gifCompare.mkdirs();
		master.mkdirs();

		renderGalleries(galleryDir, gifCompare, master, galleryIconSets, 16, 800, pngDir, gifDir);

		galleryIconSets.clear();
		// Search each subdir in the root dir for svg icons
		for (File file : iconDirectoryRoot.listFiles()) {
			if(!file.isDirectory()) {
				continue;
			}
			List<IconEntry> icons = new ArrayList<>();
			IconGatherer.gatherIcons(icons, "png", file, file, iconDirectoryRoot, false, FolderState.only);

			galleryIconSets.put(file.getName(), icons);
		}
		renderWizardBannerCompareGalleries(gifCompare, galleryIconSets, 75, 800, pngDir, gifDir);
	}

	/**
	 * <p>
	 * Renders each icon set into a gallery image for reviewing and showing off
	 * icons, and then composes them into a master gallery image.
	 * </p>
	 *
	 * @param galleryDir
	 * @param gifCompare
	 * @param master
	 * @param iconSets
	 * @param iconSize
	 * @param width
	 * @param pngDir
	 * @param gifDir
	 */
	public void renderGalleries(File galleryDir, File gifCompare, File master, Map<String, List<IconEntry>> iconSets,
			int iconSize, int width, String pngDir, String gifDir) {
		// Render each icon set and a master list
		List<IconEntry> masterList = new ArrayList<>();

		for (Entry<String, List<IconEntry>> entry : iconSets.entrySet()) {
			String key = entry.getKey();
			List<IconEntry> value = entry.getValue();

			masterList.addAll(value);

			log.info("Creating gallery for: " + key);
			renderGallery(galleryDir, key, value, iconSize, width, 3);
			renderGifCompareGallery(gifCompare, key, value, iconSize, width, 6, pngDir, gifDir, GIF_EXT);
		}

		// Render the master image
		log.info("Rendering master icon gallery...");
		renderMasterGallery(galleryDir, master, "-gallery.png", iconSize, iconSize + width, true);
		renderMasterGallery(galleryDir, master, "-gallery.png", iconSize, iconSize + width, false);

		// Master gif compare
		// renderMasterGallery(outputDir, "-gifcompare.png", iconSize, iconSize
		// + width, false);
	}

	/**
	 * <p>Renders each icon set into a gallery image for reviewing and showing off
	 * icons, and then composes them into a master gallery image.</p>
	 *
	 * @param gifCompare
	 * @param iconSets
	 * @param iconSize
	 * @param width
	 * @param pngDir
	 * @param gifDir
	 */
	public void renderWizardBannerCompareGalleries(File gifCompare, Map<String, List<IconEntry>> iconSets, int iconSize, int width, String pngDir, String gifDir) {
		// Render each icon set
		for (Entry<String, List<IconEntry>> entry : iconSets.entrySet()) {
			String key = entry.getKey();
			List<IconEntry> value = entry.getValue();

			log.info("Creating wizard banner compare gallery for: " + key);
			renderGifCompareGallery(gifCompare, key, value, iconSize, width, 6, pngDir, gifDir, ".png");
		}
	}

	/**
	 * <p>
	 * Renders comparison images, the new png/svg icons vs old gifs.
	 * </p>
	 *
	 * @param outputDir
	 * @param key
	 * @param icons
	 * @param iconSize
	 * @param width
	 * @param margin
	 * @param pngDir
	 * @param gifDir
	 */
	private void renderGifCompareGallery(File outputDir, String key, List<IconEntry> icons, int iconSize, int width,
			int margin, String pngDir, String gifDir, String fileExt) {
		int leftColumnWidth = 300;
		int textHeaderHeight = 31;
		int outputSize = iconSize;
		int widthTotal = (outputSize * 4) + (margin * 6) + leftColumnWidth;

		int rowHeight = iconSize + (margin * 2);

		// Compute the height and add some room for the text header (31 px)
		int height = (icons.size() * rowHeight) + textHeaderHeight;

		BufferedImage bi = new BufferedImage(widthTotal + iconSize, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(Color.GRAY);
		g.drawString("SVG Icon Set: " + key + " - Count: " + icons.size(), 8, 20);

		int y = textHeaderHeight;

		// Render
		ResampleOp resampleOp = new ResampleOp(outputSize, outputSize);
		resampleOp.setFilter(ResampleFilters.getLanczos3Filter());
		// resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Oversharpened);
		resampleOp.setNumberOfThreads(Runtime.getRuntime().availableProcessors());

		int second = leftColumnWidth + margin + iconSize;

		g.setColor(Color.WHITE);
		g.fillRect(0, 0, widthTotal + 10, height);

		g.setColor(Color.DARK_GRAY);
		g.fillRect(second + (margin / 2) + iconSize + 20, 0, (margin * 2) + (iconSize * 2) + 10, height);

		g.drawString(key + " (Old / New)", 15, 20);

		Collections.sort(icons);

		// Render each icon into the gallery grid
		for (IconEntry entry : icons) {

			if (entry.inputPath == null) {
				continue;
			}

			try {
				BufferedImage pngImage = ImageIO.read(entry.inputPath);

				// Munge the gif path
				File gifLocalPath = new File(entry.inputPath.getParentFile(), entry.nameBase + fileExt);
				String absoluteLocalPath = gifLocalPath.getAbsolutePath();
				String gifAbsPath = absoluteLocalPath.replaceFirst(pngDir, gifDir);
				File gifPath = new File(gifAbsPath);

				log.debug("Search for old images...");
				log.debug("Entry path: " + entry.inputPath.getAbsolutePath());
				log.debug("Old image path: " + gifPath.getAbsolutePath());

				BufferedImage gifImage = null;
				BufferedImage sizedGifImage = null;
				BufferedImage sizedPngImage = null;

				if (gifPath.exists()) {
					gifImage = ImageIO.read(gifPath);
				} else {
					log.debug("Old image not found: " + gifPath.getAbsolutePath());
				}

				g.drawString(entry.nameBase, 5, y + (margin * 3));

				g.drawLine(0, y, widthTotal, y);

				if (gifImage != null) {
					if (gifImage.getWidth() > outputSize || gifImage.getHeight() > outputSize) {
						sizedGifImage = resampleOp.filter(gifImage, null);
					} else {
						sizedGifImage = gifImage;
					}
					g.drawImage(sizedGifImage, leftColumnWidth, y + margin, null);
				}

				if (pngImage.getWidth() > outputSize || pngImage.getHeight() > outputSize) {
					sizedPngImage = resampleOp.filter(pngImage, null);
				} else {
					sizedPngImage = pngImage;
				}
				g.drawImage(sizedPngImage, second, y + margin, null);

				if (gifImage != null) {
					g.drawImage(sizedGifImage, second + margin + iconSize + 30, y + margin, null);
				}

				g.drawImage(sizedPngImage, second + (margin * 2) + (iconSize * 2) + 30, y + margin, null);

				y += iconSize + margin * 2;
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Error rendering icon for gallery: " + entry.inputPath.getAbsolutePath());
				continue;
			}
		}

		try {
			// Write the gallery image to disk
			String outputName = key + "-" + iconSize + "-gifcompare.png";
			ImageIO.write(bi, "PNG", new File(outputDir, outputName));
		} catch (IOException e) {
			e.printStackTrace();
			log.error("Error writing gif comparison gallery: " + e.getMessage());
		}
	}

	/**
	 * <p>
	 * Renders an icon set into a grid within an image.
	 * </p>
	 *
	 * @param outputRoot
	 * @param key
	 * @param icons
	 */
	private void renderGallery(File outputRoot, String key, List<IconEntry> icons, int iconSize, int width,
			int margin) {
		int textHeaderHeight = 31;
		int outputSize = iconSize;
		int outputTotal = outputSize + (margin * 2);
		int div = width / outputTotal;
		int rowCount = icons.size() / div;

		if (width % outputTotal > 0) {
			rowCount++;
		}

		// Compute the height and add some room for the text header (31 px)
		int height = Math.max(outputTotal, rowCount * outputTotal) + textHeaderHeight;

		BufferedImage bi = new BufferedImage(width + iconSize, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(Color.GRAY);
		g.drawString("SVG Icon Set: " + key + " - Count: " + icons.size(), 8, 20);

		int x = 1;
		int y = textHeaderHeight;

		// Render
		ResampleOp resampleOp = new ResampleOp(outputSize, outputSize);
		resampleOp.setFilter(ResampleFilters.getLanczos3Filter());
		// resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Oversharpened);
		resampleOp.setNumberOfThreads(Runtime.getRuntime().availableProcessors());

		// Render each icon into the gallery grid
		for (IconEntry def : icons) {
			try {
				if (def.inputPath == null) {
					log.error("Undefined gallery image for : " + def.nameBase);
					continue;
				}

				BufferedImage iconImage = ImageIO.read(def.inputPath);
				BufferedImage sizedImage = resampleOp.filter(iconImage, null);

				g.drawImage(sizedImage, x + margin, y + margin, null);

				x += outputTotal;

				if (x >= width) {
					x = 1;
					y += outputTotal;
				}
			} catch (Exception e) {
				log.error("Error rendering icon for gallery: " + def.inputPath.getAbsolutePath());
				e.printStackTrace();
				continue;
			}
		}

		try {
			// Write the gallery image to disk
			String outputName = key + "-" + iconSize + "-gallery.png";
			ImageIO.write(bi, "PNG", new File(outputRoot, outputName));
		} catch (IOException e) {
			log.error("Error writing icon: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * <p>
	 * Renders a master gallery image that contains every icon set at the
	 * current resolution.
	 * </p>
	 * 
	 * @param root
	 * @param output
	 * @param fileEnding
	 * @param iconSize
	 * @param width
	 * @param dark
	 */
	private void renderMasterGallery(File root, File output, String fileEnding, int iconSize, int width, boolean dark) {
		int headerHeight = 30;
		List<BufferedImage> images = new ArrayList<>();
		for (File file : root.listFiles()) {
			if (file.getName().endsWith(iconSize + fileEnding)) {
				BufferedImage set = null;
				try {
					set = ImageIO.read(file);
				} catch (IOException e) {
					log.error("Error reading icon: " + e.getMessage());
					e.printStackTrace();
					continue;
				}
				images.add(set);
				headerHeight += set.getHeight();
			}
		}

		BufferedImage bi = new BufferedImage(width, headerHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (dark) {
			g.setColor(Color.DARK_GRAY);
		} else {
			g.setColor(Color.WHITE);
		}
		g.fillRect(0, 0, bi.getWidth(), bi.getHeight());

		g.setColor(Color.BLACK);
		g.drawString(
				"SVG Icons for Eclipse - Count: " + iconSize + "x" + iconSize + " Rendered: " + new Date().toString(),
				8, 20);

		int x = 0;
		int y = 31;

		// Draw each icon set image into the uber gallery
		for (BufferedImage image : images) {
			g.drawImage(image, x, y, null);
			y += image.getHeight();
		}

		try {
			// Write the uber gallery to disk
			String bgState = (dark) ? "dark" : "light";
			String outputName = "global-svg-" + iconSize + "-" + bgState + fileEnding + "-icons.png";
			ImageIO.write(bi, "PNG", new File(output, outputName));
		} catch (IOException e) {
			log.error("Error writing gallery: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
