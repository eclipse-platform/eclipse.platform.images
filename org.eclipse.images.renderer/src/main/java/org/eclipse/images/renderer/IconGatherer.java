/*******************************************************************************
 * (c) Copyright 2015 l33t labs LLC and others.
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
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * Utility to find and organize icons for rendering.
 * </p>
 * 
 * @author tmccrary@l33tlabs.com
 *
 */
public class IconGatherer {

	private IconGatherer() { }
	
	/**
	 * <p>
	 * Searches the root resources directory for svg icons and adds them to a
	 * collection for later rasterization.
	 * </p>
	 *
	 * @param icons
	 * @param extension
	 * @param rootDir
	 * @param iconDir
	 * @param outputBase
	 * @param generateDisabledDirs
	 */
	public static void gatherIcons(List<IconEntry> icons, String extension, File rootDir, File iconDir, File outputBase,
			boolean generateDisabledDirs, FolderState wizardBannerState) {

		List<File> listFiles = Arrays.asList(iconDir.listFiles());

		String filter = System.getProperty("eclipse.svg.filter");
		String targetIcon = System.getProperty("eclipse.svg.targetIcon");
		
		
		for (File child : listFiles) {
			if(filter != null && !child.getAbsolutePath().contains(filter)) {
				continue;
			}
			
			if (child.isDirectory()) {
				if (child.getName().startsWith("d") && !("dgm".equals(child.getName()))) {
					continue;
				}
				
				if (wizardBannerState == FolderState.exclude && "wizban".equals(child.getName())) {
					continue;
				}

				gatherIcons(icons, extension, rootDir, child, outputBase, generateDisabledDirs, wizardBannerState);
				continue;
			}

			if (targetIcon != null && !child.getName().contains(targetIcon)) {
				continue;
			}

			if (!child.getName().endsWith(extension)) {
				continue;
			}

			if (!child.getName().endsWith(extension) || child.getName().contains("@1.5x")
					|| child.getName().contains("@2x")) {
				continue;
			}

			// Compute a relative path for the output dir
			URI rootUri = rootDir.toURI();
			URI iconUri = iconDir.toURI();

			String relativePath = rootUri.relativize(iconUri).getPath();
			File outputDir = new File(outputBase, relativePath);
			File disabledOutputDir = null;

			File parentFile = child.getParentFile();
			if (wizardBannerState == FolderState.only && !"wizban".equals(parentFile.getName())) {
				continue;
			}

			/*
			 * Determine if/where to put a disabled version of the icon Eclipse
			 * traditionally uses a prefix of d for disabled, e for enabled in
			 * the folder name
			 */
			if (generateDisabledDirs && parentFile != null) {
				String parentDirName = parentFile.getName();
				if (parentDirName.startsWith("e")) {
					StringBuilder builder = new StringBuilder();
					builder.append("d");
					builder.append(parentDirName.substring(1, parentDirName.length()));

					// Disabled variant folder name
					String disabledVariant = builder.toString();

					// The parent's parent, to create the disabled directory in
					File setParent = parentFile.getParentFile();

					// The source directory's disabled folder
					File disabledSource = new File(setParent, disabledVariant);

					// Compute a relative path, so we can create the output
					// folder
					String path = rootUri.relativize(disabledSource.toURI()).getPath();

					// Create the output folder, so a disabled icon is generated
					disabledOutputDir = new File(outputBase, path);
				}
			}

			IconEntry icon = createIcon(rootDir, child, outputDir, disabledOutputDir);

			icons.add(icon);
		}
	}

	/**
	 * <p>
	 * Creates an IconEntry, which contains information about rendering an icon
	 * such as the source file, where to render, what alternative types of
	 * output to generate, etc.
	 * </p>
	 *
	 * @param the
	 *            root directory of the icon (org.eclipse.ui, etc)
	 * @param input
	 *            the source of the icon file (SVG document)
	 * @param outputPath
	 *            the path of the rasterized version to generate
	 * @param disabledPath
	 *            the path of the disabled (desaturated) icon, if one is
	 *            required
	 *
	 * @return an IconEntry describing the rendering operation
	 */
	public static IconEntry createIcon(File iconRoot, File input, File outputPath, File disabledPath) {
		String name = input.getName();
		String[] split = name.split("\\.(?=[^\\.]+$)");

		return new IconEntry(split[0], iconRoot, input, outputPath, disabledPath, new int[0]);
	}

}
