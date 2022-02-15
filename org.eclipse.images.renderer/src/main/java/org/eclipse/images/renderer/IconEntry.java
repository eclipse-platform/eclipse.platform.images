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

/**
 * <p>
 * IconEntry is used to define an icon to rasterize, where to put it and the
 * dimensions to render it at.
 * </p>
 */
class IconEntry implements Comparable<IconEntry> {

	/** The name of the icon minus extension */
	String nameBase;

	/** The input path of the source svg files. */
	File inputPath;

	/** The sizes this icon should be rendered at */
	int[] sizes;

	/**
	 * The path rasterized versions of this icon should be written into.
	 */
	File outputPath;

	/** The path to a disabled version of the icon (gets desaturated). */
	File disabledPath;

	/** The root directory for the icon (org.eclipse.ui, etc) */
	File iconRoot;

	/**
	 * 
	 * @param nameBase
	 * @param inputPath
	 * @param outputPath
	 * @param disabledPath
	 * @param sizes
	 */
	public IconEntry(String nameBase, File iconRoot, File inputPath, File outputPath, File disabledPath, int[] sizes) {
		this.nameBase = nameBase;
		this.iconRoot = iconRoot;
		this.sizes = sizes;
		this.inputPath = inputPath;
		this.outputPath = outputPath;
		this.disabledPath = disabledPath;
	}

	@Override
	public int compareTo(IconEntry o) {
		return nameBase.compareTo(o.nameBase);
	}
}