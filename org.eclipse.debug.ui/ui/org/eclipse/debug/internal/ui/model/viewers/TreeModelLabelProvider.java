/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.model.viewers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.internal.ui.model.IElementLabelProvider;
import org.eclipse.debug.internal.ui.model.ILabelUpdate;
import org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.progress.UIJob;

/**
 * @since 3.3
 */
class TreeModelLabelProvider extends ColumnLabelProvider {
	
	private TreeModelViewer fViewer;
	private List fComplete;
	
	/**
	 * Cache of images used for elements in this label provider. Label updates
	 * use the method <code>getImage(...)</code> to cache images for
	 * image descriptors. The images are disposed with this label provider.
	 */
	private Map fImageCache = new HashMap();

	/**
	 * Cache of the fonts used for elements in this label provider. Label updates
	 * use the method <code>getFont(...)</code> to cache fonts for
	 * FontData objects. The fonts are disposed with this label provider.
	 */
	private Map fFontCache = new HashMap();

	/**
	 * Cache of the colors used for elements in this label provider. Label updates
	 * use the method <code>getColor(...)</code> to cache colors for
	 * RGB values. The colors are disposed with this label provider.
	 */
	private Map fColorCache = new HashMap();
	
	/**
	 * Constructs a new label provider on the given display
	 */
	public TreeModelLabelProvider(TreeModelViewer viewer) {
		fViewer = viewer;
	}
	
	/**
	 * Returns an image for the given image descriptor or <code>null</code>. Adds the image
	 * to a cache of images if it does not already exist.
	 * 
	 * @param descriptor image descriptor or <code>null</code>
	 * @return image or <code>null</code>
	 */
	protected Image getImage(ImageDescriptor descriptor) {
		if (descriptor == null) {
			return null;
		}
		Image image = (Image) fImageCache.get(descriptor);
		if (image == null) {
			image = new Image(getDisplay(), descriptor.getImageData());
			fImageCache.put(descriptor, image);
		}
		return image;
	}

	/**
	 * Returns the display to use for resource allocation.
	 * 
	 * @return display
	 */
	private Display getDisplay() {
		return fViewer.getControl().getDisplay();
	}
	
	/**
	 * Returns a font for the given font data or <code>null</code>. Adds the font to the font 
	 * cache if not yet created.
	 * 
	 * @param fontData font data or <code>null</code>
	 * @return font font or <code>null</code>
	 */
	protected Font getFont(FontData fontData) {
		if (fontData == null) {
			return null;
		}
		Font font = (Font) fFontCache.get(fontData);
		if (font == null) {
			font = new Font(getDisplay(), fontData);
			fFontCache.put(fontData, font);
		}
		return font;
	}	
	
	/**
	 * Returns a color for the given RGB or <code>null</code>. Adds the color to the color 
	 * cache if not yet created.
	 * 
	 * @param rgb RGB or <code>null</code>
	 * @return color or <code>null</code>
	 */
	protected Color getColor(RGB rgb) {
		if (rgb == null) {
			return null;
		}
		Color color = (Color) fColorCache.get(rgb);
		if (color == null) {
			color = new Color(getDisplay(), rgb);
			fColorCache.put(rgb, color);
		}
		return color;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.BaseLabelProvider#dispose()
	 */
	public void dispose() {
		Iterator images = fImageCache.values().iterator();
		while (images.hasNext()) {
			Image image = (Image) images.next();
			image.dispose();
		}
		fImageCache.clear();
		
		Iterator fonts = fFontCache.values().iterator();
		while (fonts.hasNext()) {
			Font font = (Font) fonts.next();
			font.dispose();
		}
		fFontCache.clear();
		
		Iterator colors = fColorCache.values().iterator();
		while (colors.hasNext()) {
			Color color = (Color) colors.next();
			color.dispose();
		}
		fColorCache.clear();
		
		super.dispose();
	}

	public synchronized void update(ViewerCell cell) {
		String[] visibleColumns = fViewer.getVisibleColumns();
		Object element = cell.getElement();
		String columnId = null;
		if (visibleColumns != null) {
			columnId = visibleColumns[cell.getColumnIndex()];
		}
		IElementLabelProvider presentation = getLabelAdapter(element);
		if (presentation != null) {
			presentation.update(new LabelUpdate(element, (TreeItem) cell.getItem(), this, columnId, cell.getColumnIndex()));
		} else if (element instanceof String) {
			// for example, expression error messages
			cell.setText((String)element);
		}
	}	
	
	/**
	 * Returns the presentation context for this label provider.
	 * 
	 * @return presentation context
	 */
	protected IPresentationContext getPresentationContext() {
		return fViewer.getPresentationContext();
	}
	
    /**
     * Returns the label provider for the given element or
     * <code>null</code> if none.
     * 
     * @param element
     *            element to retrieve adapter for
     * @return label adapter or <code>null</code>
     */
    protected IElementLabelProvider getLabelAdapter(Object element) {        
    	IElementLabelProvider adapter = null;
        if (element instanceof IElementLabelProvider) {
            adapter = (IElementLabelProvider) element;
        } else if (element instanceof IAdaptable) {
            IAdaptable adaptable = (IAdaptable) element;
            adapter = (IElementLabelProvider) adaptable.getAdapter(IElementLabelProvider.class);
        }
        return adapter;
    }		

    /**
     * A label update is complete.
     * 
     * @param update
     */
    protected synchronized void complete(ILabelUpdate update) {
		if (fComplete == null) {
			fComplete = new ArrayList();
			UIJob job = new UIJob(getDisplay(), "Label Updates") { //$NON-NLS-1$
				public IStatus runInUIThread(IProgressMonitor monitor) {
					LabelUpdate[] updates = null;
					synchronized (TreeModelLabelProvider.this) {
						updates = (LabelUpdate[]) fComplete.toArray(new LabelUpdate[fComplete.size()]);
						fComplete = null;
					}
					//System.out.println("Changed Labels: " + updates.length);
					for (int i = 0; i < updates.length; i++) {
						updates[i].update();
					}
					return Status.OK_STATUS;
				}
			};
			job.setSystem(true);
			job.schedule(10L);
		}
		fComplete.add(update);
    }

}
